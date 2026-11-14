package app.cutekind.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.cutekind.MainActivity
import app.cutekind.R
import app.cutekind.data.AppState
import app.cutekind.data.Days
import app.cutekind.data.Feature
import app.cutekind.data.Questions
import app.cutekind.data.Repo
import app.cutekind.draw.Renderer

/** Home-screen widget: the partner's latest drawing, plus today's question and the day counter. */
class PartnerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context, Repo.get(context).state.value, System.currentTimeMillis())
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        const val EMPTY_BG = 0xFFFFF3EC.toInt()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, PartnerWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, Repo.get(context).state.value, System.currentTimeMillis())
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        fun build(context: Context, state: AppState, now: Long): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_partner)
            val latest = state.received.firstOrNull()
            val bg = latest?.bg ?: EMPTY_BG
            val ink = Renderer.inkOn(bg)
            rv.setInt(R.id.widget_bg, "setColorFilter", bg)

            if (latest != null) {
                rv.setImageViewBitmap(R.id.widget_drawing, Renderer.bitmap(latest, 480, 0.09f))
                rv.setViewVisibility(R.id.widget_drawing, View.VISIBLE)
                rv.setViewVisibility(R.id.widget_empty, View.GONE)
            } else {
                rv.setViewVisibility(R.id.widget_drawing, View.GONE)
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rv.setTextColor(R.id.widget_empty, ink)
                rv.setTextViewText(
                    R.id.widget_empty,
                    if (state.paired) "Waiting for ${state.partnerLabel}'s first doodle" else "Open CuteKind to pair with your partner",
                )
            }

            val caption = latest?.takeIf { state.on(Feature.WIDGET_CAPTION) }?.let { d ->
                val who = d.name.ifBlank { state.partnerLabel }
                val note = d.note.takeIf { it.isNotBlank() }
                if (note != null) "$who: $note" else "$who · ${Days.ago(d.ts, now)}"
            }
            rv.text(R.id.widget_caption, caption, ink)

            val days = Days.daysTogether(state.anniversary, now)?.takeIf { state.on(Feature.DAYS_COUNTER) }
            rv.text(R.id.widget_days, days?.let { "♥ day $it" }, ink)

            val question = if (state.paired && state.on(Feature.DAILY_QUESTION) && state.on(Feature.WIDGET_QUESTION))
                Questions.forDay(Days.epochDay(now), state.on(Feature.SPICY)).text else null
            rv.text(R.id.widget_question, question, 0xFF3B2A33.toInt())

            val open = PendingIntent.getActivity(
                context, 7, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_VIEW_LATEST, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            return rv
        }

        private fun RemoteViews.text(id: Int, text: String?, color: Int) {
            setViewVisibility(id, if (text == null) View.GONE else View.VISIBLE)
            if (text != null) { setTextViewText(id, text); setTextColor(id, color) }
        }
    }
}
