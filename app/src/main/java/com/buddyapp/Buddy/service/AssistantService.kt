package com.buddyapp.Buddy.service

import android.accessibilityservice.AccessibilityService
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import com.buddyapp.Buddy.api.PythonBridge
import com.buddyapp.Buddy.manager.CommandManager
import com.buddyapp.Buddy.manager.HistoryManager
import com.buddyapp.Buddy.manager.KeyManager
import com.buddyapp.Buddy.manager.UsageManager
import com.buddyapp.Buddy.model.Command
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AssistantService : AccessibilityService() {

    private lateinit var keyManager: KeyManager
    private lateinit var usageManager: UsageManager
    private lateinit var commandManager: CommandManager
    private lateinit var historyManager: HistoryManager
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    @Volatile private var isProcessing = false
    private val handler = Handler(Looper.getMainLooper())
    private var triggerLastChars = setOf<Char>()
    private var cachedPrefix = CommandManager.DEFAULT_PREFIX
    private var currentJob: Job? = null
    @Volatile private var lastOriginalText: String? = null
    private var lastTriggerRefresh = 0L
    private var currentOverlayToast: View? = null
    private var dismissRunnable: Runnable? = null
    private var dismissAnimator: AnimatorSet? = null
    private var enterAnimator: AnimatorSet? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val TRIGGER_REFRESH_INTERVAL_MS = 5_000L
        const val DEFAULT_TEMPERATURE = 0.5
        val SPINNER_FRAMES = arrayOf("✦","✧","✦","✧")
        const val TOAST_BACKGROUND_COLOR = 0xE6323232.toInt()
        const val TOAST_DURATION_MS = 3500L
        const val TOAST_BOTTOM_MARGIN_DP = 64
        const val TOAST_ANIM_DURATION_MS = 300L
        const val TOAST_SLIDE_DISTANCE_DP = 40
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        keyManager = KeyManager(applicationContext)
        usageManager = UsageManager(applicationContext)
        commandManager = CommandManager(applicationContext)
        historyManager = HistoryManager(applicationContext)
        updateTriggers()
    }

    private fun updateTriggers() {
        cachedPrefix = commandManager.getTriggerPrefix()
        val cmds = commandManager.getCommands()
        triggerLastChars = cmds.mapNotNull { it.trigger.lastOrNull() }.toSet()
        lastTriggerRefresh = System.currentTimeMillis()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || isProcessing) return
        val source = event.source ?: return
        val text = source.text?.toString() ?: return
        if (text.isEmpty()) return

        if (System.currentTimeMillis() - lastTriggerRefresh > TRIGGER_REFRESH_INTERVAL_MS) {
            updateTriggers()
        }

        val lastChar = text[text.length - 1]
        if (!triggerLastChars.contains(lastChar)) {
            if (!lastChar.isLetterOrDigit() || !text.contains("${cachedPrefix}translate:")) return
        }

        val command = commandManager.findCommand(text) ?: return
        val cleanText = text.substring(0, text.length - command.trigger.length).trim()

        if (command.trigger.endsWith("undo") && command.isBuiltIn) {
            if (source.isPassword) return
            isProcessing = true
            currentJob?.cancel()
            handleUndo(source, cleanText)
            return
        }

        if (cleanText.isEmpty() && !command.isTextReplacer) return
        if (source.isPassword) return
        isProcessing = true
        currentJob?.cancel()

        if (command.isTextReplacer) {
            val prefixText = text.substring(0, text.length - command.trigger.length)
            val newText = prefixText + command.prompt
            handleTextReplacer(source, newText, text, command)
            return
        }

        processCommand(source, cleanText, command)
    }

    private fun processCommand(source: AccessibilityNodeInfo, text: String, command: Command) {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val providerType = prefs.getString("provider_type", "gemini") ?: "gemini"
        val model: String
        val endpoint: String

        if (providerType == "custom") {
            model = prefs.getString("custom_model", "") ?: ""
            endpoint = prefs.getString("custom_endpoint", "") ?: ""
            if (model.isBlank() || endpoint.isBlank()) {
                serviceScope.launch { showToast("Custom provider not configured. Set endpoint and model in Settings.") }
                isProcessing = false
                return
            }
        } else {
            model = prefs.getString("model", "gemini-3.1-flash-lite-preview") ?: "gemini-3.1-flash-lite-preview"
            endpoint = ""
        }

        currentJob = serviceScope.launch {
            val originalText = text
            var spinnerJob: Job? = null
            try {
                withTimeout(90_000) {
                    val maxAttempts = keyManager.getKeys().size.coerceAtLeast(1)
                    var lastErrorMsg: String? = null
                    var succeeded = false

                    for (attempt in 0 until maxAttempts) {
                        val key = keyManager.getNextKey() ?: break

                        if (spinnerJob == null) {
                            spinnerJob = startInlineSpinner(source, originalText)
                        }

                        // ── Python does the AI call ──────────────────────────
                        val result = if (providerType == "custom") {
                            PythonBridge.openaiGenerate(command.prompt, text, key, model, DEFAULT_TEMPERATURE, endpoint)
                        } else {
                            PythonBridge.geminiGenerate(command.prompt, text, key, model, DEFAULT_TEMPERATURE)
                        }

                        usageManager.recordRequest(key, result.isSuccess)

                        if (result.isSuccess) {
                            spinnerJob?.cancel(); spinnerJob = null
                            lastOriginalText = originalText
                            val generatedText = result.getOrThrow()
                            replaceText(source, generatedText)
                            historyManager.addHistoryItem(originalText, generatedText, command.trigger)
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            succeeded = true
                            break
                        }

                        val msg = result.exceptionOrNull()?.message ?: ""
                        lastErrorMsg = msg
                        val isRateLimit = msg.contains("Rate limit", ignoreCase = true)
                        val isInvalidKey = msg.contains("Invalid API key", ignoreCase = true) ||
                                           msg.contains("API key not valid", ignoreCase = true)

                        if (isRateLimit) {
                            val seconds = Regex("retry after (\\d+)s").find(msg)?.groupValues?.get(1)?.toLongOrNull() ?: 60
                            keyManager.reportRateLimit(key, seconds)
                        } else if (isInvalidKey) {
                            keyManager.markInvalid(key)
                        } else {
                            break
                        }
                    }

                    if (!succeeded) {
                        spinnerJob?.cancel(); spinnerJob = null
                        replaceText(source, originalText)
                        performHapticFeedback(HapticFeedbackConstants.REJECT)
                        when {
                            lastErrorMsg != null -> showToast("Buddy Error: $lastErrorMsg")
                            else -> {
                                val waitMs = keyManager.getShortestWaitTimeMs()
                                if (waitMs != null) {
                                    val waitSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
                                    showToast("API key rate limited. Try again in ${waitSec}s")
                                } else if (keyManager.getKeys().isEmpty()) {
                                    showToast("No API keys configured")
                                } else {
                                    showToast("All API keys are invalid. Please check your keys")
                                }
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                spinnerJob?.cancel()
                try { replaceText(source, originalText) } catch (_: Exception) {}
                showToast("Request timed out")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                spinnerJob?.cancel()
                try { replaceText(source, originalText) } catch (_: Exception) {
                    showToast("Could not restore original text")
                }
                showToast("Buddy Error: ${e.message}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    spinnerJob?.cancel()
                    if (!handler.postDelayed({ isProcessing = false }, 500)) isProcessing = false
                }
            }
        }
    }

    private fun handleUndo(source: AccessibilityNodeInfo, currentText: String) {
        currentJob = serviceScope.launch {
            try {
                val previousText = lastOriginalText
                if (previousText == null) {
                    replaceText(source, currentText)
                    performHapticFeedback(HapticFeedbackConstants.REJECT)
                    showToast("Nothing to undo")
                } else {
                    lastOriginalText = currentText
                    replaceText(source, previousText)
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { showToast("Could not undo")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (!handler.postDelayed({ isProcessing = false }, 500)) isProcessing = false
                }
            }
        }
    }

    private fun handleTextReplacer(source: AccessibilityNodeInfo, newText: String, originalText: String, command: Command) {
        currentJob = serviceScope.launch {
            try {
                lastOriginalText = originalText
                replaceText(source, newText)
                historyManager.addHistoryItem(originalText, newText, command.trigger)
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } catch (e: Exception) {
                showToast("Buddy Error: ${e.message}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (!handler.postDelayed({ isProcessing = false }, 500)) isProcessing = false
                }
            }
        }
    }

    private suspend fun replaceText(source: AccessibilityNodeInfo, newText: String) = withContext(Dispatchers.Main) {
        source.refresh()
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        val success = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        if (!success) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val oldClip = clipboard.primaryClip
            clipboard.setPrimaryClip(ClipData.newPlainText("Buddy Result", newText))
            val selectArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, source.text?.length ?: 0)
            }
            source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
            source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            handler.postDelayed({ if (oldClip != null) clipboard.setPrimaryClip(oldClip) }, 500)
        }
    }

    private fun setFieldText(source: AccessibilityNodeInfo, text: String) {
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun startInlineSpinner(source: AccessibilityNodeInfo, baseText: String): Job {
        return serviceScope.launch(Dispatchers.Main) {
            var frameIndex = 0
            while (isActive) {
                setFieldText(source, "$baseText ${SPINNER_FRAMES[frameIndex]}")
                frameIndex = (frameIndex + 1) % SPINNER_FRAMES.size
                delay(80)
            }
        }
    }

    private suspend fun showToast(msg: String) = withContext(Dispatchers.Main) {
        dismissOverlayToast()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val textView = TextView(applicationContext).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(24), dp(12), dp(24), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
            background = GradientDrawable().apply {
                setColor(TOAST_BACKGROUND_COLOR)
                cornerRadius = dp(24).toFloat()
            }
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dp(TOAST_SLIDE_DISTANCE_DP).toFloat()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(TOAST_BOTTOM_MARGIN_DP)
            windowAnimations = 0
        }
        try {
            wm.addView(textView, params)
            currentOverlayToast = textView
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(textView, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, dp(TOAST_SLIDE_DISTANCE_DP).toFloat(), 0f)
                )
                duration = TOAST_ANIM_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
                enterAnimator = this
            }
            val runnable = Runnable { dismissOverlayToastAnimated() }
            dismissRunnable = runnable
            handler.postDelayed(runnable, TOAST_DURATION_MS)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismissOverlayToast() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null; enterAnimator?.cancel(); enterAnimator = null
        dismissAnimator?.cancel(); dismissAnimator = null
        currentOverlayToast?.let { view ->
            try { view.visibility = View.GONE; (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) } catch (_: Exception) {}
            currentOverlayToast = null
        }
    }

    private fun dismissOverlayToastAnimated() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null; enterAnimator?.cancel(); enterAnimator = null; dismissAnimator?.cancel()
        currentOverlayToast?.let { view ->
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                dismissAnimator = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
                        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, dp(TOAST_SLIDE_DISTANCE_DP).toFloat())
                    )
                    duration = TOAST_ANIM_DURATION_MS; interpolator = DecelerateInterpolator()
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            view.visibility = View.GONE
                            try { wm.removeView(view) } catch (_: Exception) {}
                            dismissAnimator = null
                        }
                    })
                    start()
                }
            } catch (_: Exception) {}
            currentOverlayToast = null
        }
    }

    @Suppress("DEPRECATION")
    private fun performHapticFeedback(feedbackType: Int) {
        handler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    val v = vm.defaultVibrator
                    when (feedbackType) {
                        HapticFeedbackConstants.CONFIRM -> v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        HapticFeedbackConstants.REJECT  -> v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    when (feedbackType) {
                        HapticFeedbackConstants.CONFIRM -> v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        HapticFeedbackConstants.REJECT  -> v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                    }
                } else {
                    (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(50)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() { isProcessing = false; currentJob?.cancel() }
    override fun onDestroy() { super.onDestroy(); dismissOverlayToast(); serviceScope.cancel() }
}
