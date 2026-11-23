package com.lograre.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun resolveIsDark(isSystemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorageValue(raw: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
    }
}

interface ThemePreferenceStorage {
    fun readThemeMode(): ThemeMode
    fun writeThemeMode(mode: ThemeMode)
}

/**
 * Dedicated, non-sensitive SharedPreferences store for appearance preference only.
 * Read synchronously on startup so neither MainActivity nor LockScreenActivity flickers.
 */
class SharedPrefsThemeStorage(context: Context) : ThemePreferenceStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readThemeMode(): ThemeMode =
        ThemeMode.fromStorageValue(prefs.getString(KEY_THEME_MODE, null))

    override fun writeThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
    }

    companion object {
        private const val PREFS_NAME = "lograre_appearance_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

class ThemePreferenceRepository(
    private val storage: ThemePreferenceStorage
) {
    private val _themeMode = MutableStateFlow(storage.readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun getCurrentModeSync(): ThemeMode = _themeMode.value

    fun setThemeMode(mode: ThemeMode) {
        storage.writeThemeMode(mode)
        _themeMode.value = mode
    }
}
