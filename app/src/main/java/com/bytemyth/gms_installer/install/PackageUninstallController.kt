package com.bytemyth.gms_installer.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.VersionedPackage
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed class UninstallOutcome {
    data object Success : UninstallOutcome()
    data object Aborted : UninstallOutcome()
    data class Failed(val reason: String) : UninstallOutcome()
    data object NotInstalled : UninstallOutcome()
}

object UninstallWaiters {
    private val seq = AtomicInteger(1)
    private val waiters = ConcurrentHashMap<Int, Channel<SessionInstallEvent>>()

    fun open(): Pair<Int, Channel<SessionInstallEvent>> {
        val id = seq.getAndIncrement()
        val channel = Channel<SessionInstallEvent>(Channel.BUFFERED)
        waiters[id] = channel
        return id to channel
    }

    fun close(id: Int) {
        waiters.remove(id)?.close()
    }

    fun emit(id: Int, event: SessionInstallEvent) {
        waiters[id]?.trySend(event)
    }
}

class PackageUninstallController(private val context: Context) {

    suspend fun uninstall(
        packageName: String,
        startConfirm: suspend (Intent) -> Unit,
    ): UninstallOutcome {
        val installed = try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
        if (!installed) return UninstallOutcome.NotInstalled

        val (token, waiter) = UninstallWaiters.open()
        val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
            setPackage(context.packageName)
            action = InstallResultReceiver.ACTION_UNINSTALL_RESULT
            putExtra(InstallResultReceiver.EXTRA_UNINSTALL_TOKEN, token)
            putExtra(InstallResultReceiver.EXTRA_UNINSTALL_PACKAGE, packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, token, resultIntent, flags)
        return try {
            val installer = context.packageManager.packageInstaller
            if (Build.VERSION.SDK_INT >= 33) {
                installer.uninstall(
                    VersionedPackage(packageName, PackageManager.VERSION_CODE_HIGHEST),
                    pending.intentSender,
                )
            } else {
                @Suppress("DEPRECATION")
                installer.uninstall(packageName, pending.intentSender)
            }
            val first = withTimeout(180_000) { waiter.receive() }
            when (first.status) {
                PackageInstaller.STATUS_SUCCESS -> UninstallOutcome.Success
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = first.confirmIntent
                        ?: return UninstallOutcome.Failed("系统没有给出卸载确认界面")
                    startConfirm(confirm)
                    val second = withTimeout(180_000) { waiter.receive() }
                    mapStatus(second)
                }
                else -> mapStatus(first)
            }
        } catch (t: Throwable) {
            UninstallOutcome.Failed(t.message ?: "卸载失败")
        } finally {
            UninstallWaiters.close(token)
        }
    }

    private fun mapStatus(event: SessionInstallEvent): UninstallOutcome {
        return when (event.status) {
            PackageInstaller.STATUS_SUCCESS -> UninstallOutcome.Success
            PackageInstaller.STATUS_FAILURE_ABORTED -> UninstallOutcome.Aborted
            else -> UninstallOutcome.Failed(event.message ?: "卸载失败（${event.status}）")
        }
    }
}
