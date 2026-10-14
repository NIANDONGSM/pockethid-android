package com.pockethid.remote.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

enum class KeyFeedbackStyle {
    SIMPLE,
    OBVIOUS,
}

data class UiPreferences(
    val showNumpad: Boolean = true,
    val showNavigation: Boolean = true,
    val mobileKeyboardWidthPercent: Int = 88,
    val keyFeedbackStyle: KeyFeedbackStyle = KeyFeedbackStyle.OBVIOUS,
    val hapticsEnabled: Boolean = true,
    val hapticStrength: Int = 55,
)

class UiPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "pocket_hid_ui_preferences",
        Context.MODE_PRIVATE,
    )

    fun load(): UiPreferences = UiPreferences(
        showNumpad = preferences.getBoolean(KEY_SHOW_NUMPAD, true),
        showNavigation = preferences.getBoolean(KEY_SHOW_NAVIGATION, true),
        mobileKeyboardWidthPercent = preferences.getInt(KEY_MOBILE_KEYBOARD_WIDTH, 88).coerceIn(80, 96),
        keyFeedbackStyle = runCatching {
            KeyFeedbackStyle.valueOf(
                preferences.getString(KEY_FEEDBACK_STYLE, KeyFeedbackStyle.OBVIOUS.name)
                    ?: KeyFeedbackStyle.OBVIOUS.name,
            )
        }.getOrDefault(KeyFeedbackStyle.OBVIOUS),
        hapticsEnabled = preferences.getBoolean(KEY_HAPTICS_ENABLED, true),
        hapticStrength = preferences.getInt(KEY_HAPTIC_STRENGTH, 55).coerceIn(1, 100),
    )

    fun save(value: UiPreferences) {
        preferences.edit()
            .putBoolean(KEY_SHOW_NUMPAD, value.showNumpad)
            .putBoolean(KEY_SHOW_NAVIGATION, value.showNavigation)
            .putInt(KEY_MOBILE_KEYBOARD_WIDTH, value.mobileKeyboardWidthPercent.coerceIn(80, 96))
            .putString(KEY_FEEDBACK_STYLE, value.keyFeedbackStyle.name)
            .putBoolean(KEY_HAPTICS_ENABLED, value.hapticsEnabled)
            .putInt(KEY_HAPTIC_STRENGTH, value.hapticStrength.coerceIn(1, 100))
            .apply()
    }

    private companion object {
        const val KEY_SHOW_NUMPAD = "show_numpad"
        const val KEY_SHOW_NAVIGATION = "show_navigation"
        const val KEY_MOBILE_KEYBOARD_WIDTH = "mobile_keyboard_width"
        const val KEY_FEEDBACK_STYLE = "feedback_style"
        const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
    }
}

class KeyFeedbackPlayer(context: Context) {
    private val applicationContext = context.applicationContext
    private val vibrator: Vibrator? = context.applicationContext
        .getSystemService(VibratorManager::class.java)
        ?.defaultVibrator

    fun perform(settings: UiPreferences, view: View): Boolean {
        if (!settings.hapticsEnabled) return false

        // Android's recommended path for virtual keyboards. OriginOS maps this action
        // to the device-tuned X-axis linear motor effect.
        view.isHapticFeedbackEnabled = true
        if (performVirtualKeyFeedback(view)) return true

        val activeVibrator = vibrator
        if (activeVibrator?.hasVibrator() != true) return false

        val strength = settings.hapticStrength.coerceIn(1, 100)
        return try {
            val supportsOptimizedClick = activeVibrator.areEffectsSupported(
                VibrationEffect.EFFECT_CLICK,
            ).firstOrNull() == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
            val effect = if (supportsOptimizedClick) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                // Last-resort compatibility path. The app strength slider applies here.
                val duration = (24 + strength * 0.46f).toLong().coerceIn(24L, 70L)
                val amplitude = if (activeVibrator.hasAmplitudeControl()) {
                    (64 + strength * 1.91f).toInt().coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                VibrationEffect.createOneShot(duration, amplitude)
            }
            activeVibrator.vibrate(effect, touchAudioAttributes)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isSystemTouchFeedbackEnabled(): Boolean = Settings.System.getInt(
        applicationContext.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        0,
    ) != 0

    @Suppress("DEPRECATION")
    private fun performVirtualKeyFeedback(view: View): Boolean = view.performHapticFeedback(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
    )

    private companion object {
        val touchAudioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build()
    }
}
