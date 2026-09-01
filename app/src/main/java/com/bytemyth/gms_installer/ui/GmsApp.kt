@file:OptIn(ExperimentalMaterial3Api::class)

package com.bytemyth.gms_installer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytemyth.gms_installer.catalog.ResolvedRomLinks
import com.bytemyth.gms_installer.catalog.RomDownloadLink
import com.bytemyth.gms_installer.catalog.RomOption
import com.bytemyth.gms_installer.detect.ComponentStatus
import com.bytemyth.gms_installer.detect.HealthState
import com.bytemyth.gms_installer.detect.InstallPlan
import com.bytemyth.gms_installer.detect.InstallState
import com.bytemyth.gms_installer.detect.MakerHint
import com.bytemyth.gms_installer.detect.PlanAction
import com.bytemyth.gms_installer.install.MatchedPackage
import com.bytemyth.gms_installer.ui.theme.iconPalette

private val PanelShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmsApp(viewModel: GmsViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importUris(uris) }

    LaunchedEffect(state.snackbar) {
        val message = state.snackbar ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    state.dialog?.let { dialog ->
        fun runAction(action: UiDialogAction) {
            when (action.nav) {
                DialogNav.OemGoogleSettings -> {
                    val intent = viewModel.oemGoogleSettingsIntent()
                    if (intent != null) {
                        runCatching { context.startActivity(intent) }
                    }
                }
                DialogNav.AppDetails -> {
                    action.packageName?.let { pkg ->
                        context.startActivity(viewModel.appDetailsIntent(pkg))
                    }
                }
                DialogNav.None -> Unit
            }
            viewModel.dismissDialog()
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(dialog.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dialog.message)
                    if (dialog.actions.size > 1) {
                        dialog.actions.forEach { action ->
                            TextButton(
                                onClick = { runAction(action) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val single = dialog.actions.singleOrNull()
                if (single != null) {
                    TextButton(onClick = { runAction(single) }) {
                        Text(single.label)
                    }
                } else {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(if (dialog.actions.isEmpty()) "知道了" else "关闭")
                    }
                }
            },
            dismissButton = if (dialog.actions.isNotEmpty()) {
                {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text("关闭")
                    }
                }
            } else {
                null
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "谷歌框架 Caju",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = scheme.primary)
                    }
                    IconButton(
                        onClick = { context.startActivity(viewModel.unknownSourcesIntent()) },
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "未知来源")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = scheme.surface,
                tonalElevation = 0.dp,
            ) {
                val palette = MaterialTheme.iconPalette
                @Composable
                fun tabColors(accent: Color, soft: Color) = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = soft,
                    // 未选中也保留本色，略淡，底栏始终是彩色的
                    unselectedIconColor = accent.copy(alpha = 0.48f),
                    unselectedTextColor = accent.copy(alpha = 0.55f),
                )
                NavigationBarItem(
                    selected = state.tab == MainTab.Overview,
                    onClick = { viewModel.selectTab(MainTab.Overview) },
                    icon = { Icon(Icons.Outlined.Extension, contentDescription = null) },
                    label = { Text("总览") },
                    colors = tabColors(palette.blue, palette.blueSoft),
                )
                NavigationBarItem(
                    selected = state.tab == MainTab.Install,
                    onClick = { viewModel.selectTab(MainTab.Install) },
                    icon = { Icon(Icons.Outlined.InstallMobile, contentDescription = null) },
                    label = { Text("安装") },
                    colors = tabColors(palette.green, palette.greenSoft),
                )
                NavigationBarItem(
                    selected = state.tab == MainTab.Guide,
                    onClick = { viewModel.selectTab(MainTab.Guide) },
                    icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                    label = { Text("指引") },
                    colors = tabColors(palette.yellow, palette.yellowSoft),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (state.tab) {
            MainTab.Overview -> OverviewPane(
                padding = padding,
                state = state,
                deviceLine = viewModel.deviceLine(),
                canOneClickRepair = viewModel.canOneClickRepair(),
                onImport = { picker.launch(arrayOf("*/*")) },
                onOpenPlay = {
                    viewModel.openPlayStoreIntent()?.let { context.startActivity(it) }
                },
                onAddAccount = { context.startActivity(viewModel.addGoogleAccountIntent()) },
                onSelectRomApi = viewModel::selectRomApi,
                onOpenUrl = { url ->
                    runCatching { context.startActivity(viewModel.openUrlIntent(url)) }
                },
                onInstallLink = { link ->
                    viewModel.installDirect(link) { intent -> context.startActivity(intent) }
                },
                onOneClickRepair = {
                    viewModel.oneClickRepair { intent -> context.startActivity(intent) }
                },
                onOpenAppDetails = { pkg ->
                    context.startActivity(viewModel.appDetailsIntent(pkg))
                },
                onOpenPlayDetails = { pkg ->
                    runCatching { context.startActivity(viewModel.playDetailsIntent(pkg)) }
                },
            )
            MainTab.Install -> InstallPane(
                padding = padding,
                state = state,
                onImport = { picker.launch(arrayOf("*/*")) },
                onToggle = viewModel::toggleSelected,
                onInstall = { viewModel.startInstall { intent -> context.startActivity(intent) } },
                onClear = viewModel::clearImport,
            )
            MainTab.Guide -> GuidePane(
                padding = padding,
                hint = viewModel.makerHint,
                romLinks = state.romLinks,
                jobRunning = state.job?.running == true,
                onUnknownSources = { context.startActivity(viewModel.unknownSourcesIntent()) },
                onDeveloper = {
                    runCatching { context.startActivity(viewModel.developerOptionsIntent()) }
                },
                onOpenUrl = { url ->
                    runCatching { context.startActivity(viewModel.openUrlIntent(url)) }
                },
                onInstallLink = { link ->
                    viewModel.installDirect(link) { intent -> context.startActivity(intent) }
                },
            )
        }
    }
}

@Composable
private fun OverviewPane(
    padding: PaddingValues,
    state: UiState,
    deviceLine: String,
    canOneClickRepair: Boolean,
    onImport: () -> Unit,
    onOpenPlay: () -> Unit,
    onAddAccount: () -> Unit,
    onSelectRomApi: (Int?) -> Unit,
    onOpenUrl: (String) -> Unit,
    onInstallLink: (RomDownloadLink) -> Unit,
    onOneClickRepair: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onOpenPlayDetails: (String) -> Unit,
) {
    val snapshot = state.snapshot
    val jobRunning = state.job?.running == true
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatusHero(
                scanning = state.scanning,
                plan = snapshot?.plan,
                deviceLine = deviceLine,
                canOneClickRepair = canOneClickRepair,
                jobRunning = jobRunning,
                progressByKey = state.job?.progressByKey.orEmpty(),
                onOneClickRepair = onOneClickRepair,
            )
        }
        if (state.romLinks != null && snapshot != null) {
            item {
                InstallPlanPanel(
                    plan = snapshot.plan,
                    links = state.romLinks,
                    options = state.romOptions,
                    overrideApi = state.romOverrideApi,
                    deviceSdk = snapshot.device.sdk,
                    onSelectRomApi = onSelectRomApi,
                    onOpenUrl = onOpenUrl,
                    onInstallLink = onInstallLink,
                    onOneClickRepair = onOneClickRepair,
                    onOpenAppDetails = onOpenAppDetails,
                    jobRunning = jobRunning,
                    progressByKey = state.job?.progressByKey.orEmpty(),
                )
            }
        }
        item {
            SectionHeader(icon = Icons.Outlined.PhonelinkSetup, title = "快捷操作", accent = 0)
            Spacer(Modifier.height(10.dp))
            val palette = MaterialTheme.iconPalette
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentAction(
                    label = "导入",
                    icon = Icons.Outlined.FileOpen,
                    soft = palette.blueSoft,
                    tint = palette.blue,
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                )
                AccentAction(
                    label = "商店",
                    icon = Icons.Outlined.Shop,
                    soft = palette.redSoft,
                    tint = palette.red,
                    onClick = onOpenPlay,
                    modifier = Modifier.weight(1f),
                )
                AccentAction(
                    label = "账号",
                    icon = Icons.Outlined.AccountCircle,
                    soft = palette.greenSoft,
                    tint = palette.green,
                    onClick = onAddAccount,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { SectionHeader(icon = Icons.Outlined.Extension, title = "核心组件", accent = 1) }
        items(snapshot?.core.orEmpty(), key = { it.component.packageName }) { item ->
            ComponentRow(item, onClick = { onOpenPlayDetails(item.component.packageName) })
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader(icon = Icons.Outlined.Shop, title = "常用应用", accent = 3)
        }
        items(snapshot?.apps.orEmpty(), key = { it.component.packageName }) { item ->
            ComponentRow(item, onClick = { onOpenPlayDetails(item.component.packageName) })
        }
        item {
            Text(
                "点组件可打开 Play 详情（无商店则打开网页）。缺核心包请用上方一键修复或导入安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccentAction(
    label: String,
    icon: ImageVector,
    soft: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentTint = if (enabled) tint else tint.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) soft else soft.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = contentTint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentTint)
    }
}

@Composable
private fun PuzzleAccentStrip(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.iconPalette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {
        palette.accents.forEach { color ->
            Box(Modifier.weight(1f).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, accent: Int = 0) {
    val palette = MaterialTheme.iconPalette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(palette.soft(accent)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = palette.accent(accent),
                modifier = Modifier.size(16.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StatusHero(
    scanning: Boolean,
    plan: InstallPlan?,
    deviceLine: String,
    canOneClickRepair: Boolean,
    jobRunning: Boolean,
    progressByKey: Map<String, ItemProgress>,
    onOneClickRepair: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.iconPalette
    val ready = plan != null && plan.isEmpty
    // 任务进行中不切换整卡配色/文案结构，避免 LazyColumn 抖动
    val (icon, tint, bg) = when {
        scanning && !jobRunning -> Triple(Icons.Outlined.Refresh, palette.blue, palette.blueSoft)
        ready && !jobRunning -> Triple(Icons.Outlined.CheckCircle, palette.green, palette.greenSoft)
        else -> Triple(Icons.Outlined.Build, palette.yellow, palette.yellowSoft)
    }
    val jobProgress = aggregateJobProgress(progressByKey)
    val buttonProgress = when {
        scanning && !jobRunning -> ItemProgress(ItemPhase.Downloading, -1f, "检测中")
        jobRunning -> jobProgress
        else -> null
    }
    val buttonLabel = when {
        scanning && !jobRunning -> "检测中…"
        jobRunning -> progressLabel(jobProgress) ?: "处理中…"
        canOneClickRepair -> "一键修复"
        else -> "一键修复（检测）"
    }
    val titleText = when {
        scanning && !jobRunning -> "正在检测…"
        jobRunning -> "正在修复…"
        ready -> "状态良好"
        else -> {
            val n = plan?.needed?.size ?: 0
            val r = plan?.needed?.count { it.action == PlanAction.Repair } ?: 0
            if (r > 0) "需处理 $n 项 · 含 $r 项修复" else "还需 $n 项"
        }
    }
    val summaryText = when {
        ready -> plan?.summary ?: "核心组件已齐且健康。"
        else -> plan?.summary.orEmpty().ifBlank { "按本机检测生成安装与修复方案。" }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(bg),
    ) {
        PuzzleAccentStrip()
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    titleText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressFillButton(
                label = buttonLabel,
                progress = buttonProgress,
                enabled = !jobRunning && !(scanning && !jobRunning),
                onClick = onOneClickRepair,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                emphasized = true,
                leading = {
                    Icon(Icons.Outlined.Build, null, Modifier.size(18.dp))
                },
            )
            Text(
                if (jobRunning) {
                    jobProgress?.detail?.takeIf { it.isNotBlank() } ?: "进度显示在按钮背景上"
                } else {
                    deviceLine
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InstallPlanPanel(
    plan: InstallPlan,
    links: ResolvedRomLinks,
    options: List<RomOption>,
    overrideApi: Int?,
    deviceSdk: Int?,
    onSelectRomApi: (Int?) -> Unit,
    onOpenUrl: (String) -> Unit,
    onInstallLink: (RomDownloadLink) -> Unit,
    onOneClickRepair: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    jobRunning: Boolean,
    progressByKey: Map<String, ItemProgress>,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (overrideApi == null) {
        "自动 · Android ${links.option.android}（API ${links.option.api}）"
    } else {
        "Android ${links.option.android}（API ${links.option.api}）"
    }
    val installNeeded = plan.needed.filter { it.action == PlanAction.Install }
    val repairNeeded = plan.needed.filter { it.action == PlanAction.Repair }
    val enableNeeded = plan.needed.filter { it.action == PlanAction.Enable }
    val downloadNeeded = installNeeded + repairNeeded
    val planLinks = links.links.filter { link ->
        downloadNeeded.any { it.status.component.packageName == link.packageName }
    }
    val canOneClick = planLinks.any { it.hasDirect }
    val oneClickCount = planLinks.count { it.hasDirect }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .border(1.dp, scheme.outlineVariant, PanelShape),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PuzzleAccentStrip()
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        SectionHeader(icon = Icons.Outlined.Build, title = "安装方案", accent = 2)
        Text(
            "${links.abiTrackLabel} · 缺包装、异常修",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("对照 ROM") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("自动（本机 API ${deviceSdk ?: "?"}）") },
                    onClick = {
                        onSelectRomApi(null)
                        expanded = false
                    },
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("Android ${option.android}（API ${option.api}）") },
                        onClick = {
                            onSelectRomApi(option.api)
                            expanded = false
                        },
                    )
                }
            }
        }
        links.warning?.let { warning ->
            Text(warning, style = MaterialTheme.typography.bodySmall, color = scheme.tertiary)
        }

        if (plan.ready.isNotEmpty()) {
            QuietLabel("已就绪")
            plan.ready.forEach { item ->
                QuietLine(
                    "· ${item.component.title}" +
                        if (item.isSystem) " · 系统" else " · ${item.versionName ?: "已装"}",
                )
            }
        }
        if (plan.covered.isNotEmpty()) {
            QuietLabel("不必侧载")
            plan.covered.forEach { item ->
                val tip = when (item.component.id) {
                    "account" -> if (plan.googleAuthReady) "Play 服务已可登录" else "已覆盖"
                    else -> "已具备"
                }
                QuietLine("· ${item.component.title} · $tip")
            }
        }
        if (enableNeeded.isNotEmpty()) {
            QuietLabel("需手动处理")
            enableNeeded.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "· ${item.status.component.title}：${item.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.tertiary,
                    )
                    TextButton(
                        onClick = { onOpenAppDetails(item.status.component.packageName) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("应用信息")
                    }
                }
            }
        }

        if (downloadNeeded.isEmpty()) {
            Text(
                "没有需要下载的项。可添加账号或导入 APK。",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            QuietLabel(if (repairNeeded.isNotEmpty()) "需修复 / 安装" else "需安装")
            val batchProgress = aggregateJobProgress(
                progressByKey,
                planLinks.map { it.packageName },
            )
            ProgressFillButton(
                label = when {
                    jobRunning -> progressLabel(batchProgress) ?: "处理中…"
                    canOneClick -> "一键修复（$oneClickCount）"
                    else -> "一键修复"
                },
                progress = if (jobRunning) batchProgress else null,
                enabled = !jobRunning,
                onClick = onOneClickRepair,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                emphasized = true,
                leading = {
                    Icon(Icons.Outlined.Build, null, Modifier.size(18.dp))
                },
            )
            planLinks.forEach { item ->
                val planItem = downloadNeeded.firstOrNull {
                    it.status.component.packageName == item.packageName
                }
                val actionTag = when (planItem?.action) {
                    PlanAction.Repair -> "卸载重装"
                    PlanAction.Install -> "安装"
                    else -> null
                }
                ComponentDownloadBlock(
                    item = item,
                    subtitle = listOfNotNull(actionTag, planItem?.reason).joinToString(" · ")
                        .ifBlank { null },
                    jobRunning = jobRunning,
                    progress = progressByKey[item.packageName],
                    onOpenUrl = onOpenUrl,
                    onInstall = { onInstallLink(item) },
                    installLabel = if (planItem?.action == PlanAction.Repair) "修复" else "安装",
                )
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
            }
        }
        Text(
            "异常项会先卸载再装。系统纯预装请用「应用信息」。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        }
    }
}

@Composable
private fun QuietLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.iconPalette.blue,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ComponentDownloadBlock(
    item: RomDownloadLink,
    subtitle: String?,
    jobRunning: Boolean,
    progress: ItemProgress?,
    onOpenUrl: (String) -> Unit,
    onInstall: () -> Unit,
    installLabel: String = "安装",
    showMirror: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(item.title, fontWeight = FontWeight.Medium)
        Text(
            item.packageName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            if (item.hasDirect) {
                ProgressFillButton(
                    label = progressLabel(progress) ?: installLabel,
                    progress = progress,
                    enabled = !jobRunning,
                    onClick = onInstall,
                    modifier = Modifier.weight(1f),
                    leading = {
                        Icon(Icons.Outlined.InstallMobile, null, Modifier.size(16.dp))
                    },
                )
            }
            if (item.apkpureUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = { onOpenUrl(item.apkpureDownloadUrl.ifBlank { item.apkpureUrl }) },
                    modifier = Modifier.weight(1f),
                    enabled = !jobRunning,
                    shape = RoundedCornerShape(12.dp),
                ) { Text("APKPure") }
            }
            if (showMirror) {
                OutlinedButton(
                    onClick = { onOpenUrl(item.apkPageUrl.ifBlank { item.url }) },
                    modifier = Modifier.weight(1f),
                    enabled = !jobRunning,
                    shape = RoundedCornerShape(12.dp),
                ) { Text("镜像") }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    item: ComponentStatus,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.iconPalette
    val unhealthy = item.needsRepair || item.health == HealthState.Unhealthy
    val (icon, tint, bar) = when {
        item.state == InstallState.Missing ->
            Triple(Icons.Outlined.ErrorOutline, palette.red, palette.red)
        unhealthy || item.state == InstallState.Disabled ->
            Triple(Icons.Outlined.WarningAmber, palette.yellow, palette.yellow)
        else ->
            Triple(Icons.Outlined.CheckCircle, palette.green, palette.green)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(bar),
        )
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).widthIn(min = 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.component.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.component.required) {
                    Text("必要", style = MaterialTheme.typography.labelSmall, color = palette.blue, maxLines = 1)
                }
                if (item.isSystem && item.state != InstallState.Missing) {
                    Text("系统", style = MaterialTheme.typography.labelSmall, color = palette.green, maxLines = 1)
                }
            }
            Text(
                item.component.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (item.healthIssues.isNotEmpty()) {
                Text(
                    item.healthIssues.joinToString("；"),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.yellow,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                item.state == InstallState.Missing -> "未安装"
                item.state == InstallState.Disabled -> "已停用"
                unhealthy -> "异常"
                else -> item.versionName ?: "已安装"
            },
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 96.dp).padding(top = 2.dp),
        )
        }
    }
}

@Composable
private fun ContentPanel(
    modifier: Modifier = Modifier,
    softBackground: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .then(
                if (softBackground != null) {
                    Modifier.background(softBackground)
                } else {
                    Modifier.border(1.dp, scheme.outlineVariant, PanelShape)
                },
            ),
    ) {
        PuzzleAccentStrip()
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun InstallPane(
    padding: PaddingValues,
    state: UiState,
    onImport: () -> Unit,
    onToggle: (String) -> Unit,
    onInstall: () -> Unit,
    onClear: () -> Unit,
) {
    val palette = MaterialTheme.iconPalette
    val scheme = MaterialTheme.colorScheme
    val jobRunning = state.job?.running == true
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ContentPanel(softBackground = palette.blueSoft, contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.InstallMobile,
                        contentDescription = null,
                        tint = palette.blue,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("导入安装", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "选择 APK 或含 APK 的 zip，按推荐顺序排队安装。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                val pickProgress = if (state.analyzing) {
                    ItemProgress(ItemPhase.Downloading, -1f, "解析中")
                } else {
                    null
                }
                ProgressFillButton(
                    label = if (state.analyzing) "解析中…" else "选择文件",
                    progress = pickProgress,
                    enabled = !jobRunning && !state.analyzing,
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    emphasized = true,
                    leading = {
                        Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentAction(
                        label = "清空列表",
                        icon = Icons.Outlined.Refresh,
                        soft = palette.yellowSoft,
                        tint = palette.yellow,
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        enabled = !jobRunning,
                    )
                }
            }
        }
        state.analyzeError?.let { error ->
            item {
                ContentPanel(softBackground = palette.redSoft) {
                    Text(error, color = palette.red, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.matched.isEmpty()) {
            val orderedKeys = state.romLinks?.links?.map { it.packageName }
                ?.filter { it in (state.job?.progressByKey?.keys.orEmpty()) }
                .orEmpty()
                .ifEmpty { state.job?.progressByKey?.keys?.toList().orEmpty() }
            if (orderedKeys.isNotEmpty()) {
                item { SectionHeader(icon = Icons.Outlined.Build, title = "进行中", accent = 2) }
            }
            items(orderedKeys, key = { "prog_$it" }) { pkg ->
                val progress = state.job?.progressByKey?.get(pkg)
                val title = state.romLinks?.links?.firstOrNull { it.packageName == pkg }?.title ?: pkg
                QueueProgressCard(title = title, packageName = pkg, progress = progress)
            }
        }
        if (state.matched.isNotEmpty()) {
            item { SectionHeader(icon = Icons.Outlined.Extension, title = "待安装队列", accent = 1) }
        }
        items(state.matched, key = { it.packageName }) { item ->
            MatchCard(
                item = item,
                checked = item.packageName in state.selected,
                enabled = !jobRunning,
                progress = state.job?.progressByKey?.get(item.packageName),
                onToggle = { onToggle(item.packageName) },
            )
        }
        if (state.matched.isNotEmpty()) {
            item {
                val selected = state.matched.filter { it.packageName in state.selected }
                val installProgress = aggregateJobProgress(
                    state.job?.progressByKey.orEmpty(),
                    selected.map { it.packageName },
                )
                ProgressFillButton(
                    label = when {
                        jobRunning -> progressLabel(installProgress) ?: "安装中…"
                        else -> "安装已勾选（${state.selected.size}）"
                    },
                    progress = if (jobRunning) installProgress else null,
                    enabled = !jobRunning && state.selected.isNotEmpty(),
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    emphasized = true,
                    leading = {
                        Icon(Icons.Outlined.InstallMobile, null, Modifier.size(18.dp))
                    },
                )
            }
        }
        state.job?.let { job ->
            item {
                ContentPanel {
                    SectionHeader(icon = Icons.Outlined.Build, title = "安装日志", accent = 2)
                    Text(
                        when {
                            job.running && job.downloadLabel != null -> "进行中 ${job.index + 1}/${job.total}"
                            job.running -> "安装中 ${job.index + 1}/${job.total}"
                            else -> "本轮结束"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    job.log.takeLast(10).forEach { line ->
                        QuietLine(line)
                    }
                }
            }
        }
        item {
            Text(
                "导入的包按目录顺序安装；异常项可先卸再装。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueProgressCard(title: String, packageName: String, progress: ItemProgress?) {
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.iconPalette
    val bar = when (progress?.phase) {
        ItemPhase.Failed -> palette.red
        ItemPhase.Success, ItemPhase.Skipped -> palette.green
        ItemPhase.Waiting, ItemPhase.Uninstalling -> palette.yellow
        else -> palette.blue
    }
    val fill = progressFillColor(progress, palette.blue, palette.green, palette.red)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
        Box(modifier = Modifier.weight(1f)) {
            ProgressFillBackground(
                progress = progress,
                fill = fill.copy(alpha = 0.35f),
                shape = RoundedCornerShape(0.dp),
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    progressLabel(progress) ?: "排队中",
                    style = MaterialTheme.typography.labelMedium,
                    color = bar,
                )
                progress?.detail?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MatchCard(
    item: MatchedPackage,
    checked: Boolean,
    enabled: Boolean,
    progress: ItemProgress?,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.iconPalette
    val bar = when {
        progress?.phase == ItemPhase.Failed -> palette.red
        progress?.phase == ItemPhase.Success -> palette.green
        item.component == null -> palette.yellow
        checked -> palette.blue
        else -> scheme.outlineVariant
    }
    val fill = progressFillColor(progress, palette.blue, palette.green, palette.red)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onToggle),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
        Box(modifier = Modifier.weight(1f)) {
            ProgressFillBackground(
                progress = progress,
                fill = fill.copy(alpha = 0.35f),
                shape = RoundedCornerShape(0.dp),
            )
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
                Column(modifier = Modifier.weight(1f).widthIn(min = 0.dp)) {
                    Text(item.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.versionName ?: "未知版本"} · ${item.payloads.size} 个 APK" +
                            if (item.component == null) " · 目录外" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    progress?.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = palette.blue)
                    }
                    item.warning?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.red)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressFillButton(
    label: String,
    progress: ItemProgress?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    val fill = progressFillColor(progress, scheme.primary, scheme.secondary, scheme.error)
    val baseBg = if (emphasized) {
        scheme.primary.copy(alpha = if (enabled) 0.92f else 0.35f)
    } else {
        scheme.primaryContainer.copy(alpha = 0.55f)
    }
    val contentColor = if (emphasized) {
        scheme.onPrimary
    } else {
        scheme.onPrimaryContainer
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .border(
                1.dp,
                if (emphasized) scheme.primary.copy(alpha = 0.2f) else scheme.primary.copy(alpha = 0.35f),
                shape,
            )
            .background(baseBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ProgressFillBackground(
            progress = progress,
            fill = if (emphasized) Color.White.copy(alpha = 0.28f) else fill,
            shape = shape,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressFillBackground(
    progress: ItemProgress?,
    fill: Color,
    shape: RoundedCornerShape,
) {
    if (progress == null) return
    when {
        progress.phase == ItemPhase.Queued && progress.fraction <= 0f -> Unit
        progress.fraction < 0f -> {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxSize().clip(shape),
                color = fill.copy(alpha = 0.55f),
                trackColor = Color.Transparent,
            )
        }
        else -> {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.fraction.coerceIn(0f, 1f))
                    .background(fill.copy(alpha = 0.55f)),
            )
        }
    }
}

/** 汇总多项进度，用于一键修复 / 批量安装按钮背景。 */
private fun aggregateJobProgress(
    progressByKey: Map<String, ItemProgress>,
    keys: List<String>? = null,
): ItemProgress? {
    val items = if (keys == null) {
        progressByKey.values.toList()
    } else {
        keys.mapNotNull { progressByKey[it] }
    }
    if (items.isEmpty()) return null
    val activePhases = setOf(
        ItemPhase.Uninstalling,
        ItemPhase.Downloading,
        ItemPhase.Writing,
        ItemPhase.Waiting,
        ItemPhase.Queued,
    )
    val active = items.firstOrNull { it.phase in activePhases && it.phase != ItemPhase.Queued }
        ?: items.firstOrNull { it.phase == ItemPhase.Queued }
        ?: items.last()
    val done = items.count {
        it.phase == ItemPhase.Success || it.phase == ItemPhase.Skipped || it.phase == ItemPhase.Failed
    }
    val deterministic = items.all { it.fraction >= 0f }
    val fraction = if (deterministic) {
        items.map { it.fraction.coerceIn(0f, 1f) }.average().toFloat()
    } else {
        -1f
    }
    val phaseLabel = progressLabel(active) ?: active.phase.name
    return ItemProgress(
        phase = active.phase,
        fraction = fraction,
        detail = "$done/${items.size} · $phaseLabel" +
            (active.detail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
    )
}

private fun progressFillColor(
    progress: ItemProgress?,
    primary: Color,
    secondary: Color,
    error: Color,
): Color = when (progress?.phase) {
    ItemPhase.Success -> secondary
    ItemPhase.Failed -> error
    ItemPhase.Waiting, ItemPhase.Uninstalling -> Color(0xFFFBBC05) // yellow
    ItemPhase.Downloading -> primary // blue
    ItemPhase.Writing -> secondary // green
    else -> primary
}

private fun progressLabel(progress: ItemProgress?): String? {
    if (progress == null) return null
    return when (progress.phase) {
        ItemPhase.Queued -> if (progress.fraction >= 1f) "待安装" else "排队中"
        ItemPhase.Uninstalling -> "卸载中"
        ItemPhase.Downloading -> if (progress.fraction < 0f) "下载中" else "下载 ${(progress.fraction * 100).toInt()}%"
        ItemPhase.Writing -> "写入 ${(progress.fraction * 100).toInt()}%"
        ItemPhase.Waiting -> "确认中"
        ItemPhase.Success -> "完成"
        ItemPhase.Failed -> "失败"
        ItemPhase.Skipped -> "跳过"
    }
}

@Composable
private fun GuidePane(
    padding: PaddingValues,
    hint: MakerHint,
    romLinks: ResolvedRomLinks?,
    jobRunning: Boolean,
    onUnknownSources: () -> Unit,
    onDeveloper: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onInstallLink: (RomDownloadLink) -> Unit,
) {
    val steps = guideFor(hint)
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.iconPalette
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ContentPanel(softBackground = palette.yellowSoft, contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = palette.yellow,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("安装顺序", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "1. 服务框架（GSF）\n2. 账号管理（如有）\n3. Play 服务\n4. Play 商店\n5. 重启\n6. 登录账号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        item {
            ContentPanel {
                SectionHeader(icon = Icons.Outlined.PhonelinkSetup, title = "这台手机", accent = 1)
                Text(steps, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        item {
            SectionHeader(icon = Icons.Outlined.Settings, title = "快捷设置", accent = 0)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentAction(
                    label = "未知应用",
                    icon = Icons.Outlined.Settings,
                    soft = palette.blueSoft,
                    tint = palette.blue,
                    onClick = onUnknownSources,
                    modifier = Modifier.weight(1f),
                )
                AccentAction(
                    label = "开发者",
                    icon = Icons.Outlined.Build,
                    soft = palette.greenSoft,
                    tint = palette.green,
                    onClick = onDeveloper,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (romLinks != null) {
            item {
                ContentPanel {
                    SectionHeader(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        title = "下载对照",
                        accent = 3,
                    )
                    Text(
                        "Android ${romLinks.option.android}（API ${romLinks.option.api}）· ${romLinks.abiTrackLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    romLinks.links.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                        }
                        ComponentDownloadBlock(
                            item = item,
                            subtitle = null,
                            jobRunning = jobRunning,
                            progress = null,
                            onOpenUrl = onOpenUrl,
                            onInstall = { onInstallLink(item) },
                            showMirror = false,
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Caju 不打包 Google 应用。请从你有权使用的来源取得匹配包。华为/荣耀可能系统拦截 GMS。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}


private fun guideFor(hint: MakerHint): String = when (hint) {
    MakerHint.Xiaomi ->
        "小米 / Redmi / POCO：先在「帐号与同步 → 谷歌基础服务」打开开关；再授权未知应用，允许 Play 服务自启动。"
    MakerHint.Huawei, MakerHint.Honor ->
        "华为 / 荣耀：关闭纯净模式。部分机型装上后仍可能无法登录。"
    MakerHint.Oppo ->
        "OPPO / 一加 / realme：设置里搜索并打开 Google / 谷歌基础服务；允许未知来源，放行 Play 服务自启动。"
    MakerHint.Vivo ->
        "vivo / iQOO：先在「账号与同步 → 谷歌基础服务」打开开关；再允许未知来源与高耗电。"
    MakerHint.Samsung ->
        "三星一般自带 GMS。若被卸，按核心顺序补装，注意 ABI 匹配。"
    MakerHint.Generic ->
        "若系统有「谷歌基础服务」开关请先打开；再授权未知应用，按框架 → Play 服务 → 商店安装，完成后重启再登录。"
}
