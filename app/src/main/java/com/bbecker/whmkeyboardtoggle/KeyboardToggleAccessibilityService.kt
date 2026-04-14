package com.bbecker.whmkeyboardtoggle

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class KeyboardToggleAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFocusedEditable: AccessibilityNodeInfo? = null

    private val toggleRunnable = Runnable {
        if (isInputMethodWindowVisible()) {
            hideKeyboard()
        } else {
            showKeyboard()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val currentInfo = serviceInfo ?: AccessibilityServiceInfo()
        currentInfo.flags = currentInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        setServiceInfo(currentInfo)

        configureSoftKeyboardMode()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() == packageName) {
            return
        }

        captureEditableSnapshot(event.source)

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            captureEditableSnapshot(findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }

        mainHandler.removeCallbacks(toggleRunnable)
        clearEditableSnapshot()
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) {
            instance = null
        }

        mainHandler.removeCallbacks(toggleRunnable)
        clearEditableSnapshot()
        return super.onUnbind(intent)
    }

    private fun enqueueToggle() {
        mainHandler.removeCallbacks(toggleRunnable)
        mainHandler.postDelayed(toggleRunnable, TOGGLE_DELAY_MS)
    }

    private fun hideKeyboard() {
        if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
            Log.w(TAG, "BACK action was not accepted while IME was visible.")
        }
    }

    private fun showKeyboard() {
        configureSoftKeyboardMode()

        val target = resolveEditableTarget()
        if (target == null) {
            Log.w(TAG, "No editable field was discoverable in the active app.")
            return
        }

        if (!target.isFocused) {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        if (supportsClick(target) && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return
        }

        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            Log.w(TAG, "Editable field had invalid bounds; cannot tap it.")
            return
        }

        if (!dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())) {
            Log.w(TAG, "Gesture dispatch failed while trying to show the IME.")
        }
    }

    private fun configureSoftKeyboardMode() {
        try {
            softKeyboardController.setShowMode(SHOW_MODE_IGNORE_HARD_KEYBOARD)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not force soft keyboard mode.", error)
        }
    }

    private fun isInputMethodWindowVisible(): Boolean {
        return windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }

    private fun resolveEditableTarget(): AccessibilityNodeInfo? {
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && isUsableEditableNode(focusedNode) && !isOwnPackageNode(focusedNode)) {
            return focusedNode
        }

        val snapshot = lastFocusedEditable
        if (snapshot != null && snapshot.refresh() && isUsableEditableNode(snapshot) && !isOwnPackageNode(snapshot)) {
            return snapshot
        }

        val activeRoot = rootInActiveWindow
        if (activeRoot != null && !isOwnPackageNode(activeRoot)) {
            findEditableNode(activeRoot)?.let { return it }
        }

        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                continue
            }

            val root = window.root ?: continue
            if (isOwnPackageNode(root)) {
                continue
            }

            findEditableNode(root)?.let { return it }
        }

        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isUsableEditableNode(node)) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findEditableNode(child)
            if (match != null) {
                return match
            }
        }

        return null
    }

    private fun isUsableEditableNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return node.isVisibleToUser &&
            node.isEnabled &&
            (node.isEditable || node.inputType != 0 || className.endsWith("EditText"))
    }

    private fun supportsClick(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    }

    private fun isOwnPackageNode(node: AccessibilityNodeInfo): Boolean {
        return node.packageName?.toString() == packageName
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            ViewConfiguration.getTapTimeout().toLong(),
        )
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, null, null)
    }

    private fun captureEditableSnapshot(node: AccessibilityNodeInfo?) {
        if (node == null || isOwnPackageNode(node) || !isUsableEditableNode(node)) {
            return
        }

        replaceEditableSnapshot(copyOf(node))
    }

    private fun replaceEditableSnapshot(node: AccessibilityNodeInfo) {
        lastFocusedEditable = node
    }

    private fun clearEditableSnapshot() {
        lastFocusedEditable = null
    }

    @Suppress("DEPRECATION")
    private fun copyOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        return AccessibilityNodeInfo.obtain(node)
    }

    companion object {
        private const val TAG = "KeyboardToggleSvc"
        private const val TOGGLE_DELAY_MS = 180L

        @Volatile
        private var instance: KeyboardToggleAccessibilityService? = null

        fun requestToggle(): Boolean {
            val service = instance ?: return false
            service.enqueueToggle()
            return true
        }
    }
}
