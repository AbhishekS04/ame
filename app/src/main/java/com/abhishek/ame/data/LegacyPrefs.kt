package com.abhishek.ame.data

import android.content.Context
import android.content.SharedPreferences

/**
 * One-time move of a preferences file renamed away from the fork's old name.
 * Copies every entry into the new file only while it is still empty, so
 * existing users keep their data and fresh installs write nothing.
 */
fun migratePrefs(context: Context, legacyName: String, newName: String) {
    val app = context.applicationContext
    val newPrefs = app.getSharedPreferences(newName, Context.MODE_PRIVATE)
    if (newPrefs.all.isNotEmpty()) return
    val legacy = app.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
    if (legacy.all.isEmpty()) return
    newPrefs.edit().apply {
        legacy.all.forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
            }
        }
    }.apply()
}
