package com.bytemyth.gms_installer.install

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DirectApkFetcher {

    fun fetch(
        urls: List<String>,
        destDir: File,
        filePrefix: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): File {
        destDir.mkdirs()
        var lastError: Exception? = null
        val tried = linkedSetOf<String>()
        for (raw in urls.filter { it.isNotBlank() }) {
            for (url in expand(raw)) {
                if (!tried.add(url)) continue
                val dest = File(destDir, "$filePrefix.bin")
                try {
                    download(url, dest, onProgress)
                    validateArchive(dest)
                    return dest
                } catch (t: Exception) {
                    dest.delete()
                    lastError = t
                }
            }
        }
        throw lastError ?: IllegalStateException("没有可用的下载地址")
    }

    private fun expand(url: String): List<String> {
        val net = rewriteHost(url)
        return listOf(net, url).filter { it.isNotBlank() }.distinct()
    }

    private fun download(startUrl: String, dest: File, onProgress: (Long, Long) -> Unit) {
        var current = rewriteHost(startUrl)
        repeat(10) {
            val conn = open(current)
            try {
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    throw IllegalStateException("下载失败 HTTP $code")
                }
                val ctype = conn.contentType.orEmpty()
                if (ctype.contains("text/html", ignoreCase = true)) {
                    throw IllegalStateException("直链被拦截（返回了网页）")
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            readTotal += n
                            onProgress(readTotal, total)
                        }
                    }
                }
                return
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("下载重定向过多")
    }

    private fun open(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 120_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Referer", REFERER)
        }
    }

    private fun validateArchive(file: File) {
        if (!file.exists() || file.length() < 64) {
            throw IllegalStateException("文件太小，不像安装包")
        }
        val head = ByteArray(16)
        val n = file.inputStream().use { it.read(head) }
        val text = head.decodeToString(0, n.coerceAtLeast(0))
        if (text.startsWith("<") || text.startsWith("<!") || text.contains("Just a moment", ignoreCase = true)) {
            throw IllegalStateException("直链被拦截（返回了网页）。请改用 APKPure 页下载后导入。")
        }
        if (n < 4 || head[0] != 0x50.toByte() || head[1] != 0x4B.toByte()) {
            throw IllegalStateException("下到的不是 APK/ZIP（文件头不对）")
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private const val REFERER = "https://apkpure.net/"

        fun rewriteHost(url: String): String {
            return url
                .replace("://d.apkpure.com/", "://d.apkpure.net/")
                .replace("://www.apkpure.com/", "://apkpure.net/")
                .replace("://apkpure.com/", "://apkpure.net/")
        }
    }
}
