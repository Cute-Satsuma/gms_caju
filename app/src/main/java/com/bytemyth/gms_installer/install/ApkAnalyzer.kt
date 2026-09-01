package com.bytemyth.gms_installer.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.bytemyth.gms_installer.catalog.GmsCatalog
import com.bytemyth.gms_installer.catalog.GmsComponent
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ApkPayload(
    val file: File,
    val displayName: String,
)

data class MatchedPackage(
    val packageName: String,
    val component: GmsComponent?,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val payloads: List<ApkPayload>,
    val apkAbis: Set<String>,
    val abiOk: Boolean,
    val warning: String?,
)

class ApkAnalyzer(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val deviceAbis = Build.SUPPORTED_ABIS.toSet()

    fun analyze(uris: List<Uri>): List<MatchedPackage> {
        val cacheRoot = File(context.cacheDir, "import").apply {
            deleteRecursively()
            mkdirs()
        }
        val payloads = mutableListOf<ApkPayload>()
        uris.forEachIndexed { index, uri ->
            val name = queryName(uri) ?: "file_$index"
            when {
                name.endsWith(".zip", ignoreCase = true) ->
                    payloads += unzip(uri, File(cacheRoot, "zip_$index"))
                name.endsWith(".apk", ignoreCase = true) ->
                    payloads += copyApk(uri, File(cacheRoot, "apk_$index.apk"), name)
                else -> {
                    // 无扩展名时仍按 APK 试一次
                    payloads += copyApk(uri, File(cacheRoot, "apk_$index.apk"), name)
                }
            }
        }

        return match(payloads)
    }

    fun analyzeFiles(files: List<File>): List<MatchedPackage> {
        val payloads = files.flatMap { expand(it) }
        return match(payloads)
    }

    fun expand(file: File): List<ApkPayload> {
        if (readPackage(file) != null) {
            return listOf(ApkPayload(file, file.name))
        }
        return unzipFile(file, File(file.parentFile, "${file.nameWithoutExtension}_splits"))
    }

    private fun match(payloads: List<ApkPayload>): List<MatchedPackage> {
        val grouped = linkedMapOf<String, MutableList<Pair<ApkPayload, PackageInfo>>>()
        for (payload in payloads) {
            val info = readPackage(payload.file) ?: continue
            val packageName = info.packageName ?: continue
            grouped.getOrPut(packageName) { mutableListOf() }.add(payload to info)
        }
        return grouped.entries.map { (packageName, items) ->
            val primary = items.maxBy { versionCodeOf(it.second) }
            val abis = items.flatMap { nativeAbis(it.first.file) }.toSet()
            val abiOk = abis.isEmpty() || abis.any { it in deviceAbis }
            val component = GmsCatalog.find(packageName)
            val warning = buildString {
                if (!abiOk) {
                    append("这份安装包的 CPU 架构是 ${abis.joinToString()}，本机是 ${deviceAbis.joinToString()}，可能装不上。")
                }
            }.ifBlank { null }
            MatchedPackage(
                packageName = packageName,
                component = component,
                label = component?.title ?: (primary.second.applicationInfo?.loadLabel(pm)?.toString() ?: packageName),
                versionName = primary.second.versionName,
                versionCode = versionCodeOf(primary.second),
                payloads = items.map { it.first },
                apkAbis = abis,
                abiOk = abiOk,
                warning = warning,
            )
        }.sortedWith(
            compareBy<MatchedPackage> { it.component?.order ?: Int.MAX_VALUE }
                .thenBy { it.label },
        )
    }

    private fun copyApk(uri: Uri, dest: File, displayName: String): List<ApkPayload> {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: return emptyList()
        return listOf(ApkPayload(dest, displayName))
    }

    private fun unzip(uri: Uri, destDir: File): List<ApkPayload> {
        destDir.mkdirs()
        val out = mutableListOf<ApkPayload>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                var i = 0
                while (entry != null) {
                    val entryName = entry.name
                    if (!entry.isDirectory && entryName.endsWith(".apk", ignoreCase = true)) {
                        val safe = File(destDir, "e_${i}_${File(entryName).name}").normalize()
                        if (safe.startsWith(destDir)) {
                            safe.outputStream().use { zip.copyTo(it) }
                            out += ApkPayload(safe, File(entryName).name)
                            i++
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return out
    }

    private fun unzipFile(zipFile: File, destDir: File): List<ApkPayload> {
        destDir.mkdirs()
        val out = mutableListOf<ApkPayload>()
        runCatching {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                var i = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    if (!entry.isDirectory && entryName.endsWith(".apk", ignoreCase = true)) {
                        val safe = File(destDir, "e_${i}_${File(entryName).name}").normalize()
                        if (safe.path.startsWith(destDir.path)) {
                            zip.getInputStream(entry).use { input ->
                                safe.outputStream().use { input.copyTo(it) }
                            }
                            out += ApkPayload(safe, File(entryName).name)
                            i++
                        }
                    }
                }
            }
        }
        return out
    }

    private fun readPackage(file: File): PackageInfo? {
        val flags = PackageManager.GET_META_DATA
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        }
        info?.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }
        return info
    }

    private fun versionCodeOf(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun nativeAbis(file: File): Set<String> {
        val found = mutableSetOf<String>()
        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        val abi = name.removePrefix("lib/").substringBefore('/')
                        if (abi.isNotBlank()) found += abi
                    }
                }
            }
        }
        return found
    }

    private fun queryName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
