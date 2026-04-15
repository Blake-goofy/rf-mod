package com.bbecker.whmkeyboardtoggle

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class KeyboardToggleAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFocusedEditable: AccessibilityNodeInfo? = null
    private var lastFocusedEditableFallback: EditableFallback? = null
    private var isKeyboardVisibleFromEvents = false
    private var lastKeyboardPackageName: String? = null
    private var pendingToggleMode: ToggleMode? = null

    private val toggleRunnable = Runnable {
        val requestedMode = pendingToggleMode
        pendingToggleMode = null
        val imeWindowVisible = isInputMethodWindowVisible()
        val eventKeyboardVisible = isKeyboardVisibleFromEvents
        AppLogger.i(
            this,
            TAG,
            "Running keyboard toggle. requestedMode=${requestedMode ?: resolveToggleMode(imeWindowVisible, eventKeyboardVisible)} imeWindowVisible=$imeWindowVisible eventKeyboardVisible=$eventKeyboardVisible keyboardPackage=${lastKeyboardPackageName.orEmpty()}",
        )
        logWindowSnapshot("pre-toggle")
        when (requestedMode ?: resolveToggleMode(imeWindowVisible, eventKeyboardVisible)) {
            ToggleMode.HIDE -> {
                if (imeWindowVisible || eventKeyboardVisible) {
                    hideKeyboard()
                } else {
                    AppLogger.i(this, TAG, "Skipped BACK because the pending hide request found the keyboard already hidden.")
                    postHideVerification("hide-already-satisfied")
                }
            }
            ToggleMode.SHOW -> showKeyboard()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val currentInfo = serviceInfo ?: AccessibilityServiceInfo()
        currentInfo.flags = currentInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        setServiceInfo(currentInfo)

        isKeyboardVisibleFromEvents = false
        lastKeyboardPackageName = null
        AppLogger.i(this, TAG, "Accessibility service connected.")

        configureSoftKeyboardMode()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() == packageName) {
            return
        }

        updateKeyboardVisibilityFromEvent(event)
        logAccessibilityEvent(event)
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
        pendingToggleMode = null
        clearEditableSnapshot()
        isKeyboardVisibleFromEvents = false
        lastKeyboardPackageName = null
        AppLogger.i(this, TAG, "Accessibility service destroyed.")
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) {
            instance = null
        }

        mainHandler.removeCallbacks(toggleRunnable)
        pendingToggleMode = null
        clearEditableSnapshot()
        isKeyboardVisibleFromEvents = false
        lastKeyboardPackageName = null
        AppLogger.i(this, TAG, "Accessibility service unbound.")
        return super.onUnbind(intent)
    }

    internal fun enqueueToggle() {
        if (pendingToggleMode != null) {
            AppLogger.i(this, TAG, "Ignored duplicate toggle request while $pendingToggleMode is already pending.")
            return
        }

        pendingToggleMode = resolveToggleMode()
        mainHandler.removeCallbacks(toggleRunnable)
        AppLogger.i(this, TAG, "Keyboard toggle requested. requestedMode=$pendingToggleMode")
        mainHandler.postDelayed(toggleRunnable, TOGGLE_DELAY_MS)
    }

    private fun resolveToggleMode(): ToggleMode {
        return resolveToggleMode(
            imeWindowVisible = isInputMethodWindowVisible(),
            eventKeyboardVisible = isKeyboardVisibleFromEvents,
        )
    }

    private fun resolveToggleMode(imeWindowVisible: Boolean, eventKeyboardVisible: Boolean): ToggleMode {
        return if (imeWindowVisible || eventKeyboardVisible) {
            ToggleMode.HIDE
        } else {
            ToggleMode.SHOW
        }
    }

    private fun hideKeyboard() {
        if (performGlobalAction(GLOBAL_ACTION_BACK)) {
            AppLogger.i(this, TAG, "Requested BACK to hide the visible IME.")
            postHideVerification("after-hide-request") {
                reconcileKeyboardVisibilityAfterHideRequest()
            }
        } else {
            AppLogger.w(this, TAG, "BACK action was not accepted while IME was visible.")
        }
    }

    private fun showKeyboard(forceRefocus: Boolean = false) {
        configureSoftKeyboardMode()

        val target = resolveEditableTarget()
        if (target != null) {
            AppLogger.i(this, TAG, "Resolved editable field in the foreground app.")
            logNodeSnapshot("editable-target", target)

            if (target.isFocused && !forceRefocus && supportsAction(target, AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)) {
                    AppLogger.i(this, TAG, "Cleared focus from the editable field before re-requesting the IME.")
                    mainHandler.postDelayed(
                        {
                            showKeyboard(forceRefocus = true)
                        },
                        REFOCUS_SHOW_DELAY_MS,
                    )
                    return
                }

                AppLogger.w(this, TAG, "CLEAR_FOCUS was available but was not accepted by the editable field.")
            }

            if (!target.isFocused) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                    AppLogger.i(this, TAG, "Focused the editable field before requesting the IME.")
                } else {
                    AppLogger.w(this, TAG, "FOCUS action was not accepted by the editable field.")
                }
            }

            if (supportsClick(target) && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                AppLogger.i(this, TAG, "Clicked the editable field to request the IME.")
                postDiagnosticSnapshot("after-click-show-request")
                return
            }

            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                if (dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())) {
                    AppLogger.i(this, TAG, "Tapped the editable field to request the IME.")
                    postDiagnosticSnapshot("after-tap-show-request")
                    return
                }
            } else {
                AppLogger.w(this, TAG, "Editable field had invalid bounds; cannot tap it directly.")
            }
        }

        if (dispatchFallbackTap()) {
            return
        }

        AppLogger.w(this, TAG, "No editable field was discoverable in the active app.")
    }

    private fun configureSoftKeyboardMode() {
        try {
            softKeyboardController.setShowMode(SHOW_MODE_IGNORE_HARD_KEYBOARD)
        } catch (error: RuntimeException) {
            AppLogger.w(this, TAG, "Could not force soft keyboard mode.", error)
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

    private fun postDiagnosticSnapshot(label: String, afterSnapshot: (() -> Unit)? = null) {
        if (!ENABLE_DIAGNOSTICS) {
            afterSnapshot?.invoke()
            return
        }

        mainHandler.postDelayed(
            {
                logWindowSnapshot(label)
                afterSnapshot?.invoke()
                logNodeSnapshot("focused-input:$label", findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
                logFocusedContext(label)
            },
            DIAGNOSTIC_SNAPSHOT_DELAY_MS,
        )
    }

    private fun postHideVerification(label: String, afterVerification: (() -> Unit)? = null) {
        if (ENABLE_DIAGNOSTICS) {
            postDiagnosticSnapshot(label, afterVerification)
            return
        }

        mainHandler.postDelayed(
            {
                afterVerification?.invoke()
            },
            DIAGNOSTIC_SNAPSHOT_DELAY_MS,
        )
    }

    private fun reconcileKeyboardVisibilityAfterHideRequest() {
        val imeWindowVisible = isInputMethodWindowVisible()
        if (imeWindowVisible) {
            AppLogger.i(this, TAG, "Hide verification: IME window is still visible after BACK.")
            return
        }

        if (isKeyboardVisibleFromEvents) {
            setKeyboardVisibleFromEvents(false, "verified-hidden-after-back")
        } else {
            AppLogger.i(this, TAG, "Hide verification: IME window is gone and keyboard state is already hidden.")
        }
    }

    private fun logWindowSnapshot(label: String) {
        if (!ENABLE_DIAGNOSTICS) {
            return
        }

        val activePackage = resolveActiveAppPackageName().orEmpty()
        AppLogger.i(this, TAG, "Window snapshot [$label]: activeApp=$activePackage windowCount=${windows.size}")

        windows
            .sortedBy { it.layer }
            .forEachIndexed { index, window ->
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val root = window.root
                val packageName = root?.packageName?.toString().orEmpty()
                val title = window.title?.toString().orEmpty()
                AppLogger.i(
                    this,
                    TAG,
                    "Window[$index]: type=${windowTypeLabel(window.type)} layer=${window.layer} active=${window.isActive} focused=${window.isFocused} package=$packageName title=${sanitizeForLog(title)} bounds=$bounds",
                )
            }
    }

    private fun logNodeSnapshot(label: String, node: AccessibilityNodeInfo?) {
        if (!ENABLE_DIAGNOSTICS) {
            return
        }

        if (node == null) {
            AppLogger.i(this, TAG, "Node snapshot [$label]: none")
            return
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        AppLogger.i(
            this,
            TAG,
            "Node snapshot [$label]: package=${node.packageName} class=${node.className} viewId=${sanitizeForLog(node.viewIdResourceName)} text=${sanitizeForLog(node.text)} hint=${sanitizeForLog(node.hintText)} desc=${sanitizeForLog(node.contentDescription)} focused=${node.isFocused} accessibilityFocused=${node.isAccessibilityFocused} editable=${node.isEditable} clickable=${node.isClickable} longClickable=${node.isLongClickable} visible=${node.isVisibleToUser} enabled=${node.isEnabled} selected=${node.isSelected} inputType=${node.inputType} childCount=${node.childCount} actions=${describeActions(node)} bounds=$bounds",
        )
    }

    private fun logFocusedContext(label: String) {
        if (!ENABLE_DIAGNOSTICS) {
            return
        }

        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode == null) {
            AppLogger.i(this, TAG, "Focused context [$label]: no focused input node")
        } else {
            logAncestorChain(label, focusedNode)
        }

        val activeRoot = rootInActiveWindow
        if (activeRoot == null || isOwnPackageNode(activeRoot)) {
            AppLogger.i(this, TAG, "Focused context [$label]: no active external root")
            return
        }

        AppLogger.i(this, TAG, "Focused context [$label]: dumping active root subtree")
        logNodeSubtree(label, activeRoot)
    }

    private fun logAncestorChain(label: String, node: AccessibilityNodeInfo) {
        if (!ENABLE_DIAGNOSTICS) {
            return
        }

        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            val bounds = Rect()
            current.getBoundsInScreen(bounds)
            AppLogger.i(
                this,
                TAG,
                "Ancestor[$label][$depth]: class=${current.className} viewId=${sanitizeForLog(current.viewIdResourceName)} text=${sanitizeForLog(current.text)} desc=${sanitizeForLog(current.contentDescription)} focused=${current.isFocused} clickable=${current.isClickable} visible=${current.isVisibleToUser} childCount=${current.childCount} actions=${describeActions(current)} bounds=$bounds",
            )
            current = current.parent
            depth += 1
        }
    }

    private fun logNodeSubtree(label: String, root: AccessibilityNodeInfo) {
        if (!ENABLE_DIAGNOSTICS) {
            return
        }

        val remainingBudget = intArrayOf(MAX_TREE_NODE_LOGS)
        logNodeSubtreeRecursive(label, root, 0, remainingBudget)
    }

    private fun logNodeSubtreeRecursive(
        label: String,
        node: AccessibilityNodeInfo,
        depth: Int,
        remainingBudget: IntArray,
    ) {
        if (!ENABLE_DIAGNOSTICS) {
            return
        }

        if (remainingBudget[0] <= 0) {
            return
        }

        remainingBudget[0] -= 1

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        AppLogger.i(
            this,
            TAG,
            "Tree[$label][${depth}]: ${" ".repeat(depth * 2)}class=${node.className} viewId=${sanitizeForLog(node.viewIdResourceName)} text=${sanitizeForLog(node.text)} hint=${sanitizeForLog(node.hintText)} desc=${sanitizeForLog(node.contentDescription)} focused=${node.isFocused} editable=${node.isEditable} clickable=${node.isClickable} visible=${node.isVisibleToUser} selected=${node.isSelected} childCount=${node.childCount} actions=${describeActions(node)} bounds=$bounds",
        )

        if (depth >= MAX_TREE_DEPTH) {
            return
        }

        for (index in 0 until node.childCount) {
            if (remainingBudget[0] <= 0) {
                return
            }

            val child = node.getChild(index) ?: continue
            logNodeSubtreeRecursive(label, child, depth + 1, remainingBudget)
        }
    }

    private fun logAccessibilityEvent(event: AccessibilityEvent) {
        if (!ENABLE_DIAGNOSTICS) {
            return
        }

        val eventPackageName = event.packageName?.toString().orEmpty()
        val shouldLog = eventPackageName.isNotEmpty() && !isOwnPackagePackageName(eventPackageName)
        if (!shouldLog) {
            return
        }

        if (!isDiagnosticEventType(event.eventType)) {
            return
        }

        AppLogger.i(
            this,
            TAG,
            "Event: type=${eventTypeLabel(event.eventType)} package=$eventPackageName class=${event.className} windowChanges=${event.windowChanges} contentChanges=${event.contentChangeTypes} text=${sanitizeForLog(event.text.joinToString(separator = " | "))} desc=${sanitizeForLog(event.contentDescription)}",
        )

        logNodeSnapshot("event-source:${eventTypeLabel(event.eventType)}", event.source)
    }

    private fun updateKeyboardVisibilityFromEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val eventPackageName = event.packageName?.toString().orEmpty()
        val eventClassName = event.className?.toString().orEmpty()
        val eventText = event.text.joinToString(separator = " | ")

        if (eventClassName == SOFT_INPUT_WINDOW_CLASS_NAME && eventPackageName.isNotEmpty()) {
            lastKeyboardPackageName = eventPackageName
            setKeyboardVisibleFromEvents(true, "window=${sanitizeForLog(eventClassName)}")
            return
        }

        if (eventPackageName.isNotEmpty() && eventPackageName == lastKeyboardPackageName) {
            val normalizedText = eventText.lowercase()
            if (normalizedText.contains(KEYBOARD_HIDDEN_EVENT_TEXT)) {
                setKeyboardVisibleFromEvents(false, "window-state=${sanitizeForLog(eventText)}")
            }
        }
    }

    private fun setKeyboardVisibleFromEvents(visible: Boolean, reason: String) {
        if (isKeyboardVisibleFromEvents == visible) {
            return
        }

        isKeyboardVisibleFromEvents = visible
        AppLogger.i(
            this,
            TAG,
            "Keyboard visibility updated from events: visible=$visible package=${lastKeyboardPackageName.orEmpty()} reason=$reason",
        )
    }

    private fun isDiagnosticEventType(eventType: Int): Boolean {
        return eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    private fun eventTypeLabel(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "VIEW_TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
            else -> eventType.toString()
        }
    }

    private fun supportsAction(node: AccessibilityNodeInfo, actionId: Int): Boolean {
        return node.actionList.any { action -> action.id == actionId }
    }

    private fun describeActions(node: AccessibilityNodeInfo): String {
        return node.actionList
            .map { action -> actionIdLabel(action.id) }
            .joinToString(separator = ",")
    }

    private fun actionIdLabel(actionId: Int): String {
        return when (actionId) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> "ACCESSIBILITY_FOCUS"
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> "CLEAR_ACCESSIBILITY_FOCUS"
            AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> "CLEAR_FOCUS"
            AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> "CLEAR_SELECTION"
            AccessibilityNodeInfo.ACTION_CLICK -> "CLICK"
            AccessibilityNodeInfo.ACTION_COLLAPSE -> "COLLAPSE"
            AccessibilityNodeInfo.ACTION_COPY -> "COPY"
            AccessibilityNodeInfo.ACTION_CUT -> "CUT"
            AccessibilityNodeInfo.ACTION_DISMISS -> "DISMISS"
            AccessibilityNodeInfo.ACTION_EXPAND -> "EXPAND"
            AccessibilityNodeInfo.ACTION_FOCUS -> "FOCUS"
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> "LONG_CLICK"
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "NEXT_AT_MOVEMENT_GRANULARITY"
            AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT -> "NEXT_HTML_ELEMENT"
            AccessibilityNodeInfo.ACTION_PASTE -> "PASTE"
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "PREVIOUS_AT_MOVEMENT_GRANULARITY"
            AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT -> "PREVIOUS_HTML_ELEMENT"
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "SCROLL_BACKWARD"
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "SCROLL_FORWARD"
            AccessibilityNodeInfo.ACTION_SELECT -> "SELECT"
            AccessibilityNodeInfo.ACTION_SET_SELECTION -> "SET_SELECTION"
            AccessibilityNodeInfo.ACTION_SET_TEXT -> "SET_TEXT"
            else -> actionId.toString()
        }
    }

    private fun isOwnPackagePackageName(packageName: String): Boolean {
        return packageName == this.packageName
    }

    private fun sanitizeForLog(text: CharSequence?): String {
        if (text.isNullOrBlank()) {
            return ""
        }

        return TextUtils.substring(text, 0, minOf(text.length, MAX_LOG_TEXT_LENGTH))
            .replace('\n', ' ')
            .replace('\r', ' ')
    }

    private fun windowTypeLabel(type: Int): String {
        return when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "SPLIT_SCREEN_DIVIDER"
            else -> type.toString()
        }
    }

    private fun dispatchFallbackTap(): Boolean {
        val fallback = lastFocusedEditableFallback
        if (fallback == null) {
            AppLogger.i(this, TAG, "No fallback editable field is cached.")
            return false
        }

        val activePackage = resolveActiveAppPackageName()
        if (activePackage == null) {
            AppLogger.i(this, TAG, "No active application window is available for fallback tapping.")
            return false
        }

        if (activePackage != fallback.packageName) {
            AppLogger.i(
                this,
                TAG,
                "Skipping fallback tap because the foreground app changed from ${fallback.packageName} to $activePackage.",
            )
            return false
        }

        val bounds = fallback.bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            AppLogger.w(this, TAG, "Cached editable field bounds are invalid; cannot use fallback tap.")
            return false
        }

        if (dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())) {
            AppLogger.i(this, TAG, "Tapped the cached editable field to request the IME.")
            return true
        }

        AppLogger.w(this, TAG, "Gesture dispatch failed while trying the cached editable field.")
        return false
    }

    private fun resolveActiveAppPackageName(): String? {
        val activeRoot = rootInActiveWindow
        if (activeRoot != null && !isOwnPackageNode(activeRoot)) {
            return activeRoot.packageName?.toString()
        }

        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || (!window.isActive && !window.isFocused)) {
                continue
            }

            val root = window.root ?: continue
            if (!isOwnPackageNode(root)) {
                return root.packageName?.toString()
            }
        }

        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                continue
            }

            val root = window.root ?: continue
            if (!isOwnPackageNode(root)) {
                return root.packageName?.toString()
            }
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

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        replaceEditableSnapshot(
            copyOf(node),
            EditableFallback(
                packageName = node.packageName?.toString().orEmpty(),
                bounds = Rect(bounds),
            ),
        )
    }

    private fun replaceEditableSnapshot(node: AccessibilityNodeInfo, fallback: EditableFallback) {
        lastFocusedEditable = node
        lastFocusedEditableFallback = fallback
    }

    private fun clearEditableSnapshot() {
        lastFocusedEditable = null
        lastFocusedEditableFallback = null
    }

    @Suppress("DEPRECATION")
    private fun copyOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        return AccessibilityNodeInfo.obtain(node)
    }

    companion object {
        private const val TAG = "KeyboardToggleSvc"
        private const val TOGGLE_DELAY_MS = 300L
        private const val REFOCUS_SHOW_DELAY_MS = 120L
        private const val DIAGNOSTIC_SNAPSHOT_DELAY_MS = 500L
        private const val ENABLE_DIAGNOSTICS = BuildConfig.DEBUG
        private const val MAX_LOG_TEXT_LENGTH = 80
        private const val MAX_TREE_DEPTH = 5
        private const val MAX_TREE_NODE_LOGS = 80
        private const val MAX_ANCESTOR_DEPTH = 8
        private const val SOFT_INPUT_WINDOW_CLASS_NAME = "android.inputmethodservice.SoftInputWindow"
        private const val KEYBOARD_HIDDEN_EVENT_TEXT = "keyboard hidden"

        @Volatile
        private var instance: KeyboardToggleAccessibilityService? = null

        fun requestToggle(): Boolean {
            val service = instance ?: return false
            service.enqueueToggle()
            return true
        }
    }

    private data class EditableFallback(
        val packageName: String,
        val bounds: Rect,
    )

    private enum class ToggleMode {
        HIDE,
        SHOW,
    }
}
