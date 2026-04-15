package com.bbecker.whmkeyboardtoggle

import android.app.Activity
import android.os.Bundle

class TriggerProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toggleAccepted = KeyboardToggleAccessibilityService.requestToggle()
        if (!toggleAccepted && BuildConfig.DEBUG) {
            AppLogger.w(this, TAG, "Accessibility service is not connected.")
        }

        finish()
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "TriggerProxyActivity"
    }
}
