package com.bytemyth.gms_installer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bytemyth.gms_installer.catalog.GmsCatalog
import com.bytemyth.gms_installer.catalog.ResolvedRomLinks
import com.bytemyth.gms_installer.catalog.RomDownloadLink
import com.bytemyth.gms_installer.catalog.RomLinkRepository
import com.bytemyth.gms_installer.catalog.RomOption
import com.bytemyth.gms_installer.detect.GmsDetector
import com.bytemyth.gms_installer.detect.GmsSnapshot
import com.bytemyth.gms_installer.detect.HealthState
import com.bytemyth.gms_installer.detect.InstallState
import com.bytemyth.gms_installer.detect.MakerHint
import com.bytemyth.gms_installer.detect.OemGoogleServicesGate
import com.bytemyth.gms_installer.detect.OemGoogleServicesGateDetector
import com.bytemyth.gms_installer.detect.PlanAction
import com.bytemyth.gms_installer.detect.explainInstallFailure
import com.bytemyth.gms_installer.detect.makerHintFor
import com.bytemyth.gms_installer.install.ApkAnalyzer
import com.bytemyth.gms_installer.install.DirectApkFetcher
import com.bytemyth.gms_installer.install.InstallOutcome
import com.bytemyth.gms_installer.install.MatchedPackage
import com.bytemyth.gms_installer.install.PackageInstallController
import com.bytemyth.gms_installer.install.PackageUninstallController
import com.bytemyth.gms_installer.install.UninstallOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class MainTab { Overview, Install, Guide }

enum class ItemPhase {
    Queued,
    Uninstalling,
    Downloading,
    Writing,
    Waiting,
    Success,
    Failed,
    Skipped,
}

data class ItemProgress(
    val phase: ItemPhase = ItemPhase.Queued,
    /** 0..1 确定进度；小于 0 表示不确定进度 */
    val fraction: Float = 0f,
    val detail: String = "",
)

data class InstallJobState(
    val current: MatchedPackage?,
    val index: Int,
    val total: Int,
    val log: List<String>,
    val running: Boolean,
    val done: Boolean,
    val downloadLabel: String? = null,
    val bytesRead: Long = 0L,
    val bytesTotal: Long = -1L,
    /** key = packageName */
    val progressByKey: Map<String, ItemProgress> = emptyMap(),
)

enum class DialogNav {
    None,
    AppDetails,
    OemGoogleSettings,
}

data class UiDialogAction(
    val label: String,
    val nav: DialogNav = DialogNav.None,
    val packageName: String? = null,
)

data class UiDialog(
    val title: String,
    val message: String,
    val actions: List<UiDialogAction> = emptyList(),
    val kind: String? = null,
)

data class UiState(
    val tab: MainTab = MainTab.Overview,
    val snapshot: GmsSnapshot? = null,
    val scanning: Boolean = true,
    val romOptions: List<RomOption> = emptyList(),
    val romOverrideApi: Int? = null,
    val romLinks: ResolvedRomLinks? = null,
    val matched: List<MatchedPackage> = emptyList(),
    val selected: Set<String> = emptySet(),
    val analyzing: Boolean = false,
    val analyzeError: String? = null,
    val job: InstallJobState? = null,
    val snackbar: String? = null,
    val dialog: UiDialog? = null,
)

class GmsViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = GmsDetector(application)
    private val oemGateDetector = OemGoogleServicesGateDetector(application)
    private val analyzer = ApkAnalyzer(application)
    private val installer = PackageInstallController(application)
    private val uninstaller = PackageUninstallController(application)
    private val romLinkRepo = RomLinkRepository(application)
    private val fetcher = DirectApkFetcher()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val makerHint: MakerHint
        get() {
            val device = _state.value.snapshot?.device
            return makerHintFor(device?.manufacturer.orEmpty(), device?.brand.orEmpty())
        }

    init {
        refresh()
    }

    fun selectTab(tab: MainTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            // 已有检测结果时静默刷新，避免 scanning 切换导致整页布局抖动
            val firstLoad = _state.value.snapshot == null
            if (firstLoad) {
                _state.update { it.copy(scanning = true) }
            }
            val snap = withContext(Dispatchers.Default) { detector.snapshot() }
            val resolved = withContext(Dispatchers.Default) {
                runCatching {
                    romLinkRepo.resolve(
                        sdk = snap.device.sdk,
                        abis = snap.device.abis,
                        overrideApi = _state.value.romOverrideApi,
                    )
                }.getOrNull()
            }
            _state.update {
                it.copy(
                    snapshot = snap,
                    scanning = false,
                    romOptions = romLinkRepo.options,
                    romLinks = resolved ?: it.romLinks,
                )
            }
            maybePromptOemGoogleGate(snap)
        }
    }

    private fun oemGoogleGateDialog(gate: OemGoogleServicesGate): UiDialog {
        return UiDialog(
            title = "请开启系统 Google 服务",
            message = "这台手机已预置 Google 服务，但系统开关似乎未打开。\n\n" +
                "请先到系统设置打开后再继续安装或登录。\n路径参考：${gate.hintPath}",
            actions = listOf(
                UiDialogAction(
                    label = gate.buttonLabel,
                    nav = DialogNav.OemGoogleSettings,
                ),
            ),
            kind = "oem_google_gate",
        )
    }

    private fun maybePromptOemGoogleGate(snap: GmsSnapshot) {
        val gate = snap.oemGoogleGate
        if (!gate.likelyOff) {
            _state.update { current ->
                if (current.dialog?.kind == "oem_google_gate") {
                    current.copy(dialog = null)
                } else {
                    current
                }
            }
            return
        }
        if (_state.value.job?.running == true) return
        _state.update { current ->
            // 不打断其它业务弹窗；仅在空闲或同类型时提示
            if (current.dialog != null && current.dialog.kind != "oem_google_gate") current
            else current.copy(dialog = oemGoogleGateDialog(gate))
        }
    }

    fun oemGoogleSettingsIntent(): Intent? {
        return oemGateDetector.settingsIntent(makerHint)
    }

    fun selectRomApi(api: Int?) {
        _state.update { it.copy(romOverrideApi = api) }
        val snap = _state.value.snapshot ?: return
        val resolved = runCatching {
            romLinkRepo.resolve(snap.device.sdk, snap.device.abis, api)
        }.getOrNull()
        _state.update { it.copy(romLinks = resolved) }
    }

    fun openUrlIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, analyzeError = null, tab = MainTab.Install) }
            try {
                val matched = withContext(Dispatchers.IO) { analyzer.analyze(uris) }
                if (matched.isEmpty()) {
                    _state.update {
                        it.copy(
                            analyzing = false,
                            analyzeError = "没有识别到 APK。请选择 .apk 文件，或包含 APK 的 zip。",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            analyzing = false,
                            matched = matched,
                            selected = matched.map { item -> item.packageName }.toSet(),
                        )
                    }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(analyzing = false, analyzeError = t.message ?: "解析失败")
                }
            }
        }
    }

    fun toggleSelected(packageName: String) {
        _state.update { current ->
            val next = current.selected.toMutableSet()
            if (!next.add(packageName)) next.remove(packageName)
            current.copy(selected = next)
        }
    }

    fun installDirect(link: RomDownloadLink, startConfirm: (android.content.Intent) -> Unit) {
        if (!link.hasDirect) {
            _state.update { it.copy(snackbar = "没有直链，请用 APKPure") }
            return
        }
        val status = _state.value.snapshot?.components?.firstOrNull {
            it.component.packageName == link.packageName
        }
        val healthyInstalled = status?.state == InstallState.Installed &&
            status.health == HealthState.Healthy &&
            !status.needsRepair
        if (healthyInstalled) {
            _state.update {
                it.copy(
                    snackbar = if (status!!.isSystem) {
                        "${link.title} 已是系统预装且正常，无需再装"
                    } else {
                        "${link.title} 本机已安装且正常，无需再装"
                    },
                )
            }
            return
        }
        installDirectLinks(listOf(link), startConfirm)
    }

    fun installDirectAll(startConfirm: (android.content.Intent) -> Unit) {
        val links = planInstallLinks()
        if (links.isEmpty()) {
            _state.update { it.copy(snackbar = "按本机检测，当前没有需要下载安装或修复的组件") }
            return
        }
        installDirectLinks(links, startConfirm)
    }

    /**
     * 一键修复：静默刷新检测（不触发布局切换），再按方案处理。
     */
    fun oneClickRepair(startConfirm: (android.content.Intent) -> Unit) {
        if (_state.value.job?.running == true) return
        viewModelScope.launch {
            val snap = withContext(Dispatchers.Default) { detector.snapshot() }
            val resolved = withContext(Dispatchers.Default) {
                runCatching {
                    romLinkRepo.resolve(
                        sdk = snap.device.sdk,
                        abis = snap.device.abis,
                        overrideApi = _state.value.romOverrideApi,
                    )
                }.getOrNull()
            }
            _state.update {
                it.copy(
                    snapshot = snap,
                    romOptions = romLinkRepo.options,
                    romLinks = resolved ?: it.romLinks,
                )
            }
            if (snap.oemGoogleGate.likelyOff) {
                _state.update { it.copy(dialog = oemGoogleGateDialog(snap.oemGoogleGate)) }
                return@launch
            }
            val links = planInstallLinks()
            if (links.isEmpty()) {
                val enableItems = snap.plan.needed.filter { it.action == PlanAction.Enable }
                val downloadItems = snap.plan.needed.filter {
                    it.action == PlanAction.Install || it.action == PlanAction.Repair
                }
                val dialog = when {
                    snap.plan.isEmpty -> UiDialog(
                        title = "无需修复",
                        message = "检测完成：核心组件正常，无需修复。",
                    )
                    // 有缺失/可修项但无直链时，优先提示补装路径，不被「启用」弹窗抢走
                    downloadItems.isNotEmpty() -> UiDialog(
                        title = "暂无可用直链",
                        message = "检测完成：仍缺 ${downloadItems.joinToString("、") { it.status.component.title }}，" +
                            "但暂无可用直链。请在方案里用 APKPure 或导入安装。" +
                            if (snap.plan.ready.any { it.isSystem }) {
                                " 系统可用组件已自动跳过。"
                            } else {
                                ""
                            },
                    )
                    enableItems.isNotEmpty() -> {
                        val names = enableItems.joinToString("、") { it.status.component.title }
                        UiDialog(
                            title = "无法自动修复",
                            message = if (enableItems.size == 1) {
                                "${names}无法自动卸载或重装，请打开应用信息启用、清除数据或按 ROM 指引处理。"
                            } else {
                                "以下异常组件无法自动卸载或重装，请分别打开应用信息处理：\n$names"
                            },
                            actions = enableItems.map {
                                UiDialogAction(
                                    label = "打开 ${it.status.component.title}",
                                    nav = DialogNav.AppDetails,
                                    packageName = it.status.component.packageName,
                                )
                            },
                        )
                    }
                    else -> UiDialog(
                        title = "暂无可用直链",
                        message = "检测完成：暂无可用直链，请在方案里用 APKPure 或导入安装。",
                    )
                }
                _state.update { it.copy(dialog = dialog) }
                return@launch
            }
            installDirectLinks(links, startConfirm, alertAsDialog = true)
        }
    }

    fun canOneClickRepair(): Boolean = planInstallLinks().isNotEmpty()

    /** 方案里需要「安装」或「卸载重装」且有直链的项。 */
    fun planInstallLinks(): List<RomDownloadLink> {
        val pkgs = _state.value.snapshot?.plan?.needed
            ?.filter { it.action == PlanAction.Install || it.action == PlanAction.Repair }
            ?.map { it.status.component.packageName }
            ?.toSet()
            .orEmpty()
        if (pkgs.isEmpty()) return emptyList()
        return _state.value.romLinks?.links
            .orEmpty()
            .filter { it.packageName in pkgs && it.hasDirect }
    }

    fun repairPackageNames(): Set<String> {
        return _state.value.snapshot?.plan?.needed
            ?.filter { it.action == PlanAction.Repair }
            ?.map { it.status.component.packageName }
            ?.toSet()
            .orEmpty()
    }

    fun appDetailsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    }

    fun startInstall(startConfirm: (android.content.Intent) -> Unit) {
        val current = _state.value
        if (current.job?.running == true) return
        val queue = current.matched.filter { it.packageName in current.selected }
        if (queue.isEmpty()) {
            _state.update { it.copy(snackbar = "请先勾选要安装的组件") }
            return
        }
        viewModelScope.launch {
            val seeded = queue.associate {
                it.packageName to ItemProgress(ItemPhase.Queued, 0f, "排队中")
            }
            _state.update {
                it.copy(
                    job = (it.job ?: InstallJobState(
                        current = null,
                        index = 0,
                        total = queue.size,
                        log = emptyList(),
                        running = true,
                        done = false,
                    )).copy(progressByKey = seeded, running = true, done = false),
                )
            }
            runInstallQueue(queue, mutableListOf(), startConfirm)
        }
    }

    private fun installDirectLinks(
        links: List<RomDownloadLink>,
        startConfirm: (android.content.Intent) -> Unit,
        alertAsDialog: Boolean = false,
    ) {
        if (_state.value.job?.running == true) return
        viewModelScope.launch {
            val destDir = File(getApplication<Application>().cacheDir, "direct").apply {
                deleteRecursively()
                mkdirs()
            }
            val log = mutableListOf<String>()
            val initialProgress = links.associate { it.packageName to ItemProgress(ItemPhase.Queued) }
            _state.update {
                it.copy(
                    tab = MainTab.Install,
                    analyzeError = null,
                    job = InstallJobState(
                        current = null,
                        index = 0,
                        total = links.size,
                        log = emptyList(),
                        running = true,
                        done = false,
                        downloadLabel = links.first().title,
                        progressByKey = initialProgress,
                    ),
                )
            }
            val repairPkgs = repairPackageNames()
            val collected = mutableListOf<MatchedPackage>()
            for ((index, link) in links.withIndex()) {
                val existing = _state.value.snapshot?.components
                    ?.firstOrNull { it.component.packageName == link.packageName }
                val needsRepair = link.packageName in repairPkgs || existing?.needsRepair == true
                if (existing?.state == InstallState.Installed &&
                    existing.health == HealthState.Healthy &&
                    !needsRepair
                ) {
                    log += "跳过 ${link.title}：本机已安装且正常" +
                        if (existing.isSystem) "（系统预装）" else ""
                    setItemProgress(
                        link.packageName,
                        ItemProgress(ItemPhase.Skipped, 1f, "已安装，跳过"),
                    )
                    continue
                }
                val urls = listOf(link.directUrl, link.directXapkUrl).filter { it.isNotBlank() }
                if (urls.isEmpty()) {
                    log += "${link.title} 没有直链，已跳过"
                    setItemProgress(link.packageName, ItemProgress(ItemPhase.Skipped, 0f, "无直链"))
                    continue
                }
                log += "正在下载 ${link.title}…"
                val lastEmit = AtomicLong(0L)
                setItemProgress(link.packageName, ItemProgress(ItemPhase.Downloading, 0f, "开始下载"))
                _state.update {
                    it.copy(
                        job = it.job?.copy(
                            index = index,
                            total = links.size,
                            log = log.toList(),
                            downloadLabel = link.title,
                            bytesRead = 0,
                            bytesTotal = -1,
                        ),
                    )
                }
                try {
                    val file = withContext(Dispatchers.IO) {
                        fetcher.fetch(urls, destDir, "${index}_${link.componentId}") { read, total ->
                            val now = SystemClock.elapsedRealtime()
                            val prev = lastEmit.get()
                            if (now - prev < 200 && (total <= 0 || read < total)) return@fetch
                            lastEmit.set(now)
                            val fraction = if (total > 0) {
                                (read.toFloat() / total).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            val detail = if (total > 0) {
                                "${formatBytes(read)} / ${formatBytes(total)}"
                            } else {
                                formatBytes(read)
                            }
                            _state.update { s ->
                                val job = s.job ?: return@update s
                                s.copy(
                                    job = job.copy(
                                        bytesRead = read,
                                        bytesTotal = total,
                                        progressByKey = job.progressByKey + (
                                            link.packageName to ItemProgress(
                                                ItemPhase.Downloading,
                                                fraction,
                                                detail,
                                            )
                                            ),
                                    ),
                                )
                            }
                        }
                    }
                    val matched = withContext(Dispatchers.IO) { analyzer.analyzeFiles(listOf(file)) }
                    val chosen = matched.firstOrNull { it.packageName == link.packageName }
                        ?: matched.singleOrNull()
                        ?: matched.firstOrNull()
                        ?: throw IllegalStateException("下载成功但未能识别为安装包")
                    collected += chosen
                    log += "已准备 ${chosen.label} · ${formatBytes(file.length())}"
                    setItemProgress(
                        link.packageName,
                        ItemProgress(ItemPhase.Queued, 1f, "已下载，等待安装"),
                    )
                    _state.update { it.copy(job = it.job?.copy(log = log.toList())) }
                } catch (t: Throwable) {
                    log += "${link.title} 下载失败：${t.message ?: t.javaClass.simpleName}"
                    setItemProgress(
                        link.packageName,
                        ItemProgress(ItemPhase.Failed, 1f, t.message ?: "下载失败"),
                    )
                    val required = GmsCatalog.find(link.packageName)?.required == true
                    if (required || links.size == 1) {
                        log += "请改用 APKPure 页在浏览器里下载，再回到「安装」导入。"
                        _state.update {
                            val message = "${link.title} 直链不可用，请用 APKPure"
                            it.copy(
                                job = it.job?.copy(
                                    running = false,
                                    done = true,
                                    log = log.toList(),
                                    downloadLabel = null,
                                ),
                                snackbar = if (alertAsDialog) null else message,
                                dialog = if (alertAsDialog) {
                                    UiDialog(title = "下载失败", message = message)
                                } else {
                                    null
                                },
                            )
                        }
                        return@launch
                    }
                    _state.update { it.copy(job = it.job?.copy(log = log.toList())) }
                }
            }
            if (collected.isEmpty()) {
                _state.update {
                    val message = "没有可安装的包"
                    it.copy(
                        job = it.job?.copy(
                            running = false,
                            done = true,
                            log = log.toList(),
                            downloadLabel = null,
                        ),
                        snackbar = if (alertAsDialog) null else message,
                        dialog = if (alertAsDialog) {
                            UiDialog(title = "无法继续", message = message)
                        } else {
                            null
                        },
                    )
                }
                return@launch
            }
            val queue = collected.sortedWith(
                compareBy<MatchedPackage> { it.component?.order ?: Int.MAX_VALUE }
                    .thenBy { it.label },
            )
            _state.update {
                it.copy(
                    matched = queue,
                    selected = queue.map { item -> item.packageName }.toSet(),
                )
            }
            runInstallQueue(queue, log, startConfirm, repairPkgs)
        }
    }

    private suspend fun runInstallQueue(
        queue: List<MatchedPackage>,
        log: MutableList<String>,
        startConfirm: (android.content.Intent) -> Unit,
        repairPkgs: Set<String> = repairPackageNames(),
    ) {
        val seeded = (_state.value.job?.progressByKey.orEmpty()).toMutableMap()
        queue.forEach { item ->
            if (seeded[item.packageName]?.phase !in setOf(ItemPhase.Success, ItemPhase.Failed)) {
                seeded[item.packageName] = ItemProgress(ItemPhase.Queued, 0f, "排队中")
            }
        }
        _state.update {
            it.copy(
                job = InstallJobState(
                    current = queue.first(),
                    index = 0,
                    total = queue.size,
                    log = log.toList(),
                    running = true,
                    done = false,
                    downloadLabel = null,
                    progressByKey = seeded,
                ),
            )
        }
        queue.forEachIndexed { index, item ->
            val existing = _state.value.snapshot?.components
                ?.firstOrNull { it.component.packageName == item.packageName }
            val needsRepair = item.packageName in repairPkgs || existing?.needsRepair == true
            if (existing?.state == InstallState.Installed &&
                existing.health == HealthState.Healthy &&
                !needsRepair
            ) {
                log += "跳过 ${item.label}：本机已安装且正常" +
                    if (existing.isSystem) "（系统预装）" else ""
                setItemProgress(item.packageName, ItemProgress(ItemPhase.Skipped, 1f, "已安装，跳过"))
                _state.update {
                    it.copy(
                        job = it.job?.copy(
                            current = item,
                            index = index,
                            total = queue.size,
                            log = log.toList(),
                        ),
                    )
                }
                return@forEachIndexed
            }

            if (needsRepair && existing?.isPresent == true) {
                log += "正在卸载异常组件 ${item.label}…"
                setItemProgress(
                    item.packageName,
                    ItemProgress(ItemPhase.Uninstalling, 0.2f, "卸载中，请确认"),
                )
                _state.update {
                    it.copy(
                        job = it.job?.copy(
                            current = item,
                            index = index,
                            total = queue.size,
                            log = log.toList(),
                        ),
                    )
                }
                val uninstallOutcome = withContext(Dispatchers.IO) {
                    uninstaller.uninstall(item.packageName) { intent ->
                        withContext(Dispatchers.Main) { startConfirm(intent) }
                    }
                }
                when (uninstallOutcome) {
                    UninstallOutcome.Success, UninstallOutcome.NotInstalled -> {
                        log += if (uninstallOutcome is UninstallOutcome.NotInstalled) {
                            "${item.label} 已不在本机，继续安装"
                        } else {
                            "已卸载 ${item.label}，准备重装"
                        }
                        setItemProgress(
                            item.packageName,
                            ItemProgress(ItemPhase.Queued, 0.35f, "已卸载，准备安装"),
                        )
                    }
                    UninstallOutcome.Aborted -> {
                        log += "已取消卸载 ${item.label}，跳过重装"
                        setItemProgress(item.packageName, ItemProgress(ItemPhase.Failed, 1f, "卸载已取消"))
                        _state.update { it.copy(job = it.job?.copy(log = log.toList())) }
                        if (item.component?.required == true) {
                            log += "核心组件未修复，已停止后续安装。"
                            _state.update {
                                it.copy(
                                    job = it.job?.copy(running = false, done = true, log = log.toList()),
                                )
                            }
                            refresh()
                            return
                        }
                        return@forEachIndexed
                    }
                    is UninstallOutcome.Failed -> {
                        log += "${item.label} 卸载失败：${uninstallOutcome.reason}"
                        if (existing.isSystem && !existing.canUninstall) {
                            log += "该组件为系统预装，无法普通卸载。请到应用信息清除数据后重试。"
                        }
                        setItemProgress(
                            item.packageName,
                            ItemProgress(ItemPhase.Failed, 1f, uninstallOutcome.reason),
                        )
                        _state.update { it.copy(job = it.job?.copy(log = log.toList())) }
                        if (item.component?.required == true) {
                            log += "核心组件未修复，已停止后续安装。"
                            _state.update {
                                it.copy(
                                    job = it.job?.copy(running = false, done = true, log = log.toList()),
                                )
                            }
                            refresh()
                            return
                        }
                        return@forEachIndexed
                    }
                }
            }

            val lineStart = "正在安装 ${item.label}（${item.packageName}）"
            log += lineStart
            setItemProgress(item.packageName, ItemProgress(ItemPhase.Writing, 0f, "开始安装"))
            _state.update {
                it.copy(
                    job = it.job?.copy(
                        current = item,
                        index = index,
                        total = queue.size,
                        log = log.toList(),
                    ),
                )
            }
            val lastEmit = AtomicLong(0L)
            val outcome = withContext(Dispatchers.IO) {
                installer.install(
                    matched = item,
                    startConfirm = { intent ->
                        withContext(Dispatchers.Main) { startConfirm(intent) }
                    },
                    onProgress = { phase, fraction, detail ->
                        val now = SystemClock.elapsedRealtime()
                        val prev = lastEmit.get()
                        if (now - prev < 160 && fraction < 0.92f) return@install
                        lastEmit.set(now)
                        val itemPhase = when (phase) {
                            PackageInstallController.InstallWritePhase.Writing -> ItemPhase.Writing
                            PackageInstallController.InstallWritePhase.WaitingConfirm -> ItemPhase.Waiting
                        }
                        setItemProgress(item.packageName, ItemProgress(itemPhase, fraction, detail))
                    },
                )
            }
            val softSkip = outcome is InstallOutcome.Failed && (
                outcome.reason.contains("DUPLICATE_PERMISSION", ignoreCase = true) ||
                    (
                        outcome.reason.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) &&
                            item.packageName == "com.google.android.gsf.login"
                        )
                )
            val line = when {
                softSkip -> {
                    val tip = explainInstallFailure(
                        (outcome as InstallOutcome.Failed).reason,
                        item.packageName,
                    )
                    "${item.label} 已跳过：$tip"
                }
                else -> when (outcome) {
                    InstallOutcome.Success -> "已安装 ${item.label}"
                    InstallOutcome.Aborted -> "已取消 ${item.label}"
                    is InstallOutcome.Failed ->
                        "${item.label} 失败：${explainInstallFailure(outcome.reason, item.packageName)}"
                }
            }
            log += line
            when {
                outcome is InstallOutcome.Success ->
                    setItemProgress(item.packageName, ItemProgress(ItemPhase.Success, 1f, "已完成"))
                softSkip ->
                    setItemProgress(
                        item.packageName,
                        ItemProgress(
                            ItemPhase.Skipped,
                            1f,
                            explainInstallFailure(
                                (outcome as InstallOutcome.Failed).reason,
                                item.packageName,
                            ),
                        ),
                    )
                outcome is InstallOutcome.Aborted ->
                    setItemProgress(item.packageName, ItemProgress(ItemPhase.Failed, 1f, "已取消"))
                outcome is InstallOutcome.Failed ->
                    setItemProgress(
                        item.packageName,
                        ItemProgress(
                            ItemPhase.Failed,
                            1f,
                            explainInstallFailure(outcome.reason, item.packageName),
                        ),
                    )
            }
            _state.update { it.copy(job = it.job?.copy(log = log.toList())) }
            if (!softSkip && (outcome is InstallOutcome.Failed || outcome is InstallOutcome.Aborted)) {
                if (item.component?.required == true) {
                    log += "核心组件未完成，已停止后续安装。"
                    _state.update {
                        it.copy(job = it.job?.copy(running = false, done = true, log = log.toList()))
                    }
                    refresh()
                    return
                }
            }
        }
        log += "本轮安装结束。建议重启一次手机，再登录 Google 账号。"
        _state.update {
            it.copy(job = it.job?.copy(running = false, done = true, current = null, log = log.toList()))
        }
        refresh()
    }

    private fun setItemProgress(key: String, progress: ItemProgress) {
        _state.update { s ->
            val job = s.job ?: return@update s
            s.copy(job = job.copy(progressByKey = job.progressByKey + (key to progress)))
        }
    }

    fun clearImport() {
        _state.update {
            it.copy(matched = emptyList(), selected = emptySet(), analyzeError = null, job = null)
        }
    }

    fun unknownSourcesIntent(): Intent {
        val app = getApplication<Application>()
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${app.packageName}")
        }
    }

    fun addGoogleAccountIntent(): Intent {
        return Intent(Settings.ACTION_ADD_ACCOUNT).apply {
            putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        }
    }

    fun openPlayStoreIntent(): Intent? {
        val app = getApplication<Application>()
        return app.packageManager.getLaunchIntentForPackage("com.android.vending")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"))
    }

    /**
     * 打开指定包在 Play 的详情页：已装 Play 商店走 market://，否则打开网页版。
     */
    fun playDetailsIntent(packageName: String): Intent {
        val app = getApplication<Application>()
        val pm = app.packageManager
        val playInstalled = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo("com.android.vending", android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("com.android.vending", 0)
            }
        }.isSuccess
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (playInstalled) setPackage("com.android.vending")
        }
        if (playInstalled && market.resolveActivity(pm) != null) {
            return market
        }
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun developerOptionsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    fun deviceLine(): String {
        val d = _state.value.snapshot?.device ?: return ""
        val abi = d.abis.firstOrNull().orEmpty()
        return "${d.manufacturer} ${d.model} · Android ${d.androidRelease} (API ${d.sdk}) · $abi"
    }
}

internal fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0)
    return String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024.0))
}
