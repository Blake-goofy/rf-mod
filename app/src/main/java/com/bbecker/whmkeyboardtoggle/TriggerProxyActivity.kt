package com.bbecker.whmkeyboardtoggle

import android.app.Activity
import android.os.Bundle

class TriggerProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (KeyboardToggleAccessibilityService.requestToggle()) {
            AppLogger.i(this, TAG, "Forwarded toggle request to the accessibility service.")
        } else {
            AppLogger.w(this, TAG, "Accessibility service is not connected; enable it in Settings before testing.")
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
