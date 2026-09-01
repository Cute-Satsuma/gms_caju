package com.bytemyth.gms_installer.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

data class SessionInstallEvent(
    val sessionId: Int,
    val status: Int,
    val message: String?,
    val confirmIntent: Intent?,
)

object InstallWaiters {
    private val waiters = ConcurrentHashMap<Int, Channel<SessionInstallEvent>>()

    fun open(sessionId: Int): Channel<SessionInstallEvent> {
        val channel = Channel<SessionInstallEvent>(Channel.BUFFERED)
        waiters[sessionId] = channel
        return channel
    }

    fun close(sessionId: Int) {
        waiters.remove(sessionId)?.close()
    }

    fun emit(event: SessionInstallEvent) {
        waiters[event.sessionId]?.trySend(event)
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirm = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

        if (intent.action == ACTION_UNINSTALL_RESULT) {
            val token = intent.getIntExtra(EXTRA_UNINSTALL_TOKEN, -1)
            if (token >= 0) {
                UninstallWaiters.emit(
                    token,
                    SessionInstallEvent(sessionId = token, status = status, message = message, confirmIntent = confirm),
                )
            }
            return
        }

        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        InstallWaiters.emit(SessionInstallEvent(sessionId, status, message, confirm))
    }

    companion object {
        const val ACTION_UNINSTALL_RESULT = "com.bytemyth.gms_installer.UNINSTALL_RESULT"
        const val EXTRA_UNINSTALL_TOKEN = "uninstall_token"
        const val EXTRA_UNINSTALL_PACKAGE = "uninstall_package"
    }
}
