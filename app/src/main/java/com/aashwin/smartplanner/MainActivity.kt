package com.aashwin.smartplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.aashwin.smartplanner.ui.AppRoot
import com.aashwin.smartplanner.ui.MainViewModel
import com.aashwin.smartplanner.ui.PlannerTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val openTask = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            val theme by vm.prefs.theme.collectAsState()
            PlannerTheme(theme) {
                AppRoot(vm, openTask.value, onOpenHandled = { openTask.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_TASK, 0L) ?: 0L
        if (id > 0) openTask.value = id
    }

    companion object {
        const val EXTRA_OPEN_TASK = "openTask"
    }
}
