package com.bytemyth.gms_installer.detect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * 国产 ROM 常见「谷歌基础服务 / Google Mobile Services」总开关。
 * 手机已预置 GMS，但设置里未打开时，应引导用户去系统页开启，而不是当成损坏去卸载重装。
 */
data class OemGoogleServicesGate(
    /** 本机能否打开相关系统设置页（或合理兜底页） */
    val supported: Boolean,
    /** 综合判断：支持且当前像是未开启 */
    val likelyOff: Boolean,
    /** 设置页文案提示（路径说明） */
    val hintPath: String,
    val buttonLabel: String,
)

class OemGoogleServicesGateDetector(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    fun detect(
        components: List<ComponentStatus>,
        googleAuth: GmsDetector.GoogleAuthStatus,
        maker: MakerHint,
    ): OemGoogleServicesGate {
        val settings = resolveSettingsIntent(maker)
        val exactPage = resolveExactCandidate() != null
        val supported = settings != null
        val gsf = components.firstOrNull { it.component.id == "gsf" }
        val gms = components.firstOrNull { it.component.id == "play_services" }
        val store = components.firstOrNull { it.component.id == "play_store" }
        val hasFramework = listOfNotNull(gsf, gms).any { it.isPresent }
        val coreDisabled = listOfNotNull(gsf, gms, store).any {
            it.isPresent && it.state == InstallState.Disabled
        }
        val systemFramework = listOfNotNull(gsf, gms).any { it.isPresent && it.isSystem }
        // 未打开：核心包被停用；或已找到厂商开关页且系统框架在但 Google 登录能力未激活
        val likelyOff = supported && hasFramework && (
            coreDisabled ||
                (exactPage && systemFramework && !googleAuth.authenticatorPresent)
            )
        return OemGoogleServicesGate(
            supported = supported,
            likelyOff = likelyOff,
            hintPath = settings?.hintPath
                ?: "设置中搜索「谷歌」或「Google」",
            buttonLabel = settings?.buttonLabel ?: "打开系统设置",
        )
    }

    fun settingsIntent(maker: MakerHint = MakerHint.Generic): Intent? {
        return resolveSettingsIntent(maker)?.intent
    }

    private data class Resolved(
        val intent: Intent,
        val hintPath: String,
        val buttonLabel: String,
    )

    private fun resolveExactCandidate(): Candidate? {
        return CANDIDATES.firstOrNull { activityExists(it.packageName, it.className) }
    }

    private fun resolveSettingsIntent(maker: MakerHint): Resolved? {
        resolveExactCandidate()?.let { candidate ->
            val intent = Intent().setClassName(candidate.packageName, candidate.className).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return Resolved(intent, candidate.hintPath, candidate.buttonLabel)
        }
        // 账号与同步：小米 / vivo 等把「谷歌基础服务」放在这里
        if (maker in CN_MAKERS) {
            val sync = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (sync.resolveActivity(pm) != null) {
                return Resolved(
                    intent = sync,
                    hintPath = when (maker) {
                        MakerHint.Xiaomi -> "设置 → 帐号与同步 → 谷歌基础服务"
                        MakerHint.Oppo -> "设置 → 系统与更新 → Google 设置 / 搜索「谷歌」"
                        MakerHint.Vivo -> "设置 → 账号与同步 → 谷歌基础服务"
                        MakerHint.Huawei, MakerHint.Honor -> "设置中搜索「谷歌」或「GMS」"
                        else -> "设置中搜索「谷歌基础服务」或「Google」"
                    },
                    buttonLabel = "去账号与同步",
                )
            }
            val root = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (root.resolveActivity(pm) != null) {
                return Resolved(
                    intent = root,
                    hintPath = "设置中搜索「谷歌」或「Google」，打开基础服务开关",
                    buttonLabel = "打开系统设置",
                )
            }
        }
        return null
    }

    private fun activityExists(packageName: String, className: String): Boolean {
        return try {
            pm.getActivityInfo(ComponentName(packageName, className), 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private data class Candidate(
        val packageName: String,
        val className: String,
        val hintPath: String,
        val buttonLabel: String,
    )

    companion object {
        private val CN_MAKERS = setOf(
            MakerHint.Xiaomi,
            MakerHint.Oppo,
            MakerHint.Vivo,
            MakerHint.Huawei,
            MakerHint.Honor,
        )

        private val CANDIDATES = listOf(
            // 小米 / 红米 / POCO / HyperOS：手机管家内「谷歌基础服务」
            Candidate(
                packageName = "com.miui.securitycenter",
                className = "com.miui.googlebase.ui.GmsCoreSettings",
                hintPath = "设置 → 帐号与同步 → 谷歌基础服务",
                buttonLabel = "打开谷歌基础服务",
            ),
            Candidate(
                packageName = "com.miui.securitycenter",
                className = "com.miui.googlebase.ui.GmsSettingsActivity",
                hintPath = "设置 → 帐号与同步 → 谷歌基础服务",
                buttonLabel = "打开谷歌基础服务",
            ),
            // 部分 ROM 把 Google 设置挂在系统设置下
            Candidate(
                packageName = "com.android.settings",
                className = "com.android.settings.Settings\$GoogleSettingsActivity",
                hintPath = "设置 → Google 设置",
                buttonLabel = "打开 Google 设置",
            ),
            Candidate(
                packageName = "com.android.settings",
                className = "com.android.settings.Settings\$GoogleAccountSettingsActivity",
                hintPath = "设置 → Google",
                buttonLabel = "打开 Google 设置",
            ),
        )
    }
}
