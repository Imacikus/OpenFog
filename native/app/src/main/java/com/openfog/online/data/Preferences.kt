package com.openfog.online.data

import android.content.Context

/** Thin SharedPreferences wrapper for persistent user preferences. */
class Preferences(context: Context) {

    private val prefs =
        context.getSharedPreferences("openfog_prefs", Context.MODE_PRIVATE)

    var onboardingShown: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_SHOWN, value).apply()

    var followDuringTracking: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW_DURING_TRACKING, true)
        set(value) = prefs.edit().putBoolean(KEY_FOLLOW_DURING_TRACKING, value).apply()

    private companion object {
        const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        const val KEY_FOLLOW_DURING_TRACKING = "follow_during_tracking"
    }
}
