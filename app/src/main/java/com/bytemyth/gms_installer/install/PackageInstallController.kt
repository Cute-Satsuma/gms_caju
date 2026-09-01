package com.bytemyth.gms_installer.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.withTimeout
import java.io.File

sealed class InstallOutcome {
    data object Success : InstallOutcome()
    data object Aborted : InstallOutcome()
    data class Failed(val reason: String) : InstallOutcome()
}

class PackageInstallController(private val context: Context) {

    suspend fun install(
        matched: MatchedPackage,
        startConfirm: suspend (Intent) -> Unit,
        onProgress: (phase: InstallWritePhase, fraction: Float, detail: String) -> Unit = { _, _, _ -> },
    ): InstallOutcome {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 34) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        params.setAppPackageName(matched.packageName)
        val sessionId = installer.createSession(params)
        val waiter = InstallWaiters.open(sessionId)
        val session = installer.openSession(sessionId)
        try {
            val totalBytes = matched.payloads.sumOf { it.file.length() }.coerceAtLeast(1L)
            var writtenAll = 0L
            onProgress(InstallWritePhase.Writing, 0f, "写入安装包…")
            matched.payloads.forEachIndexed { index, payload ->
                writeApk(session, payload.file, "split_$index.apk") { chunk ->
                    writtenAll += chunk
                    val fraction = (writtenAll.toFloat() / totalBytes).coerceIn(0f, 0.92f)
                    onProgress(
                        InstallWritePhase.Writing,
                        fraction,
                        "写入 ${index + 1}/${matched.payloads.size}",
                    )
                }
            }
            val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
                setPackage(context.packageName)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, resultIntent, flags)
            session.commit(pending.intentSender)
            session.close()
            onProgress(InstallWritePhase.WaitingConfirm, 0.93f, "等待系统确认…")
        } catch (t: Throwable) {
            runCatching { session.abandon() }
            InstallWaiters.close(sessionId)
            return InstallOutcome.Failed(t.message ?: t.javaClass.simpleName)
        }

        return try {
            val first = withTimeout(180_000) { waiter.receive() }
            when (first.status) {
                PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = first.confirmIntent
                        ?: return InstallOutcome.Failed("系统没有给出确认安装界面")
                    onProgress(InstallWritePhase.WaitingConfirm, 0.95f, "请在系统弹窗点安装")
                    startConfirm(confirm)
                    val second = withTimeout(180_000) { waiter.receive() }
                    mapStatus(second)
                }
                else -> mapStatus(first)
            }
        } catch (t: Throwable) {
            InstallOutcome.Failed(t.message ?: "等待安装结果超时")
        } finally {
            InstallWaiters.close(sessionId)
        }
    }

    enum class InstallWritePhase { Writing, WaitingConfirm }

    private fun mapStatus(event: SessionInstallEvent): InstallOutcome {
        return when (event.status) {
            PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Aborted
            else -> InstallOutcome.Failed(event.message ?: "安装失败（${event.status}）")
        }
    }

    private fun writeApk(
        session: PackageInstaller.Session,
        file: File,
        name: String,
        onChunk: (Long) -> Unit,
    ) {
        file.inputStream().use { input ->
            session.openWrite(name, 0, file.length()).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    onChunk(n.toLong())
                }
                session.fsync(out)
            }
        }
    }
}
