package com.typeassist.app.service

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.LoadingIndicatorStyle
import com.typeassist.app.ui.OverlayColors
import com.typeassist.app.ui.ThemePalettes
import kotlin.math.cos
import kotlin.math.sin

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var loadingView: View? = null
    private var undoView: FrameLayout? = null
    private var previewView: FrameLayout? = null 
    private val loadingAnimators = mutableListOf<Animator>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideUndoRunnable = Runnable { hideUndoButton() }
    private val hidePreviewRunnable = Runnable { hidePreviewDialog() }
    
    var onUndoAction: (() -> Unit)? = null
    var onOverlayShown: (() -> Unit)? = null
    var onOverlayHidden: (() -> Unit)? = null

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density

    /** Colour of the loading indicator, from Settings -> Indicator colour. */
    private var indicatorColor: Int = LoadingIndicatorStyle.DEFAULT_COLOR

    /** Size multiplier of the loading indicator, from Settings -> Indicator size. */
    private var indicatorScale: Float = 1f

    /** Density-independent size, additionally multiplied by the user's indicator size. */
    private fun sdp(v: Int): Int =
        (v * indicatorScale * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    /** Same as [sdp] but keeps the fractional part, for stroke widths and corner radii. */
    private fun sdpF(v: Float): Float = v * indicatorScale * context.resources.displayMetrics.density

    fun showLoading(config: AppConfig) {
        if (!config.enableLoadingOverlay) return
        mainHandler.post {
            if (loadingView != null) return@post
            clearLoadingAnimators()
            val rawStyle = try { config.loadingIndicatorStyle } catch (_: Exception) { "classic" }
            val style = (rawStyle as? String ?: "classic").ifBlank { "classic" }
            indicatorColor = LoadingIndicatorStyle.effectiveColor(style, config.loadingIndicatorColor)
            indicatorScale = LoadingIndicatorStyle.scaleOf(config.loadingIndicatorSizePercent)
            val content = when (style) {
                "dots" -> createDotsView()
                "pulse" -> createPulseView()
                "bars" -> createBarsView()
                "typing" -> createTypingView()
                "pill" -> createPillView()
                "neon" -> createNeonRingView()
                else -> createClassicView()
            }
            loadingView = content
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            try { windowManager?.addView(loadingView, params) } catch (e: Exception) {}
        }
    }

    fun hideLoading() {
        mainHandler.post {
            clearLoadingAnimators()
            if (loadingView != null) {
                try { windowManager?.removeView(loadingView); loadingView = null } catch (e: Exception) {}
            }
        }
    }

    private fun clearLoadingAnimators() {
        loadingAnimators.forEach { try { it.cancel() } catch (_: Exception) {} }
        loadingAnimators.clear()
    }

    private fun circleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun dotWithShadow(color: Int, withShadow: Boolean = true): View {
        return View(context).apply {
            background = circleDrawable(color)
            if (withShadow) {
                elevation = dpF(3f)
            }
        }
    }

    // No background - immersive, just floating indicator
    private fun createClassicView(): View {
        val container = FrameLayout(context).apply {
            setPadding(sdp(8), sdp(8), sdp(8), sdp(8))
            // No background - transparent
        }
        val progressBar = ProgressBar(context).apply {
            // 48dp is the platform default, so the default size looks exactly like before.
            layoutParams = FrameLayout.LayoutParams(sdp(48), sdp(48))
            indeterminateTintList = android.content.res.ColorStateList.valueOf(indicatorColor)
            // Add subtle shadow via elevation on parent? ProgressBar itself
            elevation = dpF(4f)
        }
        container.addView(progressBar)
        return container
    }

    private fun createDotsView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(sdp(4), sdp(4), sdp(4), sdp(4))
            // No background - immersive
        }
        repeat(3) { index ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(sdp(10), sdp(10)).apply {
                    setMargins(sdp(4), 0, sdp(4), 0)
                }
                background = circleDrawable(indicatorColor)
                elevation = dpF(6f)
            }
            container.addView(dot)
            val anim = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.3f, 1f).apply {
                duration = 600
                startDelay = (index * 180L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            loadingAnimators.add(anim)
            val scaleX = ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.7f, 1.2f).apply {
                duration = 600
                startDelay = (index * 180L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
            val scaleY = ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.7f, 1.2f).apply {
                duration = 600
                startDelay = (index * 180L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
            loadingAnimators.add(scaleX)
            loadingAnimators.add(scaleY)
        }
        return container
    }

    private fun createPulseView(): View {
        val container = FrameLayout(context).apply {
            setPadding(sdp(4), sdp(4), sdp(4), sdp(4))
        }
        val outer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(sdp(48), sdp(48), Gravity.CENTER)
            background = circleDrawable(LoadingIndicatorStyle.withAlpha(indicatorColor, 0x33))
        }
        val inner = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(sdp(20), sdp(20), Gravity.CENTER)
            background = circleDrawable(indicatorColor)
            elevation = dpF(8f)
        }
        container.addView(outer)
        container.addView(inner)

        val pulseScaleX = ObjectAnimator.ofFloat(inner, View.SCALE_X, 0.85f, 1.4f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val pulseScaleY = ObjectAnimator.ofFloat(inner, View.SCALE_Y, 0.85f, 1.4f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val outerScaleX = ObjectAnimator.ofFloat(outer, View.SCALE_X, 0.9f, 1.6f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        val outerScaleY = ObjectAnimator.ofFloat(outer, View.SCALE_Y, 0.9f, 1.6f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        val outerAlpha = ObjectAnimator.ofFloat(outer, View.ALPHA, 0.8f, 0.15f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        loadingAnimators.addAll(listOf(pulseScaleX, pulseScaleY, outerScaleX, outerScaleY, outerAlpha))
        return container
    }

    private fun createBarsView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(sdp(4), sdp(4), sdp(4), sdp(4))
        }
        repeat(3) { index ->
            val bar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(sdp(5), sdp(18)).apply {
                    setMargins(sdp(3), 0, sdp(3), 0)
                    gravity = Gravity.BOTTOM
                }
                background = GradientDrawable().apply {
                    setColor(indicatorColor)
                    cornerRadius = sdpF(2.5f)
                }
                elevation = dpF(4f)
            }
            container.addView(bar)
            val anim = ObjectAnimator.ofFloat(bar, View.SCALE_Y, 0.35f, 1.5f).apply {
                duration = 500
                startDelay = (index * 150L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            loadingAnimators.add(anim)
        }
        return container
    }

    private fun createTypingView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(sdp(6), sdp(6), sdp(6), sdp(6))
        }
        repeat(3) { index ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(sdp(8), sdp(8)).apply {
                    setMargins(sdp(3), 0, sdp(3), 0)
                }
                background = circleDrawable(indicatorColor)
                elevation = dpF(5f)
            }
            container.addView(dot)
            val anim = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -sdpF(7f)).apply {
                duration = 380
                startDelay = (index * 120L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            loadingAnimators.add(anim)
        }
        return container
    }

    private fun createPillView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(sdp(4), sdp(4), sdp(4), sdp(4))
        }
        val progress = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(sdp(16), sdp(16)).apply {
                setMargins(0, 0, sdp(8), 0)
            }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(indicatorColor)
            elevation = dpF(4f)
        }
        val text = TextView(context).apply {
            this.text = "Thinking..."
            textSize = 12f * indicatorScale
            setTextColor(indicatorColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setShadowLayer(dpF(4f), 0f, 0f, Color.BLACK)
        }
        container.addView(progress)
        container.addView(text)
        return container
    }

    /**
     * Neon ring: a glowing tube of light that sweeps around a dim track, with a bright leading
     * head and a breathing glow. Drawn by [NeonRingView] instead of a tinted ProgressBar so the
     * glow, trail and core can all follow the colour the user picked.
     */
    private fun createNeonRingView(): View {
        val container = FrameLayout(context).apply {
            setPadding(sdp(6), sdp(6), sdp(6), sdp(6))
        }
        val ring = NeonRingView(context, LoadingIndicatorStyle.NEON_PALETTE.toIntArray(), sdpF(3.5f)).apply {
            layoutParams = FrameLayout.LayoutParams(sdp(52), sdp(52), Gravity.CENTER)
        }
        val core = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(sdp(8), sdp(8), Gravity.CENTER)
            // A neon tube needs a white-hot centre, not a dark contrast dot.
            background = circleDrawable(Color.WHITE)
            elevation = dpF(6f)
        }
        container.addView(ring)
        container.addView(core)

        val spin = ObjectAnimator.ofFloat(ring, View.ROTATION, 0f, 360f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        // Reflection-free "breathe": drives the glow strength and redraws the ring.
        val breathe = ValueAnimator.ofFloat(0.55f, 1f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { ring.glowStrength = it.animatedValue as Float }
            start()
        }
        val coreScaleX = ObjectAnimator.ofFloat(core, View.SCALE_X, 0.75f, 1.25f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val coreScaleY = ObjectAnimator.ofFloat(core, View.SCALE_Y, 0.75f, 1.25f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        loadingAnimators.addAll(listOf(spin, breathe, coreScaleX, coreScaleY))
        return container
    }

    // --- Result chip (Accept / Reject / Retry) -------------------------------------------------

    /** One tappable action on the result chip. */
    data class ChipAction(val label: String, val onClick: () -> Unit)

    private var resultChipView: LinearLayout? = null
    private var resultChipParams: WindowManager.LayoutParams? = null

    private val hideResultChipRunnable = Runnable { hideResultChip() }

    /**
     * Small floating chip shown next to the field after a command replaced its text.
     *
     * The window is [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE], so tapping it never takes
     * focus away from the text field (the keyboard and cursor stay where they were).
     *
     * @param anchor bounds of the edited field, used to place the chip underneath it (or above it
     *   when there is no room below).
     * @param themeMode the resolved theme (light, dark or amoled) the chip is drawn in.
     * @param autoDismissMs how long the chip stays before it hides itself; `null` keeps it visible
     *   until the caller hides it (used while a stream is still running).
     */
    fun showResultChip(
        config: AppConfig,
        anchor: android.graphics.Rect?,
        actions: List<ChipAction>,
        themeMode: String,
        autoDismissMs: Long? = RESULT_CHIP_TIMEOUT_MS
    ) {
        if (actions.isEmpty()) return
        mainHandler.post {
            hideResultChipInternal()
            if (actions.isEmpty()) return@post

            val colors = ThemePalettes.overlayColors(themeMode)
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(colors.chipBackground)
                    cornerRadius = dpF(18f)
                    setStroke(dpF(1f).toInt().coerceAtLeast(1), colors.chipStroke)
                }
                elevation = dpF(8f)
                isClickable = true
                isFocusable = false
            }

            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    card.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dpF(1f).toInt().coerceAtLeast(1), dpF(18f).toInt())
                        setBackgroundColor(colors.chipDivider)
                    })
                }
                card.addView(TextView(context).apply {
                    text = action.label
                    textSize = 13f
                    setTextColor(actionColor(action.label, colors))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(dpF(14f).toInt(), dpF(10f).toInt(), dpF(14f).toInt(), dpF(10f).toInt())
                    isFocusable = false
                    setOnClickListener { action.onClick() }
                })
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = anchor?.left ?: 0
                y = anchor?.bottom ?: 0
            }

            resultChipView = card
            resultChipParams = params
            try {
                windowManager?.addView(card, params)
                card.post { positionResultChip(card, params, anchor) }
            } catch (e: Exception) {
                resultChipView = null
                resultChipParams = null
                return@post
            }
            if (autoDismissMs != null) {
                mainHandler.postDelayed(hideResultChipRunnable, autoDismissMs)
            }
        }
    }

    fun hideResultChip() {
        mainHandler.removeCallbacks(hideResultChipRunnable)
        mainHandler.post { hideResultChipInternal() }
    }

    private fun hideResultChipInternal() {
        val view = resultChipView
        resultChipView = null
        resultChipParams = null
        if (view != null) {
            try { windowManager?.removeView(view) } catch (e: Exception) {}
        }
    }

    /** Keeps the chip on screen: under the field when it fits, above it otherwise. */
    private fun positionResultChip(
        view: View,
        params: WindowManager.LayoutParams,
        anchor: android.graphics.Rect?
    ) {
        val metrics = context.resources.displayMetrics
        val margin = dpF(10f).toInt()
        val width = view.width.takeIf { it > 0 } ?: return
        val height = view.height

        val centerX = anchor?.centerX() ?: (metrics.widthPixels / 2)
        var x = centerX - width / 2
        var y = (anchor?.bottom ?: (metrics.heightPixels / 2)) + margin
        if (anchor != null && y + height > metrics.heightPixels - margin) {
            y = anchor.top - height - margin
        }

        x = x.coerceIn(margin, (metrics.widthPixels - width - margin).coerceAtLeast(margin))
        y = y.coerceIn(margin, (metrics.heightPixels - height - margin).coerceAtLeast(margin))

        params.x = x
        params.y = y
        try { windowManager?.updateViewLayout(view, params) } catch (e: Exception) {}
    }

    private fun actionColor(label: String, colors: OverlayColors): Int = when (label.lowercase()) {
        "accept" -> colors.accept
        "reject", "stop", "cancel" -> colors.reject
        else -> colors.accent
    }

    fun showUndoButton(config: AppConfig) {        if (!config.enableUndoOverlay) return
        mainHandler.post {
            if (undoView != null) return@post
            undoView = FrameLayout(context)
            val btn = Button(context).apply {
                text = "UNDO"
                textSize = 14f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(0xEE333333.toInt()); cornerRadius = 50f; setStroke(2, Color.WHITE) }
                setOnClickListener { 
                    onUndoAction?.invoke() 
                    hideUndoButton()
                }
            }
            undoView?.addView(btn)
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            params.gravity = Gravity.CENTER
            try { windowManager?.addView(undoView, params); mainHandler.postDelayed(hideUndoRunnable, 5000) } catch (e: Exception) {}
        }
    }

    fun hideUndoButton() {
        mainHandler.removeCallbacks(hideUndoRunnable)
        mainHandler.post {
            if (undoView != null) { try { windowManager?.removeView(undoView); undoView = null } catch (e: Exception) {} }
        }
    }

    fun hidePreviewDialog() {
        mainHandler.post { removePreviewInternal() }
    }

    private fun removePreviewInternal() {
        mainHandler.removeCallbacks(hidePreviewRunnable)
        if (previewView != null) {
            try {
                windowManager?.removeView(previewView)
            } catch (e: Exception) {
            } finally {
                previewView = null
            }
        }
    }

    /**
     * @param themeMode the resolved theme (light, dark or amoled) the card is drawn in.
     */
    fun showPreviewDialog(text: String, themeMode: String, onInsert: () -> Unit) {
        mainHandler.post {
            removePreviewInternal()

            val colors = ThemePalettes.overlayColors(themeMode)
            val cardBgColor = colors.cardBackground
            val primaryTextColor = colors.titleText
            val secondaryTextColor = colors.bodyText
            val discardTextColor = colors.mutedText
            val insertTextColor = colors.accent

            val card = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                background = GradientDrawable().apply { 
                    setColor(cardBgColor)
                    cornerRadius = 32f 
                    setStroke(3, colors.cardStroke)
                }
                isClickable = true
                elevation = 20f
            }

            val title = android.widget.TextView(context).apply {
                this.text = "Preview Response"
                textSize = 18f
                setTextColor(primaryTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 5)
            }
            card.addView(title)

            val hint = android.widget.TextView(context).apply {
                this.text = "Long press and drag to select text portion"
                textSize = 11f
                setTextColor(discardTextColor)
                setTypeface(null, android.graphics.Typeface.ITALIC)
                setPadding(0, 0, 0, 15)
            }
            card.addView(hint)

            val scrollView = android.widget.ScrollView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                    0 
                ).apply { weight = 1f }
            }
            scrollView.layoutParams.height = (context.resources.displayMetrics.heightPixels * 0.35).toInt()
            
            val contentText = android.widget.TextView(context).apply {
                this.text = text
                textSize = 14f
                setTextColor(secondaryTextColor)
                setTextIsSelectable(true)
            }
            scrollView.addView(contentText)
            card.addView(scrollView)

            val btnRow = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, 30, 0, 0)
            }

            fun createButton(label: String, color: Int, onClick: () -> Unit): Button {
                return Button(context).apply {
                    this.text = label
                    setTextColor(color)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    background = android.util.TypedValue().let { tv ->
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                        context.resources.getDrawable(tv.resourceId, context.theme)
                    }
                    setPadding(15, 20, 15, 20)
                    setOnClickListener { onClick() }
                }
            }

            val discardBtn = createButton("Discard", discardTextColor) { hidePreviewDialog() }
            val copyBtn = createButton("Copy", primaryTextColor) {
                val start = contentText.selectionStart
                val end = contentText.selectionEnd
                val min = kotlin.math.min(start, end)
                val max = kotlin.math.max(start, end)

                val textToCopy = if (min >= 0 && max > min) {
                    contentText.text.subSequence(min, max).toString()
                } else {
                    text
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Prompt AI response", textToCopy)
                clipboard.setPrimaryClip(clip)
                
                if (min >= 0 && max > min) {
                    showToast("Copied selection")
                } else {
                    showToast("Copied full text")
                }
            }
            val insertBtn = createButton("Insert", insertTextColor) { 
                onInsert()
                hidePreviewDialog() 
            }

            btnRow.addView(discardBtn)
            btnRow.addView(copyBtn)
            btnRow.addView(insertBtn)
            card.addView(btnRow)

            previewView = FrameLayout(context)
            previewView?.addView(card)

            val rootParams = WindowManager.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.75).toInt(), 
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            
            try { 
                windowManager?.addView(previewView, rootParams) 
            } catch (e: Exception) {}
        }
    }
    
    fun showToast(message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    
    private var snippetSelectionView: FrameLayout? = null
    private var currentSnippetTrigger: String? = null

    /**
     * @param themeMode the resolved theme (light, dark or amoled) the picker is drawn in.
     */
    fun showSnippetSelection(trigger: String, variations: List<String>, themeMode: String, onSelected: (String) -> Unit) {
        if (currentSnippetTrigger == trigger) return
        
        currentSnippetTrigger = trigger
        onOverlayShown?.invoke()

        mainHandler.post {
            removeSnippetSelectionInternal()

            val colors = ThemePalettes.overlayColors(themeMode)
            val cardBgColor = colors.cardBackground
            val primaryTextColor = colors.titleText
            val secondaryTextColor = colors.bodyText
            val surfaceVariantColor = colors.divider
            val primaryColor = colors.cardStroke

            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                background = GradientDrawable().apply {
                    setColor(cardBgColor)
                    cornerRadius = 32f
                    setStroke(3, primaryColor)
                }
                isClickable = true
                elevation = 20f
            }

            val title = android.widget.TextView(context).apply {
                text = "Select Variation: $trigger"
                textSize = 18f
                setTextColor(primaryTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            }
            container.addView(title)

            val scrollView = android.widget.ScrollView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    0
                ).apply { 
                    weight = 1f 
                }
            }
            scrollView.layoutParams.height = (context.resources.displayMetrics.heightPixels * 0.35).toInt()
            
            val list = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }

            variations.forEach { variation ->
                val item = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(24, 32, 24, 32)
                    isClickable = true
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    
                    setOnClickListener {
                        onSelected(variation)
                        hideSnippetSelection()
                    }
                }

                val content = android.widget.TextView(context).apply {
                    text = variation
                    textSize = 14f
                    setTextColor(secondaryTextColor)
                    maxLines = 4
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                item.addView(content)
                
                val divider = android.view.View(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2
                    )
                    setBackgroundColor(surfaceVariantColor)
                }
                
                list.addView(item)
                list.addView(divider)
            }
            scrollView.addView(list)
            container.addView(scrollView)
            
            val btnRow = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, 30, 0, 0)
            }

            val closeBtn = Button(context).apply {
                text = "Cancel"
                setTextColor(primaryTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = android.util.TypedValue().let { tv ->
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                    context.resources.getDrawable(tv.resourceId, context.theme)
                }
                setPadding(15, 20, 15, 20)
                setOnClickListener { hideSnippetSelection() }
            }
            btnRow.addView(closeBtn)
            container.addView(btnRow)

            snippetSelectionView = FrameLayout(context)
            snippetSelectionView?.addView(container)

            val params = WindowManager.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.80).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try { windowManager?.addView(snippetSelectionView, params) } catch (e: Exception) {}
        }
    }

    fun hideSnippetSelection() {
        currentSnippetTrigger = null
        onOverlayHidden?.invoke()
        mainHandler.post {
            removeSnippetSelectionInternal()
        }
    }

    private fun removeSnippetSelectionInternal() {
        if (snippetSelectionView != null) {
            try {
                windowManager?.removeView(snippetSelectionView)
            } catch (e: Exception) {
            } finally {
                snippetSelectionView = null
            }
        }
    }

    fun hideAll() {
        hideLoading()
        hideUndoButton()
        hidePreviewDialog()
        hideSnippetSelection()
        hideResultChip()
    }

    companion object {
        /** How long the Accept / Reject / Retry chip stays on screen before it hides itself. */
        const val RESULT_CHIP_TIMEOUT_MS = 6000L
    }
}

/**
 * A glowing ring, drawn rather than tinted: a dim track, a comet-like arc whose tail fades out,
 * a wide soft halo and a bright leading head. [glowStrength] makes the glow breathe.
 */
private class NeonRingView(
    context: Context,
    palette: IntArray,
    private val strokeWidth: Float
) : View(context) {

    private val neonPalette = palette.copyOf()
    private val baseNeonColor = neonPalette.first()

    /** 0f..1f - how strongly the tube is glowing right now. */
    var glowStrength: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val arcRect = RectF()
    private var headX = 0f
    private var headY = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = this@NeonRingView.strokeWidth
        color = LoadingIndicatorStyle.withAlpha(baseNeonColor, 36)
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = this@NeonRingView.strokeWidth * 3f
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = this@NeonRingView.strokeWidth
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = this@NeonRingView.strokeWidth * 0.45f
    }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    /** Length of the lit arc; the remaining 60 degrees are the faded tail. */
    private val sweepAngle = 300f

    init {
        // setShadowLayer() is ignored for shapes on a hardware canvas - glow needs software.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Leave room around the ring so the halo and the glow are not clipped by the view bounds.
        val inset = strokeWidth * 3.5f + 1f
        arcRect.set(inset, inset, w - inset, h - inset)

        val spectrumStops = floatArrayOf(0f, 0.08f, 0.25f, 0.45f, 0.65f, 0.85f, 1f)
        val spectrum = intArrayOf(
            LoadingIndicatorStyle.withAlpha(neonPalette[0], 0),
            LoadingIndicatorStyle.withAlpha(neonPalette[0], 220),
            neonPalette[1],
            neonPalette[2],
            neonPalette[3],
            neonPalette[4],
            neonPalette[0]
        )
        arcPaint.shader = SweepGradient(w / 2f, h / 2f, spectrum, spectrumStops)
        haloPaint.shader = SweepGradient(
            w / 2f,
            h / 2f,
            intArrayOf(
                LoadingIndicatorStyle.withAlpha(neonPalette[0], 0),
                LoadingIndicatorStyle.withAlpha(neonPalette[0], 90),
                LoadingIndicatorStyle.withAlpha(neonPalette[1], 70),
                LoadingIndicatorStyle.withAlpha(neonPalette[2], 70),
                LoadingIndicatorStyle.withAlpha(neonPalette[3], 70),
                LoadingIndicatorStyle.withAlpha(neonPalette[4], 70),
                LoadingIndicatorStyle.withAlpha(neonPalette[0], 70)
            ),
            spectrumStops
        )
        corePaint.shader = SweepGradient(
            w / 2f,
            h / 2f,
            intArrayOf(0x00FFFFFF, 0x40FFFFFF, 0xB3FFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            floatArrayOf(0f, 0.3f, 0.85f, 1f)
        )
        val radians = Math.toRadians(sweepAngle.toDouble())
        headX = arcRect.centerX() + arcRect.width() / 2f * cos(radians).toFloat()
        headY = arcRect.centerY() + arcRect.height() / 2f * sin(radians).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (arcRect.isEmpty) return
        val glow = glowStrength

        haloPaint.alpha = (110f * glow).toInt().coerceIn(0, 255)
        haloPaint.setShadowLayer(strokeWidth * 1.5f * glow, 0f, 0f, baseNeonColor)
        canvas.drawArc(arcRect, 0f, sweepAngle, false, haloPaint)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        arcPaint.setShadowLayer(strokeWidth * 2.2f * glow, 0f, 0f, baseNeonColor)
        canvas.drawArc(arcRect, 0f, sweepAngle, false, arcPaint)

        // White-hot core running through the multicolour tube.
        corePaint.setShadowLayer(strokeWidth * glow, 0f, 0f, baseNeonColor)
        canvas.drawArc(arcRect, 0f, sweepAngle, false, corePaint)

        headPaint.setShadowLayer(strokeWidth * 2.5f * glow, 0f, 0f, Color.WHITE)
        canvas.drawCircle(headX, headY, strokeWidth * 0.75f, headPaint)
    }
}
