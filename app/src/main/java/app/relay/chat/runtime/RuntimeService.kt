package app.relay.chat.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.relay.chat.MainActivity
import app.relay.chat.R
import app.relay.chat.Lang

/**
 * Foreground service held while a CLI runs (setup, sign-in, a subscription reply).
 * Android — Samsung builds especially — cuts network access for background apps, and
 * sign-in always backgrounds Relay behind the browser right when the CLI needs to
 * reach the vendor's token endpoint.
 */
class RuntimeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Working…"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, Lang.wrap(this).getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle("Relay")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "runtime"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_TEXT = "text"
        private var holders = 0

        /** Keeps the service up until a matching [release]; nested holders are counted. */
        @Synchronized
        fun acquire(context: Context, text: String) {
            holders++
            context.startForegroundService(Intent(context, RuntimeService::class.java).putExtra(EXTRA_TEXT, text))
        }

        @Synchronized
        fun release(context: Context) {
            holders = (holders - 1).coerceAtLeast(0)
            if (holders == 0) context.stopService(Intent(context, RuntimeService::class.java))
        }
    }
}
