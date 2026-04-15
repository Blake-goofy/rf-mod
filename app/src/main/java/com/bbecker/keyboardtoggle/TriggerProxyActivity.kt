package com.bbecker.whmkeyboardtoggle

import android.app.Activity
import android.os.Build
import android.os.Bundle

class TriggerProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toggleAccepted = KeyboardToggleAccessibilityService.requestToggle()
        if (!toggleAccepted && BuildConfig.DEBUG) {
            AppLogger.w(this, TAG, "Accessibility service is not connected.")
        }

        finish()
        suppressTransitionAnimation()
    }

    override fun onResume() {
        super.onResume()
        finish()
        suppressTransitionAnimation()
    }

    private fun suppressTransitionAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            return
        }

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "TriggerProxyActivity"
    }
}
