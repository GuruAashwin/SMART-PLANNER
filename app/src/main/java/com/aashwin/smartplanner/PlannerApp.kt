package com.aashwin.smartplanner

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.aashwin.smartplanner.alarm.Notifier
import com.aashwin.smartplanner.data.PlannerDatabase
import com.aashwin.smartplanner.data.Prefs
import com.aashwin.smartplanner.data.Repository
import com.aashwin.smartplanner.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlannerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        repository = Repository(this, PlannerDatabase.create(this), prefs)
        Notifier.createChannels(this)
        appScope.launch {
            repository.rollover()
            repository.rescheduleAll()
            repository.finishFocus() // a session that ended while the phone was off
        }
        widgetRefresher()
    }

    /** Keeps the home-screen widget in step with the data: any change refreshes it (batched). */
    private fun widgetRefresher() {
        appScope.launch {
            repository.tasks.collectLatest {
                delay(400)
                try { TodayWidget().updateAll(this@PlannerApp) } catch (_: Exception) { }
            }
        }
    }
}
