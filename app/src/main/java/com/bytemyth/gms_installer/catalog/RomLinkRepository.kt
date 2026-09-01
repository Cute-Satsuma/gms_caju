package com.bytemyth.gms_installer.catalog

import android.content.Context
import android.os.Build
import org.json.JSONObject

data class RomOption(
    val android: String,
    val api: Int,
    val codename: String,
)

data class RomDownloadLink(
    val componentId: String,
    val title: String,
    val packageName: String,
    val url: String,
    val apkPageUrl: String,
    val apkpureUrl: String,
    val apkpureDownloadUrl: String,
    val directUrl: String,
    val directXapkUrl: String,
) {
    val hasDirect: Boolean get() = directUrl.isNotBlank() || directXapkUrl.isNotBlank()
}

data class ResolvedRomLinks(
    val option: RomOption,
    val abiTrack: String,
    val abiTrackLabel: String,
    val exactMatch: Boolean,
    val warning: String?,
    val links: List<RomDownloadLink>,
)

class RomLinkRepository(context: Context) {

    private val appContext = context.applicationContext
    private val root: JSONObject by lazy { loadRoot() }

    val options: List<RomOption> by lazy {
        val array = root.getJSONArray("roms")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            RomOption(
                android = o.getString("android"),
                api = o.getInt("api"),
                codename = o.optString("codename"),
            )
        }
    }

    fun resolve(sdk: Int, abis: List<String>, overrideApi: Int? = null): ResolvedRomLinks? {
        if (options.isEmpty()) return null
        val track = abiTrack(abis)
        val chosen = if (overrideApi != null) {
            options.firstOrNull { it.api == overrideApi } ?: matchSdk(overrideApi)
        } else {
            matchSdk(sdk)
        } ?: options.last()

        val rom = romObject(chosen.api) ?: return null
        val exact = chosen.api == sdk && overrideApi == null
        val warning = buildString {
            if (overrideApi != null && overrideApi != sdk) {
                append("已手动选择 Android ${chosen.android}，本机是 API $sdk。")
            } else if (!exact) {
                append("目录里没有 API $sdk 的条目，已就近使用 Android ${chosen.android}（API ${chosen.api}）。GSF 必须对大版本，装前请再核对。")
            }
        }.ifBlank { null }

        val links = listOf(
            link("gsf", "服务框架", rom, track),
            link("account_manager", "账号管理", rom, track),
            link("play_services", "Play 服务", rom, track),
            link("play_store", "Play 商店", rom, track),
        )
        return ResolvedRomLinks(
            option = chosen,
            abiTrack = track,
            abiTrackLabel = if (track == TRACK_X86) "x86 / x86_64（模拟器）" else "arm64（真机）",
            exactMatch = exact,
            warning = warning,
            links = links,
        )
    }

    private fun link(
        jsonKey: String,
        title: String,
        rom: JSONObject,
        track: String,
    ): RomDownloadLink {
        val packages = root.getJSONObject("packages")
        val catalogs = root.getJSONObject("catalogs")
        val apkpureNet = root.optJSONObject("catalogs_apkpure_net")
        val apkpureDl = root.optJSONObject("catalogs_apkpure_download")
        val defaults = root.optJSONObject("direct_defaults")?.optJSONObject(jsonKey)
        val node = rom.optJSONObject(jsonKey) ?: JSONObject()

        val listing = firstNonBlank(
            node.optString(track),
            node.optString("url"),
            catalogs.optString(jsonKey),
        )
        val apkPage = firstNonBlank(
            node.optString("apk_page"),
            listing,
        )
        val apkpure = reachable(
            firstNonBlank(
                node.optString("apkpure_net_url"),
                node.optString("apkpure_url"),
                apkpureNet?.optString(jsonKey).orEmpty(),
            ),
        )
        val apkpureDownload = reachable(
            firstNonBlank(
                node.optString("apkpure_download"),
                apkpureDl?.optString(jsonKey).orEmpty(),
                if (apkpure.isNotBlank()) "$apkpure/download" else "",
            ),
        )
        val directNode = node.optJSONObject("direct")
        val direct = reachable(
            firstNonBlank(
                directNode?.optString(track).orEmpty(),
                directNode?.optString("all").orEmpty(),
                directNode?.optString("apk").orEmpty(),
                defaults?.optString(track).orEmpty(),
                defaults?.optString("all").orEmpty(),
                defaults?.optString("apk").orEmpty(),
            ),
        )
        val xapk = reachable(
            firstNonBlank(
                directNode?.optString("xapk").orEmpty(),
                defaults?.optString("xapk").orEmpty(),
            ),
        )
        return RomDownloadLink(
            componentId = jsonKey,
            title = title,
            packageName = packages.optString(jsonKey),
            url = listing,
            apkPageUrl = apkPage,
            apkpureUrl = apkpure,
            apkpureDownloadUrl = apkpureDownload,
            directUrl = direct,
            directXapkUrl = xapk,
        )
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun reachable(url: String): String {
        return url
            .replace("://d.apkpure.com/", "://d.apkpure.net/")
            .replace("://www.apkpure.com/", "://apkpure.net/")
            .replace("://apkpure.com/", "://apkpure.net/")
    }

    private fun matchSdk(sdk: Int): RomOption? {
        options.firstOrNull { it.api == sdk }?.let { return it }
        return options.filter { it.api <= sdk }.maxByOrNull { it.api }
            ?: options.minByOrNull { it.api }
    }

    private fun romObject(api: Int): JSONObject? {
        val array = root.getJSONArray("roms")
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (o.getInt("api") == api) return o
        }
        return null
    }

    private fun loadRoot(): JSONObject {
        val text = appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JSONObject(text)
    }

    companion object {
        const val ASSET_NAME = "gms-rom-links.json"
        const val TRACK_ARM = "arm64"
        const val TRACK_X86 = "avd_x86_64"

        fun abiTrack(abis: List<String>): String {
            val primary = abis.firstOrNull().orEmpty()
            return if (primary.startsWith("x86")) TRACK_X86 else TRACK_ARM
        }

        fun deviceAbis(): List<String> = Build.SUPPORTED_ABIS.toList()
    }
}
