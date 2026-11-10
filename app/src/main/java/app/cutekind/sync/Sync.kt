package app.cutekind.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cutekind.MainActivity
import app.cutekind.R
import app.cutekind.data.Envelope
import app.cutekind.data.Feature
import app.cutekind.data.Repo
import app.cutekind.draw.Renderer
import app.cutekind.widget.PartnerWidget
import java.util.concurrent.TimeUnit

/** App-level glue: runs the engine, then refreshes the widget and posts notifications. */
object Sync {
    private const val CHANNEL = "partner"
    private const val WORK = "background-sync"
    @Volatile private var engine: SyncEngine? = null
    @Volatile var appInForeground = false

    fun engine(context: Context): SyncEngine = engine ?: synchronized(this) {
        engine ?: SyncEngine(Repo.get(context), NtfyRelay()).also { engine = it }
    }

    suspend fun run(context: Context, outgoing: Envelope? = null) {
        val app = context.applicationContext
        val events = if (outgoing != null) engine(app).send(outgoing) else engine(app).sync()
        PartnerWidget.updateAll(app)
        val state = Repo.get(app).state.value
        if (!state.on(Feature.NOTIFICATIONS)) return
        for (e in events) when (e) {
            is SyncEvent.NewDrawing -> notify(app, 1, "${e.drawing.name.ifBlank { "Your partner" }} drew something for you",
                e.drawing.note.ifBlank { "It is on your home screen now" }, e)
            is SyncEvent.Nudge -> if (state.on(Feature.NUDGES)) notify(app, 2, "${e.name} is thinking of you", "💗", null)
            is SyncEvent.Answer -> if (state.on(Feature.DAILY_QUESTION)) notify(app, 3, "${e.name} answered today's question", "Open to see it", null)
            is SyncEvent.Joined -> notify(app, 4, "${e.name} joined you", "You are paired. Draw them something!", null)
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String, drawing: SyncEvent.NewDrawing?) {
        if (appInForeground && drawing == null) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "From your partner", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true)
        drawing?.drawing?.let { d ->
            val bmp = Renderer.bitmap(d.strokes, d.bg, 512)
            b.setLargeIcon(bmp).setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp).bigLargeIcon(null as android.graphics.Bitmap?))
        }
        nm.notify(id, b.build())
    }

    /** Turn the periodic background check on or off to match the Settings switch. */
    fun schedule(context: Context) {
        val state = Repo.get(context).state.value
        val wm = WorkManager.getInstance(context)
        if (state.paired && state.on(Feature.BACKGROUND_SYNC)) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Sync.run(applicationContext)
        return Result.success()
    }
}
