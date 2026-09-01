package com.bytemyth.gms_installer.detect

import android.accounts.AccountManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.bytemyth.gms_installer.catalog.ComponentGroup
import com.bytemyth.gms_installer.catalog.GmsCatalog
import com.bytemyth.gms_installer.catalog.GmsComponent

enum class InstallState {
    Installed,
    Disabled,
    Missing,
}

enum class HealthState {
    /** 未装，不做健康判定 */
    NotApplicable,
    Healthy,
    Unhealthy,
}

data class ComponentStatus(
    val component: GmsComponent,
    val state: InstallState,
    val versionName: String?,
    val versionCode: Long?,
    val isSystem: Boolean = false,
    val health: HealthState = HealthState.NotApplicable,
    val healthIssues: List<String> = emptyList(),
    /** 用户态可卸载（含可卸系统更新）；纯系统预装且不可卸则为 false */
    val canUninstall: Boolean = false,
) {
    val isPresent: Boolean get() = state == InstallState.Installed || state == InstallState.Disabled
    val needsRepair: Boolean
        get() = health == HealthState.Unhealthy || state == InstallState.Disabled
}

data class DeviceProfile(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val abis: List<String>,
)

enum class PlanAction {
    /** 需要下载并安装 */
    Install,
    /** 已安装但被停用，引导去启用 */
    Enable,
    /** 已装但异常：卸载后重装（系统纯预装则提示清除数据） */
    Repair,
}

data class InstallPlanItem(
    val status: ComponentStatus,
    val action: PlanAction,
    val reason: String,
)

/**
 * 进入应用时根据本机检测结果给出的「必要安装方案」。
 * 已装好且健康的组件不会出现在 [needed] 里；异常的会标成 Repair。
 */
data class InstallPlan(
    val needed: List<InstallPlanItem>,
    val ready: List<ComponentStatus>,
    /** 缺包但不建议装（例如账号管理已被 Play 服务覆盖） */
    val covered: List<ComponentStatus> = emptyList(),
    val summary: String,
    val googleAuthReady: Boolean = false,
) {
    val isEmpty: Boolean get() = needed.isEmpty()
    val packageNames: Set<String> get() = needed.map { it.status.component.packageName }.toSet()
}

data class GmsSnapshot(
    val device: DeviceProfile,
    val components: List<ComponentStatus>,
    val plan: InstallPlan,
    val oemGoogleGate: OemGoogleServicesGate = OemGoogleServicesGate(
        supported = false,
        likelyOff = false,
        hintPath = "",
        buttonLabel = "",
    ),
) {
    val core = components.filter { it.component.group == ComponentGroup.Core }
    val apps = components.filter { it.component.group == ComponentGroup.Apps }

    val requiredMissing: Int
        get() = core.count { it.component.required && it.state != InstallState.Installed }

    val coreReady: Boolean
        get() = requiredMissing == 0 && core.none { it.component.required && it.health == HealthState.Unhealthy }
}

class GmsDetector(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val oemGateDetector = OemGoogleServicesGateDetector(appContext)

    fun snapshot(): GmsSnapshot {
        val device = DeviceProfile(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
        )
        val googleAuth = detectGoogleAuth()
        val components = GmsCatalog.all.map { component ->
            readStatus(component, googleAuth)
        }
        val maker = makerHintFor(device.manufacturer, device.brand)
        val plan = buildPlan(components, device, googleAuth)
        val oemGoogleGate = oemGateDetector.detect(components, googleAuth, maker)
        val planWithGate = if (oemGoogleGate.likelyOff) {
            plan.copy(
                summary = "请先打开系统 Google 服务（${oemGoogleGate.hintPath}）。 ${plan.summary}",
            )
        } else {
            plan
        }
        return GmsSnapshot(device, components, planWithGate, oemGoogleGate)
    }

    data class GoogleAuthStatus(
        val authenticatorPresent: Boolean,
        val hasAccounts: Boolean,
        val authenticatorPackage: String? = null,
    ) {
        val ready: Boolean get() = authenticatorPresent
    }

    fun detectGoogleAuth(): GoogleAuthStatus {
        return try {
            val am = AccountManager.get(appContext)
            val auth = am.authenticatorTypes.firstOrNull { it.type == "com.google" }
            val hasAccounts = runCatching { am.getAccountsByType("com.google").isNotEmpty() }.getOrDefault(false)
            GoogleAuthStatus(
                authenticatorPresent = auth != null,
                hasAccounts = hasAccounts,
                authenticatorPackage = auth?.packageName,
            )
        } catch (_: Exception) {
            GoogleAuthStatus(authenticatorPresent = false, hasAccounts = false)
        }
    }

    fun buildPlan(
        components: List<ComponentStatus>,
        device: DeviceProfile,
        googleAuth: GoogleAuthStatus = detectGoogleAuth(),
    ): InstallPlan {
        val core = components.filter { it.component.group == ComponentGroup.Core }
        val needed = mutableListOf<InstallPlanItem>()
        val ready = mutableListOf<ComponentStatus>()
        val covered = mutableListOf<ComponentStatus>()
        val playServicesOk = components.any {
            it.component.id == "play_services" &&
                it.state == InstallState.Installed &&
                it.health != HealthState.Unhealthy
        }

        for (item in core.sortedBy { it.component.order }) {
            when {
                item.state == InstallState.Missing -> {
                    when {
                        item.component.required -> needed += InstallPlanItem(
                            status = item,
                            action = PlanAction.Install,
                            reason = "未安装",
                        )
                        item.component.id == "account" -> {
                            if (googleAuth.ready || playServicesOk) {
                                covered += item
                            } else {
                                needed += InstallPlanItem(
                                    status = item,
                                    action = PlanAction.Install,
                                    reason = "未安装，且本机尚无 Google 登录能力",
                                )
                            }
                        }
                    }
                }
                item.state == InstallState.Disabled -> {
                    if (item.canUninstall) {
                        needed += InstallPlanItem(
                            status = item,
                            action = PlanAction.Repair,
                            reason = "已停用：${item.healthIssues.joinToString("；").ifBlank { "无法启动" }}，建议卸载重装",
                        )
                    } else {
                        needed += InstallPlanItem(
                            status = item,
                            action = PlanAction.Enable,
                            reason = if (item.isSystem) {
                                "系统组件已停用，请到应用信息里启用（无法普通卸载）"
                            } else {
                                "已安装但被停用"
                            },
                        )
                    }
                }
                item.health == HealthState.Unhealthy -> {
                    // 系统预装且核心仍可用：跳过，只补缺失包
                    if (item.isSystem && isCoreFunctionUsable(item.component.id, googleAuth)) {
                        ready += item
                    } else {
                        val issue = item.healthIssues.joinToString("；").ifBlank { "服务异常，无法正常启动" }
                        if (item.canUninstall) {
                            needed += InstallPlanItem(
                                status = item,
                                action = PlanAction.Repair,
                                reason = "已装但异常：$issue。将卸载后重装",
                            )
                        } else {
                            needed += InstallPlanItem(
                                status = item,
                                action = PlanAction.Enable,
                                reason = "系统预装异常：$issue。无法普通卸载，请清除数据或启用组件后重试",
                            )
                        }
                    }
                }
                else -> ready += item
            }
        }

        val maker = makerHintFor(device.manufacturer, device.brand)
        val summary = buildString {
            if (needed.isEmpty()) {
                append("核心组件已齐且健康，无需再装。")
            } else {
                val titles = needed.joinToString("、") {
                    val tag = when (it.action) {
                        PlanAction.Repair -> "修复"
                        PlanAction.Enable -> "启用"
                        PlanAction.Install -> "安装"
                    }
                    "${it.status.component.title}($tag)"
                }
                append("按本机检测，需处理：$titles。")
            }
            val systemReady = ready.filter { it.isSystem }
            if (systemReady.isNotEmpty()) {
                append(" 已检测到系统可用：${systemReady.joinToString("、") { it.component.title }}，将跳过。")
            }
            if (covered.any { it.component.id == "account" }) {
                when {
                    googleAuth.hasAccounts ->
                        append(" 账号管理缺失，但 Play 服务已可登录（本机已有 Google 账号），不必侧载。")
                    googleAuth.authenticatorPresent || playServicesOk ->
                        append(" 账号管理缺失，但 Play 服务已提供 Google 登录，不必侧载。")
                }
            }
            if (maker == MakerHint.Samsung) {
                append(" 三星机勿强行覆盖系统服务框架，易触发权限冲突。")
            }
            if (maker == MakerHint.Huawei || maker == MakerHint.Honor) {
                append(" 华为/荣耀可能拦截 GMS，装上后仍可能无法登录。")
            }
        }

        return InstallPlan(
            needed = needed,
            ready = ready,
            covered = covered,
            summary = summary.trim(),
            googleAuthReady = googleAuth.ready,
        )
    }

    private fun readStatus(component: GmsComponent, googleAuth: GoogleAuthStatus): ComponentStatus {
        return try {
            val flags = PackageManager.GET_META_DATA
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    component.packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(component.packageName, flags)
            }
            val setting = try {
                packageManager.getApplicationEnabledSetting(component.packageName)
            } catch (_: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            val disabledByUser = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val appInfo = info.applicationInfo
            val flagsVal = appInfo?.flags ?: 0
            val suspended = (flagsVal and ApplicationInfo.FLAG_SUSPENDED) != 0
            val stopped = (flagsVal and ApplicationInfo.FLAG_STOPPED) != 0
            val isSystem = (flagsVal and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (flagsVal and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val appEnabled = appInfo?.enabled != false
            val state = if (!disabledByUser && appEnabled && !suspended) {
                InstallState.Installed
            } else {
                InstallState.Disabled
            }
            // 用户应用可卸；系统更新可卸回预装（仍标可卸，用于“卸更新再重装”）
            val canUninstall = !isSystem || isUpdatedSystem

            val issues = mutableListOf<String>()
            if (disabledByUser || !appEnabled) issues += "应用被停用"
            if (suspended) issues += "应用被挂起"
            if (stopped && state == InstallState.Installed) {
                // FLAG_STOPPED 在从未启动时也会置位；结合其它探针再定是否异常
            }
            issues += probeRuntimeIssues(component, googleAuth, stopped)

            val systemPkg = isSystem || isUpdatedSystem
            // 系统预装：只要核心能力可用，就视为健康，避免误报「异常」打断补装缺失包
            val coreUsable = state == InstallState.Installed &&
                isCoreFunctionUsable(component.id, googleAuth)
            val health = when {
                state == InstallState.Disabled -> HealthState.Unhealthy
                systemPkg && coreUsable -> HealthState.Healthy
                issues.isNotEmpty() -> HealthState.Unhealthy
                else -> HealthState.Healthy
            }
            val reportedIssues = when {
                state == InstallState.Disabled -> issues.distinct()
                systemPkg && coreUsable -> emptyList()
                else -> issues.distinct()
            }

            ComponentStatus(
                component = component,
                state = state,
                versionName = info.versionName,
                versionCode = versionCode,
                isSystem = systemPkg,
                health = health,
                healthIssues = reportedIssues,
                canUninstall = canUninstall && component.group == ComponentGroup.Core,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            ComponentStatus(component, InstallState.Missing, null, null, false)
        }
    }

    private fun probeRuntimeIssues(
        component: GmsComponent,
        googleAuth: GoogleAuthStatus,
        stopped: Boolean,
    ): List<String> {
        val issues = mutableListOf<String>()
        when (component.id) {
            "gsf" -> {
                // 注意：多数第三方应用无权 acquire gservices ContentProvider，
                // 拿不到客户端不等于框架损坏（三星等机型上几乎必失败）。
                // 已启用的 GSF 包本身即可视为底座可用；仅在包被停用时由上层标异常。
            }
            "play_services" -> {
                if (!googleAuth.authenticatorPresent) {
                    issues += "已安装但未注册 Google 账号认证器，登录服务异常"
                } else if (
                    googleAuth.authenticatorPackage != null &&
                    googleAuth.authenticatorPackage != component.packageName &&
                    googleAuth.authenticatorPackage != "com.google.android.gsf.login"
                ) {
                    // 其它包提供认证器也算可用，不算异常
                }
                if (!componentEnabled(
                        ComponentName(
                            component.packageName,
                            "com.google.android.gms.auth.account.authenticator.GoogleAccountAuthenticatorService",
                        ),
                    )
                ) {
                    // 组件名可能因版本变化不存在；仅在能查到包信息时才追加
                    val known = hasDeclaredComponent(
                        component.packageName,
                        "com.google.android.gms.auth.account.authenticator.GoogleAccountAuthenticatorService",
                    )
                    if (known) issues += "Google 账号认证服务被停用"
                }
                if (stopped && !googleAuth.authenticatorPresent) {
                    issues += "进程被强制停止且服务未恢复"
                }
            }
            "play_store" -> {
                val launch = packageManager.getLaunchIntentForPackage(component.packageName)
                if (launch == null) {
                    issues += "没有可启动的商店入口，安装可能不完整"
                }
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.vending"))
                val handlers = packageManager.queryIntentActivities(market, PackageManager.MATCH_DEFAULT_ONLY)
                val handledByStore = handlers.any { it.activityInfo?.packageName == component.packageName }
                if (!handledByStore && launch == null) {
                    issues += "无法处理 market 链接"
                }
            }
            "account" -> {
                // 独立账号管理缺失时由 plan 处理；已装但若认证器不在本包也可接受
            }
        }
        return issues
    }

    /**
     * 判断核心组件是否「够用」：系统自带残缺 GMS 时，只要底座可用就跳过，只补缺失包。
     * 调用方需已确认包为 [InstallState.Installed]（未停用/未挂起）。
     */
    private fun isCoreFunctionUsable(componentId: String, googleAuth: GoogleAuthStatus): Boolean {
        return when (componentId) {
            // 已启用即够用；勿依赖 gservices（第三方常无权访问）
            "gsf" -> true
            "play_services" -> googleAuth.authenticatorPresent
            "play_store" -> {
                val launch = packageManager.getLaunchIntentForPackage("com.android.vending")
                if (launch != null) return true
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.vending"))
                packageManager.queryIntentActivities(market, PackageManager.MATCH_DEFAULT_ONLY)
                    .any { it.activityInfo?.packageName == "com.android.vending" }
            }
            "account" -> googleAuth.authenticatorPresent
            else -> true
        }
    }

    private fun componentEnabled(name: ComponentName): Boolean {
        return try {
            when (packageManager.getComponentEnabledSetting(name)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                -> false
                else -> true
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun hasDeclaredComponent(packageName: String, className: String): Boolean {
        return try {
            val flags = PackageManager.GET_SERVICES
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags)
            }
            info.services?.any { it.name == className } == true
        } catch (_: Exception) {
            false
        }
    }
}

enum class MakerHint {
    Generic,
    Xiaomi,
    Huawei,
    Honor,
    Oppo,
    Vivo,
    Samsung,
}

fun makerHintFor(manufacturer: String, brand: String): MakerHint {
    val blob = "$manufacturer $brand".lowercase()
    return when {
        listOf("xiaomi", "redmi", "poco", "blackshark").any { blob.contains(it) } -> MakerHint.Xiaomi
        blob.contains("huawei") || blob.contains("harmony") -> MakerHint.Huawei
        blob.contains("honor") -> MakerHint.Honor
        listOf("oppo", "oneplus", "realme").any { blob.contains(it) } -> MakerHint.Oppo
        blob.contains("vivo") || blob.contains("iqoo") -> MakerHint.Vivo
        blob.contains("samsung") -> MakerHint.Samsung
        else -> MakerHint.Generic
    }
}

/** 把系统安装失败码翻成可读说明。 */
fun explainInstallFailure(raw: String, packageName: String? = null): String {
    val text = raw
    return when {
        text.contains("DUPLICATE_PERMISSION", ignoreCase = true) ->
            "与系统其它应用权限名冲突，无法覆盖安装。若该组件已是系统预装，可跳过。"
        text.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) -> {
            if (packageName == "com.google.android.gsf.login") {
                "签名与本机 GSF/Play 服务的 sharedUser 不一致，装不上。现代机一般由 Play 服务负责登录，不必侧载账号管理。"
            } else {
                "与本机已有同名包或 sharedUser 签名不兼容，无法安装/更新。"
            }
        }
        text.contains("VERSION_DOWNGRADE", ignoreCase = true) ->
            "不能降级安装：本机已有更高版本。"
        text.contains("INSUFFICIENT_STORAGE", ignoreCase = true) ->
            "存储空间不足。"
        text.contains("INVALID_APK", ignoreCase = true) ->
            "安装包无效或已损坏，请换源重下。"
        else -> text
    }
}
