package com.bbecker.whmkeyboardtoggle

import android.app.Activity
import android.os.Bundle
import android.util.Log

class TriggerProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!KeyboardToggleAccessibilityService.requestToggle()) {
            Log.w(TAG, "Accessibility service is not connected; enable it in Settings before testing.")
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
