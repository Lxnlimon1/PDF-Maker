package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PageSizeOption
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pdf_master_prefs")

class UserPreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val pageSizeKey = stringPreferencesKey("default_page_size")
    private val highResScanKey = booleanPreferencesKey("high_res_scan")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[themeKey] ?: ThemeMode.SYSTEM.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    val defaultPageSize: Flow<PageSizeOption> = context.dataStore.data.map { prefs ->
        val raw = prefs[pageSizeKey] ?: PageSizeOption.A4.name
        runCatching { PageSizeOption.valueOf(raw) }.getOrDefault(PageSizeOption.A4)
    }

    val highResScan: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[highResScanKey] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDefaultPageSize(size: PageSizeOption) {
        context.dataStore.edit { it[pageSizeKey] = size.name }
    }

    suspend fun setHighResScan(enabled: Boolean) {
        context.dataStore.edit { it[highResScanKey] = enabled }
    }
}
