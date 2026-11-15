package com.aashwin.smartplanner

import android.app.Application
import com.aashwin.smartplanner.alarm.Notifier
import com.aashwin.smartplanner.data.PlannerDatabase
import com.aashwin.smartplanner.data.Prefs
import com.aashwin.smartplanner.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        }
    }
}
