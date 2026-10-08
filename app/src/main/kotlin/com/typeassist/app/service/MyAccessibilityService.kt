package com.typeassist.app.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.typeassist.app.api.LocalLlmClient
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.ConfigMigrations
import com.typeassist.app.data.HistoryManager
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.data.StreamThrottle
import com.typeassist.app.ui.ThemePalettes
import com.typeassist.app.utils.ScreenTextExtractor
import okhttp3.*
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

        private const val SCREEN_REPLY_PROMPT =
            "You are drafting a reply in an ongoing conversation for the user. The screen text is " +
                "provided. Lines starting with Me: were written by the user, and lines starting with " +
                "Them: were written by the other person. Copy the user's own writing style from the " +
                "Me: lines: their language, vocabulary, sentence length, punctuation, capitalisation, " +
                "emoji use and formality. If there are no Me: lines, keep the reply short and plain and " +
                "match the conversation's language and tone. Do not use exaggerated, flowery or robotic " +
                "phrasing (for example \"I hope this finds you well\", \"absolutely delighted\" or " +
                "\"delve into\"), and do not over-explain. Write only the reply message, with no quotes, " +
                "labels or explanations."

        private const val SCREEN_SUMMARY_PROMPT =
            "Summarize the content on the screen into a few short bullet points. Keep only the " +
                "important information. Return only the summary."

        private const val SCREEN_ANSWER_PROMPT =
            "Answer the user's question using only the screen content provided. Be concise and " +
                "return only the answer."
    }

    external fun stringFromJNI(): String
    external fun loadModel(path: String, useGpu: Boolean): Boolean
    external fun generateResponseNative(prompt: String, temp: Float, topP: Float, maxTokens: Int): String
    external fun stopGenerationNative()
    external fun unloadModel()

    private val client = OkHttpClient()
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
        val originalFieldText: String
    )

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
        Log.d(TAG, "Service Connected")
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
            getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE).getString("theme_mode", null)
        } catch (e: Exception) {
            null
        }
        return ThemePalettes.resolveMode(stored, systemIsDark())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event == null) return

        if (event.packageName?.toString() == packageName) {
            val prefs = getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)
            val isTesting = prefs.getBoolean("is_testing_active", false)
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

            val prefs = getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)
            val configJson = prefs.getString("config_json", null)
            if (configJson == null) {
                Log.e(TAG, "config_json is null!")
                return
            }

            try {
                val gson = com.google.gson.GsonBuilder().create()
                // Gson skips constructors, so fields that did not exist when the config was saved
                // arrive as zero values. Restore their defaults before anything reads them.
                val config = ConfigMigrations.apply(
                    LoadingIndicatorStyle.sanitize(gson.fromJson(configJson, AppConfig::class.java))
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
                            if (config.isHistoryEnabled) HistoryManager.add(originalTextCache)
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
                            prefs.edit().putString("config_json", gson.toJson(config)).apply()
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
                    if (config.isHistoryEnabled) HistoryManager.add(originalTextCache)
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
                    ".pass" to { com.typeassist.app.utils.UtilityBelt.generatePassword() }
                )

                for ((uTrigger, uAction) in utilityTriggers) {
                    val idx = currentText.lastIndexOf(uTrigger)
                    if (idx != -1) {
                         val isAtEnd = idx + uTrigger.length == currentText.length
                         if (config.allowTriggerAnywhere || isAtEnd) {
                             val result = uAction()
                             originalTextCache = currentText
                             if (config.isHistoryEnabled) HistoryManager.add(originalTextCache)
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
                            if (config.isHistoryEnabled) HistoryManager.add(originalTextCache)
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
                             currentText.substring(triggerIndex + pattern.length)
                        } else { "" }
                        
                        if (textToProcess.length > 1) {
                            val runnable = Runnable {
                                if (!inputNode.refresh()) return@Runnable
                                // Same text .undo restores: what the user wrote before the trigger.
                                originalTextCache = textToProcess
                                lastNode = inputNode
                                undoCacheTimestamp = System.currentTimeMillis()
                                if (config.isHistoryEnabled) HistoryManager.add(originalTextCache)

                                executeAiCommand(config, prompt, textToProcess, inputNode, textToProcess) { aiText ->
                                    // Keep whatever the user wrote after the trigger.
                                    aiText + suffix
                                }
                            }
                            pendingTriggerRunnable = runnable
                            debounceHandler.postDelayed(runnable, config.triggerDebounceMs)
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
        render: (String) -> String
    ) {
        lastCommand = PendingAiCommand(prompt, userText, render, originalFieldText)
        overlayManager.showLoading(config)
        overlayManager.hideUndoButton()
        overlayManager.hideResultChip()

        val provider = providerFor(config)
        val generation = ++streamGeneration
        val stream = if (config.streamResponses && node != null) {
            try {
                provider.streamResponse(
                    prompt = prompt,
                    userText = userText,
                    config = config,
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

        runNonStreamingCommand(config, prompt, userText, node, render)
    }

    private fun runNonStreamingCommand(
        config: AppConfig,
        prompt: String,
        userText: String,
        node: AccessibilityNodeInfo?,
        render: (String) -> String
    ) {
        performAICall(config, prompt, userText) { result ->
            uiHandler.post {
                overlayManager.hideLoading()
                result.onSuccess { aiText ->
                    Log.d(TAG, "AI Success: ${aiText.take(50)}...")
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
        val prompt: String
        when {
            replyIndex >= 0 -> {
                instruction = currentText.substring(0, replyIndex).trim()
                prompt = SCREEN_REPLY_PROMPT
            }
            summaryIndex >= 0 -> {
                instruction = currentText.substring(0, summaryIndex).trim()
                prompt = SCREEN_SUMMARY_PROMPT
            }
            asksAboutScreen -> {
                instruction = currentText.substring(0, askIndex)
                    .replace(SCREEN_ASK_MARKER, "", ignoreCase = true)
                    .trim()
                prompt = SCREEN_ANSWER_PROMPT
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

        val userText = buildString {
            append(screenContext)
            if (instruction.isNotBlank()) {
                append("\n\n")
                append("Additional instruction: ")
                append(instruction)
            }
        }

        originalTextCache = currentText
        if (config.isHistoryEnabled) HistoryManager.add(originalTextCache)
        lastNode = inputNode
        undoCacheTimestamp = System.currentTimeMillis()

        // The answer replaces what the user typed, like every other command.
        executeAiCommand(config, prompt, userText, inputNode, currentText) { aiText -> aiText }
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
            overlayManager.hideLoading()
            overlayManager.hideResultChip()

            result.onSuccess { fullText ->
                streamBuffer.setLength(0)
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
                    runNonStreamingCommand(config, lastCommand?.prompt.orEmpty(), lastCommand?.userText.orEmpty(), node, render)
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
        executeAiCommand(config, command.prompt, command.userText, node, command.originalFieldText, command.render)
    }

    /** True when the text is exactly what we last wrote ourselves, so it must not be re-processed. */
    private fun isOwnEcho(text: String): Boolean {
        val last = lastAppliedText ?: return false
        if (text != last) return false
        return System.currentTimeMillis() - lastAppliedAt < ECHO_WINDOW_MS
    }

    private fun performAICall(config: AppConfig, prompt: String, userText: String, callback: (Result<String>) -> Unit) {
        Log.d(TAG, "Performing AI Call: Provider=${config.provider}, Timeout=${config.apiTimeoutSeconds}s")
        providerFor(config).generateResponse(prompt, userText, config, callback)
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
            if (!text.endsWith(trigger)) return -1
            val triggerStartIndex = text.length - trigger.length
            if (!ignoreWhitespace && triggerStartIndex > 0 && !text[triggerStartIndex - 1].isWhitespace()) return -1
            return triggerStartIndex
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
                ')' -> { if (balance == 0) { endIndex = i; break }; balance-- }
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
