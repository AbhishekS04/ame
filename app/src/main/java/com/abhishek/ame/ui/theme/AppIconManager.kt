package com.abhishek.ame.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.abhishek.ame.R

enum class AppIcon(val title: String, val aliasName: String, val previewRes: Int) {
    DEFAULT("Default", "com.abhishek.ame.ui.MainActivityDefault", R.drawable.preview_icon_light),
    DARK("True Dark", "com.abhishek.ame.ui.MainActivityDark", R.drawable.preview_icon_dark),
    ORBIT("Orbit", "com.abhishek.ame.ui.MainActivityOrbit", R.drawable.preview_icon_orbit),
    BLUE_DRIP("Blue Drip", "com.abhishek.ame.ui.MainActivityBlueDrip", R.drawable.preview_icon_bluedrip),
    GALACTIC("Galactic", "com.abhishek.ame.ui.MainActivityGalactic", R.drawable.preview_icon_galactic),
    GOTHIC("Gothic", "com.abhishek.ame.ui.MainActivityGothic", R.drawable.preview_icon_gothic),
    VINYL("Vinyl Orbit", "com.abhishek.ame.ui.MainActivityVinyl", R.drawable.preview_icon_vinyl),
    STARS("Ame Stars", "com.abhishek.ame.ui.MainActivityStars", R.drawable.preview_icon_stars),
}

object AppIconManager {
    fun getActiveIcon(context: Context): AppIcon {
        val pm = context.packageManager
        val pkg = context.packageName

        for (icon in AppIcon.entries) {
            val component = ComponentName(pkg, icon.aliasName)
            val state = runCatching { pm.getComponentEnabledSetting(component) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return icon
            }
        }
        return AppIcon.DEFAULT
    }

    fun setAppIcon(context: Context, target: AppIcon) {
        val pm = context.packageManager
        val pkg = context.packageName

        // Enable target and disable all other aliases
        for (icon in AppIcon.entries) {
            val component = ComponentName(pkg, icon.aliasName)
            val newState = if (icon == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }

        // Restart app cleanly with the newly selected activity alias
        val targetComponent = ComponentName(pkg, target.aliasName)
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            component = targetComponent
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(launchIntent)
        Runtime.getRuntime().exit(0)
    }
}
