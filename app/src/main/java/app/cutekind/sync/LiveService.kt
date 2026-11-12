package app.cutekind.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.cutekind.MainActivity
import app.cutekind.R
import app.cutekind.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Instant delivery. Holds one live connection to the relay so a partner's drawing reaches the
 * widget within seconds, even with the app closed. Android only lets a connection stay open in
 * the background behind a visible notification, so this runs as a foreground service.
 */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Instant delivery", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps CuteKind connected so drawings arrive right away. Safe to hide."
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Connected to your partner")
            .setContentText("Drawings arrive on your home screen right away")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this, 100, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )

        val repo = Repo.get(this)
        scope.launch {
            // reconnect from scratch whenever the couple code or relay server changes
            repo.state.map { it.code to it.relay }.distinctUntilChanged().collectLatest { (code, _) ->
                if (code == null) stopSelf()
                else Sync.engine(this@LiveService).listen { events -> Sync.deliver(applicationContext, events) }
            }
        }
        // catch up on anything sent while the phone was off, and push anything still queued
        scope.launch { Sync.run(applicationContext) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "live"

        /** Start or stop the live connection. Android may refuse a start from the background; that is fine. */
        fun sync(context: Context, wanted: Boolean) {
            val intent = Intent(context, LiveService::class.java)
            if (wanted) runCatching { ContextCompat.startForegroundService(context, intent) }
            else context.stopService(intent)
        }

        /** True when the phone's battery saver is not allowed to cut this app off while the screen is off. */
        fun unrestricted(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
}

/** Brings the live connection back after a restart or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Sync.schedule(context)
}
