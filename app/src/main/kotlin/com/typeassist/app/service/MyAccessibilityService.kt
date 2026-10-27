package com.typeassist.app.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.typeassist.app.R
import com.typeassist.app.api.AiProvider
import com.typeassist.app.api.AiStream
import com.typeassist.app.api.ApiErrorFormatter
import com.typeassist.app.api.CloudflareApiClient
import com.typeassist.app.api.CustomApiClient
import com.typeassist.app.api.GeminiApiClient
import com.typeassist.app.api.HttpClients
import com.typeassist.app.api.LocalLlmClient
import com.typeassist.app.api.PromptImage
import com.typeassist.app.api.ScreenPrompts
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.ConfigMigrations
import com.typeassist.app.data.HistoryManager
import com.typeassist.app.data.HistoryStore
import com.typeassist.app.data.SecretStore
import com.typeassist.app.data.Secrets
import com.typeassist.app.data.UsageStore
import com.typeassist.app.data.UsageTracker
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.data.StreamRecoveryManager
import com.typeassist.app.data.StreamThrottle
import com.typeassist.app.ui.ThemePalettes
import com.typeassist.app.utils.CommandGuard
import com.typeassist.app.utils.ScreenTextExtractor
import okhttp3.*
import java.util.TimeZone
import java.util.regex.Pattern
import android.util.Log

class MyAccessibilityService : AccessibilityService() {

    private val TAG = "TypeAssistService"
    @Volatile private var isSnippetSelectionVisible = false

    companion object {
        init {
            System.loadLibrary("typeassist")
        }

        /**
         * How long after we write text ourselves an identical text-changed event is still treated
         * as our own echo rather than the user typing.
         */
        private const val ECHO_WINDOW_MS = 1500L

        /** Trailing shortcuts that read the screen. */
        private const val SCREEN_REPLY_TRIGGER = ".reply"
        private const val SCREEN_SUMMARY_TRIGGER = ".sum"
        private const val SCREEN_ASK_TRIGGER = ".ta"

        /** Marker that makes a normal `.ta` question refer to what is on screen. */
        private const val SCREEN_ASK_MARKER = "@screen"

        /** Offline text tools: they work without any provider configured. */
        private const val CLIP_TRIGGER = ".clip"
        private const val COUNT_TRIGGER = ".count"
        private val TEXT_TOOLS: Map<String, (String) -> String> = mapOf(
            ".upper" to { s -> s.uppercase() },
            ".lower" to { s -> s.lowercase() },
            ".title" to { s -> com.typeassist.app.utils.UtilityBelt.titleCase(s) }
        )

    }

    external fun stringFromJNI(): String
    external fun loadModel(path: String, useGpu: Boolean): Boolean
    external fun generateResponseNative(prompt: String, temp: Float, topP: Float, maxTokens: Int): String
    external fun stopGenerationNative()
    external fun unloadModel()

    private val client = HttpClients.shared()

    /**
     * One Gson instance for the whole service: each new instance would rebuild every type adapter
     * for [AppConfig] and its nested lists, on the path between the user's keystroke and the
     * request going out.
     */
    private val gson = com.google.gson.Gson()

    /** The app's preferences, resolved once instead of by every accessibility event. */
    private val configPrefs by lazy { getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE) }

    /** Tracks in-flight requests across process death, so a crash mid-answer is reported. */
    /** Tracks in-flight requests across process death, so a crash mid-answer is reported. */
    private val streamRecovery by lazy { StreamRecoveryManager(configPrefs) }

    /** Opens the encrypted API keys the app wrote to the settings file. */
    private val secretStore = SecretStore()
    private val geminiApiClient = GeminiApiClient(client)
    private val cloudflareApiClient = CloudflareApiClient(client)
    private val customApiClient = CustomApiClient(client)
    private val localLlmClient = LocalLlmClient(this)
    
    private lateinit var overlayManager: OverlayManager
    
    // -- Undo Cache --
    private var lastNode: AccessibilityNodeInfo? = null
    private var originalTextCache: String = ""
    private var undoCacheTimestamp: Long = 0L

    // -- Result chip / streaming / echo detection --
    /** The command that produced the current result, so the chip can offer Retry. */
    private var lastCommand: PendingAiCommand? = null

    /** Text we last wrote into a field ourselves; events echoing it are ignored. */
    private var lastAppliedText: String? = null
    private var lastAppliedAt: Long = 0L

    private val uiHandler = Handler(Looper.getMainLooper())

    /** Turns the model's answer into the full field text for the command that is running. */
    private data class PendingAiCommand(
        val prompt: String,
        val userText: String,
        val render: (String) -> String,
        val originalFieldText: String,
        val image: PromptImage? = null
    )

    /** Reminders already shown this session, so a refusal is explained once, not on every keystroke. */
    private val shownRefusals = mutableSetOf<CommandGuard.Refusal>()

    /**
     * Explains a refused command, but only the first time: a trigger that keeps matching while the
     * user types would otherwise show the same message over and over.
     */
    private fun notifyRefusalOnce(refusal: CommandGuard.Refusal) {
        if (!shownRefusals.add(refusal)) return
        Log.d(TAG, "Command refused: $refusal")
        overlayManager.showToast(CommandGuard.message(refusal))
    }

    // -- In-flight stream --
    private var activeStream: AiStream? = null
    private var activeStreamNode: AccessibilityNodeInfo? = null
    private var activeStreamPreText: String? = null
    private var streamDeltaCount = 0
    private val streamBuffer = StringBuilder()
    private val streamThrottle = StreamThrottle()

    /** Incremented for every stream so late callbacks from a cancelled one are ignored. */
    private var streamGeneration = 0

    // -- Debounce --
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingTriggerRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "JNI Test: ${stringFromJNI()}")
        overlayManager = OverlayManager(this)
        overlayManager.onUndoAction = { performUndo() }
        overlayManager.onOverlayShown = { 
            Log.d(TAG, "Callback: Overlay Shown")
            isSnippetSelectionVisible = true 
        }
        overlayManager.onOverlayHidden = { 
            Log.d(TAG, "Callback: Overlay Hidden")
            isSnippetSelectionVisible = false 
        }
        startPersistentNotification()
        // Token totals are read back here so the usage tab shows the stored history, not just the
        // requests made since the phone was last restarted.
        UsageTracker.load(UsageStore(this).load())
        // The command history is read back as well, so the History tab still shows what was there
        // before the service was restarted.
        HistoryManager.attachStore(HistoryStore(this))
        reportInterruptedStream()
        Log.d(TAG, "Service Connected")
    }

    /**
     * If this process died while a request was in flight, say so. There is no field to stream into
     * any more, so the honest outcome is a clear message rather than silence. The record is then
     * dropped: it described one request, which is now gone.
     */
    private fun reportInterruptedStream() {
        try {
            val pending = streamRecovery.pendingStream() ?: return
            streamRecovery.clearPendingStream()
            Log.w(
                TAG,
                "Previous ${pending.provider} request was interrupted by a process restart " +
                    "(${pending.timestamp}); nothing was resumed"
            )
            overlayManager.showToast(
                "The previous ${pending.provider} request was interrupted — try the command again"
            )
        } catch (e: Exception) {
            // Recovery reporting must never keep the service from starting.
            Log.w(TAG, "Could not report an interrupted request: ${e.message}")
        }
    }

    private fun startPersistentNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "typeassist_service",
                    "Prompt AI",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Prompt AI ready in the background"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }

            val intent = Intent(this, com.typeassist.app.MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

            val notification = NotificationCompat.Builder(this, "typeassist_service")
                .setContentTitle("Prompt AI is active")
                .setContentText("Ready to assist with your typing.")
                .setSmallIcon(R.drawable.ic_notification_monochrome) 
                .setLargeIcon(largeIcon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            startForeground(101, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting notification", e)
        }
    }

    /** True while the device is in night mode. Only used to resolve the System appearance. */
    private fun systemIsDark(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Theme for the floating overlays: the Appearance chosen in the app, with System resolved
     * against the device's night mode. Never throws, so a bad preference cannot break the service.
     */
    private fun overlayThemeMode(): String {
        val stored = try {
            configPrefs.getString("theme_mode", null)
        } catch (e: Exception) {
            null
        }
        return ThemePalettes.resolveMode(stored, systemIsDark())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event == null) return

        if (event.packageName?.toString() == packageName) {
            val isTesting = configPrefs.getBoolean("is_testing_active", false)
            if (!isTesting) return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.refresh()
            return
        }

        // The focused field changed: the chip no longer refers to what the user is looking at, and
        // an in-flight stream must not keep writing into a field that lost focus. Focus events that
        // immediately follow our own edit (same text) are ignored, so writing a result cannot
        // dismiss the chip it just showed.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val focusedText = try { event.source?.text?.toString() } catch (e: Exception) { null }
            if (!isOwnEcho(focusedText.orEmpty())) onUserInterruption()
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // Skip processing for internal events with no actual changes
            if (event.addedCount == 0 && event.removedCount == 0) return

            Log.d(TAG, "Event: TYPE_VIEW_TEXT_CHANGED | added: ${event.addedCount} | removed: ${event.removedCount} | text: ${event.text}")
            
            debounceHandler.removeCallbacksAndMessages(null)

            val inputNode = event.source ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
            var currentText = inputNode.text?.toString() ?: ""
            if (currentText.isEmpty() && event.text.isNotEmpty()) {
                currentText = event.text.joinToString("")
            }
            Log.d(TAG, "Current Text: '$currentText'")

            // Text that matches exactly what we just wrote is our own echo (a paste or a streamed
            // chunk), not the user typing: ignore it, otherwise every update would look like an edit.
            if (isOwnEcho(currentText)) return

            // The user (or another app) really changed the field: stop streaming and put the chip
            // away, then carry on parsing what they typed.
            onUserInterruption()

            // A password field is untouchable: no command runs there, nothing is written to the
            // history, and no text is ever sent to a provider. Checked before any command is
            // parsed, so the password cannot leak through a trigger either.
            if (!CommandGuard.allowsAnyCommand(inputNode.isPassword)) {
                notifyRefusalOnce(CommandGuard.Refusal.PASSWORD_FIELD)
                return
            }

            val configJson = configPrefs.getString("config_json", null)
            if (configJson == null) {
                Log.e(TAG, "config_json is null!")
                return
            }

            try {
                // Gson skips constructors, so fields that did not exist when the config was saved
                // arrive as zero values. Restore their defaults before anything reads them. The
                // keys on disk are encrypted; open them so the providers see the real values.
                val config = Secrets.decryptConfig(
                    ConfigMigrations.apply(
                        LoadingIndicatorStyle.sanitize(gson.fromJson(configJson, AppConfig::class.java))
                    ),
                    secretStore::decrypt
                )
                
                // Migration: Convert old single content to contents list
                config.snippets?.forEach { snippet ->
                    if (snippet.contents == null) snippet.contents = mutableListOf()
                    if (snippet.content != null && snippet.content.isNotEmpty()) {
                        if (!snippet.contents.contains(snippet.content)) {
                            snippet.contents.add(snippet.content)
                        }
                        snippet.content = ""
                    }
                }
                
                if (!config.isAppEnabled) {
                    return
                }

                // --- 0. Global Inline Transformation ---
                val globalTriggerPattern = config.globalTriggerPattern
                if (globalTriggerPattern.contains("%") && globalTriggerPattern.length >= 3) {
                    val globalRegexStr = buildGlobalTriggerRegex(globalTriggerPattern)
                    val globalTransformRegex = Pattern.compile(globalRegexStr)
                    val globalMatcher = globalTransformRegex.matcher(currentText)
                    if (globalMatcher.find()) {
                        Log.d(TAG, "Global Trigger Match Found")
                        val contextText = globalMatcher.group(1) ?: ""
                        val instruction = globalMatcher.group(2) ?: ""

                        if (contextText.isNotBlank() && instruction.isNotBlank()) {
                            originalTextCache = currentText
                            if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
                            lastNode = inputNode
                            undoCacheTimestamp = System.currentTimeMillis()

                            val systemPrompt = "Rewrite the following text according to this instruction: $instruction. Return ONLY the rewritten text, no explanations, no chat."

                            executeAiCommand(config, systemPrompt, contextText, inputNode, currentText) { aiText -> aiText }

                            return
                        }
                    }
                }

                // -- Snippets Logic --
                val snippetPrefix = config.snippetTriggerPrefix
                val saveSnippetPattern = config.saveSnippetPattern
                val snippets = config.snippets.sortedByDescending { it.trigger.length }

                var matchedAnySnippet = false
                for (s in snippets) {
                    val fullTrigger = snippetPrefix + s.trigger
                    val idx = currentText.lastIndexOf(fullTrigger)
                    if (idx != -1) {
                        val isAtEnd = idx + fullTrigger.length == currentText.length
                        if (config.allowTriggerAnywhere || isAtEnd) {
                            matchedAnySnippet = true
                            Log.d(TAG, "Snippet Match: ${s.trigger}, variations: ${s.contents?.size ?: "null"}")
                            val variations = s.contents?.filter { it.isNotEmpty() } ?: emptyList()
                            if (variations.size > 1) {
                                // Show selection overlay
                                Log.d(TAG, "Showing selection overlay for '${s.trigger}' with ${variations.size} variations")
                                pendingTriggerRunnable?.let { debounceHandler.removeCallbacks(it) }
                                overlayManager.showSnippetSelection(s.trigger, variations, overlayThemeMode()) { selected ->
                                    Log.d(TAG, "Variation selected: '$selected'")
                                    if (!inputNode.refresh()) {
                                        Log.e(TAG, "Could not refresh input node for insertion")
                                        return@showSnippetSelection
                                    }
                                    val prefix = currentText.substring(0, idx)
                                    val suffix = currentText.substring(idx + fullTrigger.length)
                                    val newText = prefix + selected + suffix
                                    pasteText(inputNode, newText)
                                }
                                return
                            } else if (variations.isNotEmpty() || s.content.isNotEmpty()) {
                                val replacement = if (variations.isNotEmpty()) variations[0] else s.content
                                Log.d(TAG, "Single variation match. Scheduling auto-replacement for '${s.trigger}'")
                                val runnable = Runnable {
                                    if (!inputNode.refresh()) return@Runnable
                                    val prefix = currentText.substring(0, idx)
                                    val suffix = currentText.substring(idx + fullTrigger.length)
                                    val newText = prefix + replacement + suffix
                                    pasteText(inputNode, newText)
                                }
                                pendingTriggerRunnable = runnable
                                debounceHandler.postDelayed(runnable, config.triggerDebounceMs)
                                return
                            }
                        }
                    }
                }

                if (!matchedAnySnippet && !isSnippetSelectionVisible) {
                    if (overlayManager != null) { 
                         Log.d(TAG, "No snippet match in current text. Hiding selection overlay.")
                         overlayManager.hideSnippetSelection()
                    }
                }

                // Robust check for saveSnippetPattern
                if (saveSnippetPattern.split("%").size == 3 && saveSnippetPattern.length >= 5) {
                    val saveMatcher = Pattern.compile(buildSaveSnippetRegex(saveSnippetPattern)).matcher(currentText)
                    if (saveMatcher.find()) {
                        Log.d(TAG, "Save Snippet Match Found")
                        val fullMatch = saveMatcher.group(0) ?: ""
                        val newTrigger = saveMatcher.group(1)?.trim() ?: ""
                        val newContent = saveMatcher.group(2)?.trim() ?: ""

                        if (newTrigger.isNotEmpty() && newContent.isNotEmpty()) {
                            val existing = config.snippets.find { it.trigger == newTrigger }
                            if (existing != null) {
                                if (!existing.contents.contains(newContent)) {
                                    existing.contents.add(newContent)
                                }
                            } else {
                                config.snippets.add(com.typeassist.app.data.Snippet(newTrigger, contents = mutableListOf(newContent)))
                            }
                            configPrefs.edit().putString(
                                "config_json",
                                gson.toJson(Secrets.encryptConfig(config, secretStore::encrypt))
                            ).apply()
                            val cleanText = currentText.replace(fullMatch, newContent)
                            pasteText(inputNode, cleanText)
                            overlayManager.showToast("Snippet '$newTrigger' saved!")
                            return
                        }
                    }
                }

                // -- Utility Belt --
                findBalancedCommand(currentText, "(.c:")?.let { (fullMatch, expr) ->
                    val result = com.typeassist.app.utils.UtilityBelt.evaluateMath(expr)
                    originalTextCache = currentText
                    if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
                    lastNode = inputNode
                    undoCacheTimestamp = System.currentTimeMillis()
                    
                    val idx = currentText.lastIndexOf(fullMatch)
                    if (idx != -1) {
                        val prefix = currentText.substring(0, idx)
                        val suffix = currentText.substring(idx + fullMatch.length)
                        val newText = prefix + result + suffix
                        pasteText(inputNode, newText)
                    }
                    overlayManager.showUndoButton(config)
                    return
                }

                val utilityTriggers = mapOf(
                    ".now" to { com.typeassist.app.utils.UtilityBelt.getTime() },
                    ".date" to { com.typeassist.app.utils.UtilityBelt.getDate() },
                    ".pass" to { com.typeassist.app.utils.UtilityBelt.generatePassword() },
                    ".uuid" to { com.typeassist.app.utils.UtilityBelt.generateUuid() }
                )

                for ((uTrigger, uAction) in utilityTriggers) {
                    val idx = currentText.lastIndexOf(uTrigger)
                    if (idx != -1) {
                         val isAtEnd = idx + uTrigger.length == currentText.length
                         if (config.allowTriggerAnywhere || isAtEnd) {
                             val result = uAction()
                             originalTextCache = currentText
                             if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
                             lastNode = inputNode
                             undoCacheTimestamp = System.currentTimeMillis()

                             val prefix = currentText.substring(0, idx)
                             val suffix = currentText.substring(idx + uTrigger.length)
                             val newText = prefix + result + suffix

                             pasteText(inputNode, newText)
                             overlayManager.showUndoButton(config)
                             return
                         }
                    }
                }

                // -- Offline text tools: instant, provider-free, undoable like every other command --
                // .clip inserts the clipboard in place of the trigger.
                val clipIndex = currentText.lastIndexOf(CLIP_TRIGGER)
                if (clipIndex != -1 &&
                    (config.allowTriggerAnywhere || clipIndex + CLIP_TRIGGER.length == currentText.length)
                ) {
                    val clipboard = clipboardText()
                    if (clipboard.isNullOrEmpty()) {
                        overlayManager.showToast("The clipboard is empty")
                    } else {
                        originalTextCache = currentText
                        if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
                        lastNode = inputNode
                        undoCacheTimestamp = System.currentTimeMillis()
                        pasteText(
                            inputNode,
                            currentText.substring(0, clipIndex) + clipboard +
                                currentText.substring(clipIndex + CLIP_TRIGGER.length)
                        )
                        overlayManager.showUndoButton(config)
                    }
                    return
                }

                // .count reports the text before the trigger, then the case tools rewrite it.
                val countIndex = currentText.lastIndexOf(COUNT_TRIGGER)
                if (countIndex != -1 &&
                    (config.allowTriggerAnywhere || countIndex + COUNT_TRIGGER.length == currentText.length) &&
                    currentText.substring(0, countIndex).trim().isNotEmpty()
                ) {
                    originalTextCache = currentText
                    if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
                    lastNode = inputNode
                    undoCacheTimestamp = System.currentTimeMillis()
                    pasteText(
                        inputNode,
                        currentText.substring(0, countIndex) +
                            com.typeassist.app.utils.UtilityBelt.countSummary(currentText.substring(0, countIndex).trim()) +
                            currentText.substring(countIndex + COUNT_TRIGGER.length)
                    )
                    overlayManager.showUndoButton(config)
                    return
                }

                var textToolMatched = false
                for ((toolTrigger, toolAction) in TEXT_TOOLS) {
                    val toolIndex = currentText.lastIndexOf(toolTrigger)
                    if (toolIndex == -1) continue
                    val isAtEnd = toolIndex + toolTrigger.length == currentText.length
                    if (!config.allowTriggerAnywhere && !isAtEnd) continue
                    // A case tool with nothing to transform would wipe the field, so it stays put.
                    val target = currentText.substring(0, toolIndex).trim()
                    if (target.isEmpty()) continue
                    originalTextCache = currentText
                    if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
                    lastNode = inputNode
                    undoCacheTimestamp = System.currentTimeMillis()
                    pasteText(
                        inputNode,
                        toolAction(target) + currentText.substring(toolIndex + toolTrigger.length)
                    )
                    overlayManager.showUndoButton(config)
                    textToolMatched = true
                    break
                }
                if (textToolMatched) return

                val undoCommandPattern = config.undoCommandPattern.trim()
                val timeSinceCache = System.currentTimeMillis() - undoCacheTimestamp
                if (currentText.endsWith(undoCommandPattern) && originalTextCache.isNotEmpty() && timeSinceCache < 300000) {
                    pasteText(inputNode, originalTextCache)
                    return
                }
                
                // -- Screen-aware commands (.reply / .sum / .ta with @screen) --
                // Checked before the user's triggers so they win while screen context is on, and
                // skipped entirely when it is off: this feature is opt-in.
                if (maybeHandleScreenCommand(config, currentText, inputNode)) return

                val triggers = config.triggers
                val inlineCommands = config.inlineCommands

                // -- Process Inline Commands --
                for (inlineCommand in inlineCommands) {
                    val inlinePattern = inlineCommand.pattern
                    val inlinePromptTemplate = inlineCommand.prompt
                    if (inlinePattern.contains("%") && inlinePattern.length >= 3) {
                        val regexPattern = Pattern.compile(buildRegexFromInlinePattern(inlinePattern))
                        val matcher = regexPattern.matcher(currentText)

                        if (matcher.find()) {
                            val fullMatchedString = matcher.group(0) ?: continue
                            val userPrompt = matcher.group(1) ?: continue
                            Log.d(TAG, "Inline Command Match: $inlinePattern | User Prompt: $userPrompt")

                            originalTextCache = currentText
                            if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
                            lastNode = inputNode
                            undoCacheTimestamp = System.currentTimeMillis()

                            executeAiCommand(config, inlinePromptTemplate, userPrompt, inputNode, currentText) { aiText ->
                                // Replace only the matched inline command. A literal replacement (the
                                // String overload) keeps dollar signs and backslashes in the answer
                                // intact, unlike a regex replacement.
                                currentText.replaceFirst(fullMatchedString, aiText)
                            }
                            return
                        }
                    }
                }

                // -- Process Trailing Triggers (Debounced) --
                for (trigger in triggers) {
                    val pattern = trigger.pattern
                    val prompt = trigger.prompt

                    val triggerIndex = findTriggerIndex(currentText, pattern, config.allowTriggerAnywhere, config.ignorePrecedingWhitespace)
                    if (triggerIndex != -1) {
                        val textToProcess = currentText.substring(0, triggerIndex).trim()
                        Log.d(TAG, "Trigger Match: $pattern | Input Text: $textToProcess")

                        val suffix = if (currentText.length > triggerIndex + pattern.length) {
                             currentText.substring(triggerIndex + pattern.length).trim()
                        } else { "" }

                        if (textToProcess.length > 1) {
                            // Cancel any pending trigger so only the latest one fires.
                            pendingTriggerRunnable?.let { debounceHandler.removeCallbacks(it) }
                            val runnable = Runnable {
                                if (!inputNode.refresh()) return@Runnable
                                originalTextCache = textToProcess
                                lastNode = inputNode
                                undoCacheTimestamp = System.currentTimeMillis()
                                if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)

                                executeAiCommand(config, prompt, textToProcess, inputNode, textToProcess) { aiText ->
                                    if (suffix.isNotEmpty()) aiText + " " + suffix else aiText
                                }
                            }
                            pendingTriggerRunnable = runnable
                            debounceHandler.post(runnable)
                        }
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onAccessibilityEvent", e)
            }
        }
    }

    private fun processAiResult(config: AppConfig, node: AccessibilityNodeInfo, aiText: String, render: (String) -> String) {
        val cleanedText = cleanAiText(aiText)

        if (cleanedText.isBlank()) {
            Log.w(TAG, "AI returned empty result after cleaning. Raw length=${aiText.length}")
            overlayManager.showToast("Model returned empty output — try a smarter model or increase max tokens")
            return
        }

        val wordCount = cleanedText.split("\\s+".toRegex()).size

        if (wordCount > 15 && config.enablePreviewDialog) {
            overlayManager.showPreviewDialog(cleanedText, overlayThemeMode()) {
                pasteText(node, render(cleanedText), moveCursorToEnd = true)
                overlayManager.showUndoButton(config)
                showResultChipFor(config)
            }
        } else {
            pasteText(node, render(cleanedText), moveCursorToEnd = true)
            overlayManager.showUndoButton(config)
            showResultChipFor(config)
        }
    }

    /**
     * Runs one AI command and writes the answer into [node] through [render], which receives the
     * model text and returns the complete field content for the command that is running (a trailing
     * trigger keeps the text that followed it, an inline command replaces only its own match, and a
     * global rewrite replaces everything).
     *
     * Uses streaming when the provider supports it and the setting is on, and falls back to a single
     * request otherwise.
     */
    private fun executeAiCommand(
        config: AppConfig,
        prompt: String,
        userText: String,
        node: AccessibilityNodeInfo?,
        originalFieldText: String,
        image: PromptImage? = null,
        render: (String) -> String
    ) {
        // Every AI request passes through here, so both privacy rules are checked in one place:
        // a password field, or an app on the user's "never touch this app" list. The offline
        // tools are not affected by the blocklist - they never leave the phone.
        val refusal = CommandGuard.refusal(
            isPassword = node?.isPassword == true,
            blockedPackages = config.screenContextBlockedPackages,
            packageName = node?.packageName?.toString()
        )
        if (refusal != CommandGuard.Refusal.NONE) {
            notifyRefusalOnce(refusal)
            return
        }

        lastCommand = PendingAiCommand(prompt, userText, render, originalFieldText, image)
        overlayManager.showLoading(config)
        overlayManager.hideUndoButton()
        overlayManager.hideResultChip()
        // Remember that a request is now in flight, so process death during it can be reported.
        streamRecovery.savePendingStream(
            provider = config.provider,
            prompt = prompt,
            userText = userText,
            hasImage = image != null
        )

        val provider = providerFor(config)
        val generation = ++streamGeneration
        val stream = if (config.streamResponses && node != null) {
            try {
                provider.streamResponse(
                    prompt = prompt,
                    userText = userText,
                    config = config,
                    image = image,
                    onDelta = { delta -> onStreamDelta(generation, node, render, delta) },
                    callback = { result ->
                        onStreamFinished(generation, config, node, render, originalFieldText, result)
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Streaming request could not be started: ${e.message}")
                null
            }
        } else null

        if (stream != null) {
            activeStream = stream
            activeStreamNode = node
            activeStreamPreText = originalFieldText
            streamDeltaCount = 0
            streamThrottle.reset()
            // Nothing to accept yet: while the answer is still arriving the chip is a Stop button.
            if (config.showResultChip) {
                overlayManager.showResultChip(
                    config = config,
                    actions = listOf(OverlayManager.ChipAction("Stop") { cancelActiveStream(restoreOriginal = true) }),
                    themeMode = overlayThemeMode(),
                    autoDismissMs = null
                )
            }
            return
        }

        runNonStreamingCommand(config, prompt, userText, node, image, render)
    }

    private fun runNonStreamingCommand(
        config: AppConfig,
        prompt: String,
        userText: String,
        node: AccessibilityNodeInfo?,
        image: PromptImage?,
        render: (String) -> String
    ) {
        performAICall(config, prompt, userText, image) { result ->
            uiHandler.post {
            overlayManager.hideLoading()
            streamRecovery.clearPendingStream()
            result.onSuccess { aiText ->
                Log.d(TAG, "AI Success: ${aiText.take(50)}...")
                    recordUsage(config, lastCommand?.prompt.orEmpty(), lastCommand?.userText.orEmpty(), aiText)
                    if (node != null && node.refresh()) {
                        processAiResult(config, node, aiText, render)
                    } else {
                        overlayManager.showToast("The text field is no longer available")
                    }
                }.onFailure {
                    Log.e(TAG, "AI Failure: ${it.message}")
                    overlayManager.showToast(ApiErrorFormatter.explain(it))
                }
            }
        }
    }

    private fun providerFor(config: AppConfig): AiProvider = when (config.provider) {
        "cloudflare" -> cloudflareApiClient
        "custom" -> customApiClient
        "local" -> localLlmClient
        else -> geminiApiClient
    }

    /**
     * Whether the active provider can read an image. Gemini and OpenAI-compatible endpoints do;
     * Workers AI text models and the on-device llama.cpp models do not, so no screenshot is made
     * for them (which would only waste time and privacy).
     */
    private fun providerSupportsImages(config: AppConfig): Boolean =
        config.provider == "gemini" || config.provider == "custom"

    /**
     * Counts what one finished command used, for the token-usage tab. The provider's own numbers are
     * used when it reported them; otherwise the tracker estimates from the text that was sent and
     * received. Only counters are stored — never the text.
     */
    private fun recordUsage(config: AppConfig, prompt: String, userText: String, answer: String) {
        try {
            UsageTracker.record(
                provider = config.provider,
                model = usageModelName(config),
                promptText = prompt + "\n" + userText,
                answerText = answer,
                reported = providerFor(config).reportedUsage()
            )
            UsageStore(this).save(UsageTracker.dump())
        } catch (e: Exception) {
            // Counting is never worth failing a command over.
            Log.w(TAG, "Could not record token usage: ${e.message}")
        }
    }

    /** The model ID the usage rows are grouped under, per provider. */
    private fun usageModelName(config: AppConfig): String = when (config.provider) {
        "custom" -> config.customApiConfig.model
        "cloudflare" -> config.cloudflareConfig.model
        "local" -> config.localLlmConfig.modelPath
        else -> config.model
    }

    // --- Screen-aware commands ----------------------------------------------------------------

    /**
     * Handles the commands that read the screen: `.reply`, `.sum` and `.ta` when the prompt
     * contains "@screen". Returns true when the text was consumed (the command ran, was refused, or
     * the screen could not be read), false when the text should be handled as a normal command.
     */
    private fun maybeHandleScreenCommand(
        config: AppConfig,
        currentText: String,
        inputNode: AccessibilityNodeInfo
    ): Boolean {
        val replyIndex = trailingCommandIndex(currentText, SCREEN_REPLY_TRIGGER, config)
        val summaryIndex = trailingCommandIndex(currentText, SCREEN_SUMMARY_TRIGGER, config)
        val askIndex = trailingCommandIndex(currentText, SCREEN_ASK_TRIGGER, config)
        val asksAboutScreen = askIndex >= 0 && currentText.contains(SCREEN_ASK_MARKER, ignoreCase = true)

        val instruction: String
        var prompt: String
        // For .reply the instruction is part of the system prompt (it has priority over the tone
        // rules), so it is not repeated in the user turn.
        var instructionCarriedByPrompt = false
        val now = ScreenPrompts.currentTimeLine(TimeZone.getDefault(), System.currentTimeMillis())
        when {
            replyIndex >= 0 -> {
                instruction = currentText.substring(0, replyIndex).trim()
                prompt = ScreenPrompts.reply(config, instruction, now = now)
                instructionCarriedByPrompt = true
            }
            summaryIndex >= 0 -> {
                instruction = currentText.substring(0, summaryIndex).trim()
                prompt = ScreenPrompts.summary(config, now = now)
            }
            asksAboutScreen -> {
                instruction = currentText.substring(0, askIndex)
                    .replace(SCREEN_ASK_MARKER, "", ignoreCase = true)
                    .trim()
                prompt = ScreenPrompts.answer(config, now = now)
            }
            else -> return false
        }

        if (!config.screenContextEnabled) {
            // Asking the screen a question must never be sent as literal text; the plain .reply and
            // .sum shortcuts stay out of the way so a user-defined command with the same trigger
            // keeps working.
            if (asksAboutScreen) {
                overlayManager.showToast("Screen context is off. Turn it on in Settings → Screen context.")
                return true
            }
            return false
        }

        val reader = ScreenContextReader(this)
        val foreground = reader.foregroundPackage()
        if (ScreenTextExtractor.isPackageBlocked(config.screenContextBlockedPackages, foreground)) {
            overlayManager.showToast("Screen reading is disabled for this app.")
            return true
        }

        val screenContext = reader.readScreenContext(config)
        if (screenContext.isNullOrBlank()) {
            overlayManager.showToast("Could not read this screen")
            return true
        }

        // .reply is rewritten with the user's own messages quoted back at the model: the rules say
        // "copy the Me: lines", and a short sample of them makes that instruction concrete.
        val voiceSample = ScreenPrompts.voiceSample(screenContext)
        if (replyIndex >= 0) {
            prompt = ScreenPrompts.reply(config, instruction, voiceSample, now)
        }

        val userText = buildString {
            append(screenContext)
            if (instruction.isNotBlank() && !instructionCarriedByPrompt) {
                append("\n\n")
                append("Additional instruction: ")
                append(instruction)
            }
        }

        // A screenshot is only meaningful for commands that describe the conversation on screen, and
        // only for providers that can actually read an image: Cloudflare and the local model cannot.
        val wantsImage = config.screenImageEnabled &&
            (replyIndex >= 0 || summaryIndex >= 0) &&
            providerSupportsImages(config)

        fun run(image: PromptImage?) {
            // With an image the prompt is rebuilt so the model is told to read the screenshot too.
            val effectivePrompt = if (image == null) {
                prompt
            } else {
                when {
                    replyIndex >= 0 -> ScreenPrompts.reply(config, instruction, voiceSample, now, imageAttached = true)
                    summaryIndex >= 0 -> ScreenPrompts.summary(config, now, imageAttached = true)
                    else -> prompt
                }
            }
            originalTextCache = currentText
            if (config.isHistoryEnabled) HistoryManager.add(originalTextCache, config.historyRetentionMinutes)
            lastNode = inputNode
            undoCacheTimestamp = System.currentTimeMillis()
            // The answer replaces what the user typed, like every other command.
            executeAiCommand(config, effectivePrompt, userText, inputNode, currentText, image) { aiText -> aiText }
        }

        if (wantsImage) {
            if (!ScreenImageCapture.isSupported()) {
                overlayManager.showToast("Screen images need Android 11 or newer")
                run(null)
                return true
            }
            ScreenImageCapture.capture(this) { image ->
                if (image == null) overlayManager.showToast("Could not capture the screen image")
                run(image)
            }
            return true
        }

        run(null)
        return true
    }

    /** Index of a command typed at the end of the field, honouring the trigger settings. */
    private fun trailingCommandIndex(text: String, command: String, config: AppConfig): Int =
        findTriggerIndex(text, command, config.allowTriggerAnywhere, config.ignorePrecedingWhitespace)

    // --- Streaming ---------------------------------------------------------------------------

    private fun onStreamDelta(
        generation: Int,
        node: AccessibilityNodeInfo,
        render: (String) -> String,
        delta: String
    ) {
        if (generation != streamGeneration) return
        streamDeltaCount++
        streamBuffer.append(delta)
        // ~10 updates per second is fast enough to look live and cheap enough not to fight the
        // keyboard: the field is only rewritten when the throttle lets it through.
        if (!streamThrottle.shouldEmit()) return
        val partial = streamBuffer.toString()
        uiHandler.post { applyStreamedText(node, render(partial)) }
    }

    private fun onStreamFinished(
        generation: Int,
        config: AppConfig,
        node: AccessibilityNodeInfo,
        render: (String) -> String,
        originalFieldText: String,
        result: Result<String>
    ) {
        uiHandler.post {
            if (generation != streamGeneration) return@post // cancelled or replaced already
            activeStream = null
            activeStreamNode = null
            activeStreamPreText = null
            streamRecovery.clearPendingStream()
            overlayManager.hideLoading()
            overlayManager.hideResultChip()

            result.onSuccess { fullText ->
                streamBuffer.setLength(0)
                recordUsage(config, lastCommand?.prompt.orEmpty(), lastCommand?.userText.orEmpty(), fullText)
                if (node.refresh()) {
                    processAiResult(config, node, fullText, render)
                } else {
                    overlayManager.showToast("The text field is no longer available")
                }
            }.onFailure { error ->
                val streamedSomething = streamDeltaCount > 0
                streamDeltaCount = 0
                if (streamedSomething) {
                    // Part of the answer was already visible: put the original text back and say why.
                    restoreField(node, originalFieldText)
                    Log.e(TAG, "Stream failed mid-answer: ${error.message}")
                    overlayManager.showToast(ApiErrorFormatter.explain(error))
                } else {
                    // Nothing arrived, so the provider probably does not stream: ask normally.
                    Log.d(TAG, "Streaming unavailable (${error.message}); falling back to a single request")
                    runNonStreamingCommand(config, lastCommand?.prompt.orEmpty(), lastCommand?.userText.orEmpty(), node, lastCommand?.image, render)
                }
            }
        }
    }

    private fun applyStreamedText(node: AccessibilityNodeInfo, newText: String) {
        try {
            if (!node.refresh()) return
            pasteText(node, newText, moveCursorToEnd = true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not update the field mid-stream: ${e.message}")
        }
    }

    /** Aborts the in-flight stream (if any) and optionally puts the original text back. */
    private fun cancelActiveStream(restoreOriginal: Boolean, message: String? = null) {
        val stream = activeStream ?: return
        activeStream = null
        val node = activeStreamNode
        val original = activeStreamPreText
        activeStreamNode = null
        activeStreamPreText = null
        streamDeltaCount = 0
        streamGeneration++
        streamRecovery.clearPendingStream()
        try { stream.cancel() } catch (e: Exception) { /* already gone */ }
        if (restoreOriginal && node != null && original != null) {
            restoreField(node, original)
        }
        overlayManager.hideLoading()
        overlayManager.hideResultChip()
        if (message != null) overlayManager.showToast(message)
    }

    /** The user typed or moved to another field: stop streaming and hide the chip. */
    private fun onUserInterruption() {
        cancelActiveStream(restoreOriginal = true)
        overlayManager.hideResultChip()
    }

    private fun restoreField(node: AccessibilityNodeInfo, text: String) {
        try {
            if (node.refresh()) pasteText(node, text, moveCursorToEnd = true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore the original text: ${e.message}")
        }
    }

    private fun showResultChipFor(config: AppConfig) {
        if (!config.showResultChip) return
        overlayManager.showResultChip(
            config = config,
            actions = listOf(
                OverlayManager.ChipAction("Accept") { overlayManager.hideResultChip() },
                OverlayManager.ChipAction("Reject") {
                    overlayManager.hideResultChip()
                    performUndo()
                },
                OverlayManager.ChipAction("Retry") {
                    overlayManager.hideResultChip()
                    retryLastCommand(config)
                }
            ),
            themeMode = overlayThemeMode()
        )
    }

    /** Re-runs the command that produced the current result, from the original text. */
    private fun retryLastCommand(config: AppConfig) {
        val command = lastCommand ?: return
        val node = lastNode?.takeIf { it.refresh() }
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return
        originalTextCache = command.originalFieldText
        lastNode = node
        undoCacheTimestamp = System.currentTimeMillis()
        executeAiCommand(config, command.prompt, command.userText, node, command.originalFieldText, command.image, command.render)
    }

    /** True when the text is exactly what we last wrote ourselves, so it must not be re-processed. */
    private fun isOwnEcho(text: String): Boolean {
        val last = lastAppliedText ?: return false
        if (text != last) return false
        return System.currentTimeMillis() - lastAppliedAt < ECHO_WINDOW_MS
    }

    /** The first clipboard item as text, or null when the clipboard is empty or unreadable. */
    private fun clipboardText(): String? = try {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip?.getItemAt(0)?.coerceToText(applicationContext)?.toString()
    } catch (e: Exception) {
        null
    }

    private fun performAICall(
        config: AppConfig,
        prompt: String,
        userText: String,
        image: PromptImage?,
        callback: (Result<String>) -> Unit
    ) {
        Log.d(TAG, "Performing AI Call: Provider=${config.provider}, Timeout=${config.apiTimeoutSeconds}s")
        providerFor(config).generateResponse(prompt, userText, config, image, callback)
    }

    private fun cleanAiText(text: String): String {
        var result = text.trim()

        // Remove surrounding pipe signs if present
        if (result.startsWith("|") && result.endsWith("|")) {
            result = result.substring(1, result.length - 1).trim()
        }

        // Remove surrounding quotes if present
        if ((result.startsWith("\"") && result.endsWith("\"")) ||
            (result.startsWith("'") && result.endsWith("'"))) {
            result = result.substring(1, result.length - 1).trim()
        }

        return result
    }

    private fun findTriggerIndex(text: String, trigger: String, allowAnywhere: Boolean, ignoreWhitespace: Boolean): Int {
        if (!allowAnywhere) {
            val trimmedEnd = text.trimEnd()
            if (!trimmedEnd.endsWith(trigger)) return -1
            return trimmedEnd.length - trigger.length
        }

        var idx = text.lastIndexOf(trigger)
        while (idx != -1) {
            val startOk = ignoreWhitespace || (idx == 0) || text[idx - 1].isWhitespace()
            val endIdx = idx + trigger.length
            val endOk = (endIdx == text.length) || text[endIdx].isWhitespace()

            if (startOk && endOk) return idx

            idx = text.lastIndexOf(trigger, idx - 1)
        }
        return -1
    }

    private fun performUndo() {
        val timeSinceCache = System.currentTimeMillis() - undoCacheTimestamp
        if (lastNode != null && originalTextCache.isNotEmpty() && timeSinceCache < 300000) {
            if (lastNode!!.refresh()) {
                pasteText(lastNode!!, originalTextCache)
                overlayManager.showToast("Undone!")
            }
        }
        overlayManager.hideUndoButton()
    }

    private fun pasteText(node: AccessibilityNodeInfo, text: String, moveCursorToEnd: Boolean = false) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        // Remember what we wrote so the text-changed event it triggers is recognised as our own
        // echo instead of the user typing (which would cancel a stream or hide the chip).
        lastAppliedText = text
        lastAppliedAt = System.currentTimeMillis()

        if (moveCursorToEnd && text.isNotEmpty()) {
            val selection = Bundle()
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
            try { node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection) } catch (e: Exception) { }
        }
    }

    private fun buildRegexFromInlinePattern(inlinePattern: String): String {
        return Pattern.quote(inlinePattern).replace("%", "\\E(.+?)\\Q").replace("\\Q\\E", "")
    }

    private fun buildSaveSnippetRegex(pattern: String): String {
        val parts = pattern.split("%", limit = 3)
        if (parts.size != 3) return Pattern.quote(pattern)
        return Pattern.quote(parts[0]) + "(.+?)" + Pattern.quote(parts[1]) + "(.+?)" + Pattern.quote(parts[2])
    }

    private fun findBalancedCommand(text: String, startPattern: String): Pair<String, String>? {
        val startIndex = text.lastIndexOf(startPattern)
        if (startIndex == -1) return null
        val contentStartIndex = startIndex + startPattern.length
        var balance = 0
        var endIndex = -1
        for (i in contentStartIndex until text.length) {
            when (text[i]) {
                '(' -> balance++
                ')' -> if (balance == 0) { endIndex = i; break } else balance--
            }
        }
        if (endIndex != -1) return Pair(text.substring(startIndex, endIndex + 1), text.substring(contentStartIndex, endIndex))
        return null
    }

    private fun buildGlobalTriggerRegex(pattern: String): String {
        val parts = pattern.split("%", limit = 2)
        if (parts.size != 2) return "(?s)(.*)\\Q${pattern}\\E\\s*$"
        return "(?s)(.*)" + Pattern.quote(parts[0]) + "(.+?)" + Pattern.quote(parts[1]) + "\\s*$"
    }

    override fun onInterrupt() {
        cancelActiveStream(restoreOriginal = false)
        overlayManager.hideAll()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelActiveStream(restoreOriginal = false)
        unloadModel()
        if (::overlayManager.isInitialized) {
            overlayManager.hideAll()
        }
    }
}
