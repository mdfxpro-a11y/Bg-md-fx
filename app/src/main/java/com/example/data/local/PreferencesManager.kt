package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ApiKeySettings
import com.example.data.model.OutputResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bg_remover_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ApiKeySettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): ApiKeySettings {
        val key = prefs.getString(KEY_API_KEY, "") ?: ""
        val model = prefs.getString(KEY_MODEL, "gemini-2.5-flash-image") ?: "gemini-2.5-flash-image"
        val qualityStr = prefs.getString(KEY_QUALITY, OutputResolution.HD_1080P.name) ?: OutputResolution.HD_1080P.name
        val quality = try {
            OutputResolution.valueOf(qualityStr)
        } catch (e: Exception) {
            OutputResolution.HD_1080P
        }
        val autoFeather = prefs.getBoolean(KEY_FEATHER, true)
        val lang = prefs.getString(KEY_LANG, "hi") ?: "hi"

        return ApiKeySettings(
            customApiKey = key,
            activeModel = model,
            hdQuality = quality,
            autoFeathering = autoFeather,
            preferredLanguage = lang
        )
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
        _settingsFlow.value = _settingsFlow.value.copy(customApiKey = apiKey.trim())
    }

    fun saveModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
        _settingsFlow.value = _settingsFlow.value.copy(activeModel = model)
    }

    fun saveQuality(quality: OutputResolution) {
        prefs.edit().putString(KEY_QUALITY, quality.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(hdQuality = quality)
    }

    fun saveAutoFeather(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEATHER, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(autoFeathering = enabled)
    }

    fun saveLanguage(lang: String) {
        prefs.edit().putString(KEY_LANG, lang).apply()
        _settingsFlow.value = _settingsFlow.value.copy(preferredLanguage = lang)
    }

    companion object {
        private const val KEY_API_KEY = "gemini_custom_api_key"
        private const val KEY_MODEL = "gemini_active_model"
        private const val KEY_QUALITY = "output_hd_quality"
        private const val KEY_FEATHER = "auto_feather_enabled"
        private const val KEY_LANG = "user_language_pref"
    }
}
