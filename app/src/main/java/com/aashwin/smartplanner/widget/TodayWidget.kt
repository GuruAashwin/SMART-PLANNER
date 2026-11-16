package com.aashwin.smartplanner.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aashwin.smartplanner.MainActivity
import com.aashwin.smartplanner.PlannerApp
import com.aashwin.smartplanner.data.Status
import com.aashwin.smartplanner.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One line in the widget. */
data class WidgetItem(val id: Long, val title: String, val sub: String, val overdue: Boolean)
data class WidgetData(val items: List<WidgetItem>, val done: Int, val total: Int)

private val taskKey = ActionParameters.Key<Long>("taskId")

// Black + gold at night, white + gold by day, like the app.
private val gold = ColorProvider(day = Color(0xFFA07C1C), night = Color(0xFFD4AF37))
private val bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF000000))
private val ink = ColorProvider(day = Color(0xFF111111), night = Color(0xFFF2F2F2))
private val muted = ColorProvider(day = Color(0xFF66625A), night = Color(0xFF9E9E9E))
private val red = ColorProvider(day = Color(0xFFC62828), night = Color(0xFFFF6B6B))
private val onGold = ColorProvider(day = Color(0xFF000000), night = Color(0xFF000000))

/** Overdue first, then today's tasks by time. */
suspend fun loadWidgetData(context: Context): WidgetData {
    val repo = (context.applicationContext as PlannerApp).repository
    val all = repo.allTasks()
    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val zone = ZoneId.systemDefault()
    val todays = all.filter {
        (it.task.status == Status.PENDING && it.task.dueDate == todayDay) ||
            (it.task.status == Status.DONE && it.task.completedAt?.let { c -> Instant.ofEpochMilli(c).atZone(zone).toLocalDate() == today } == true)
    }
    val overdue = all.filter { it.task.isOverdue(now) && it.task.dueDate != todayDay }
    val open = (overdue + todays.filter { it.task.status == Status.PENDING })
        .sortedWith(compareBy({ !it.task.isOverdue(now) }, { it.task.dueMillis() ?: Long.MAX_VALUE }))
    return WidgetData(
        items = open.take(12).map { f ->
            val t = f.task
            WidgetItem(
                id = t.id, title = t.title,
                sub = when {
                    t.isOverdue(now) -> "Overdue · " + Fmt.due(t.date, t.dueMinutes)
                    t.dueMinutes != null -> Fmt.time(t.dueMinutes)
                    else -> "Today"
                } + if (f.subtasks.isNotEmpty()) " · ${f.subtasks.count { it.done }}/${f.subtasks.size}" else "",
                overdue = t.isOverdue(now),
            )
        },
        done = todays.count { it.task.status == Status.DONE },
        total = todays.size,
    )
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { WidgetContent(context, data) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Tapping the circle completes the task without opening the app. */
class CompleteFromWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[taskKey] ?: return
        (context.applicationContext as PlannerApp).repository.completeTask(id, completeSubtasks = true)
        TodayWidget().updateAll(context)
    }
}

/** Each intent gets its own data URI so Android keeps them apart (otherwise every row opens the same task). */
private fun openTaskIntent(context: Context, id: Long) = Intent(context, MainActivity::class.java)
    .setData(Uri.parse("smartplanner://task/$id"))
    .putExtra(MainActivity.EXTRA_OPEN_TASK, id)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

private fun quickAddIntent(context: Context) = Intent(context, MainActivity::class.java)
    .setData(Uri.parse("smartplanner://quickadd"))
    .putExtra(MainActivity.EXTRA_QUICK_ADD, true)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

private fun homeIntent(context: Context) = Intent(context, MainActivity::class.java)
    .setData(Uri.parse("smartplanner://home"))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun WidgetContent(context: Context, d: WidgetData) {
    Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(homeIntent(context)))) {
                Text("Today", style = TextStyle(color = gold, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(
                    if (d.total == 0) "Nothing due" else "${d.done} of ${d.total} done",
                    style = TextStyle(color = muted, fontSize = 12.sp),
                )
            }
            Box(
                GlanceModifier.size(36.dp).cornerRadius(18.dp).background(gold)
                    .clickable(actionStartActivity(quickAddIntent(context))),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = onGold, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(GlanceModifier.size(8.dp))
        if (d.items.isEmpty()) {
            Text("All clear. Tap + to add a task.", style = TextStyle(color = muted, fontSize = 13.sp))
        } else {
            LazyColumn {
                items(d.items, itemId = { it.id }) { item ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "○",
                            style = TextStyle(color = if (item.overdue) red else gold, fontSize = 20.sp),
                            modifier = GlanceModifier.clickable(
                                actionRunCallback<CompleteFromWidget>(actionParametersOf(taskKey to item.id))
                            ),
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openTaskIntent(context, item.id)))) {
                            Text(item.title, style = TextStyle(color = ink, fontSize = 14.sp), maxLines = 1)
                            Text(item.sub, style = TextStyle(color = if (item.overdue) red else muted, fontSize = 11.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
