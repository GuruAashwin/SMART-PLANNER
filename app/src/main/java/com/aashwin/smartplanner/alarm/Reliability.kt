package com.aashwin.smartplanner.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Many Android brands stop apps from starting in the background (after a restart, or after
 * being swiped away), which silently cancels alarms. This knows where each brand hides the
 * switch and what it is called.
 */
object Reliability {
    data class Brand(val name: String, val autostartLabel: String, val steps: String)

    private val maker = Build.MANUFACTURER.lowercase()

    val brand: Brand? = when {
        maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> Brand(
            "Xiaomi", "Autostart",
            "Turn on Autostart. Then in Battery saver choose No restrictions. In Recents, pull the app's card down to lock it.",
        )
        maker.contains("realme") -> Brand(
            "Realme", "Auto launch",
            "Allow Auto launch. Then in Battery usage allow background activity. In Recents, lock the app's card.",
        )
        maker.contains("oppo") -> Brand(
            "Oppo", "Auto launch",
            "Allow Auto launch. Then in Battery usage allow background activity. In Recents, lock the app's card.",
        )
        maker.contains("vivo") || maker.contains("iqoo") -> Brand(
            "Vivo", "Background start-up",
            "Allow Auto-start / Background start-up. Then allow High background power use. In Recents, lock the app's card.",
        )
        maker.contains("oneplus") -> Brand(
            "OnePlus", "Auto launch",
            "Allow Auto launch. Then set Battery to Don't optimise.",
        )
        maker.contains("samsung") -> Brand(
            "Samsung", "Never sleeping apps",
            "Set Battery to Unrestricted. Make sure the app is not in Sleeping apps.",
        )
        maker.contains("huawei") || maker.contains("honor") -> Brand(
            "Huawei", "App launch",
            "In App launch, switch Smart Planner to Manage manually and allow all three options.",
        )
        maker.contains("asus") -> Brand("Asus", "Auto-start", "Allow Auto-start in Mobile Manager.")
        else -> null
    }

    private val autostartScreens = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"),
    )

    /** Opens the brand's auto-start screen; falls back to the app's own settings page. */
    fun openAutostart(ctx: Context) {
        if (brand != null && brand.name != "Samsung") {
            for (c in autostartScreens) {
                try {
                    ctx.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: Exception) { }
            }
        }
        openAppDetails(ctx)
    }

    fun openAppDetails(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }
}
