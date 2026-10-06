package com.typeassist.app.service

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
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
import com.typeassist.app.data.AppThemeMode
import com.typeassist.app.data.LoadingIndicatorStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Colours for the floating cards, resolved from the user's theme choice in Settings →
 * Appearance: System follows the device, Dark and AMOLED are always dark (AMOLED pure
 * black), Light always light. Keeps the floating UI consistent with the in-app Material 3 look.
 */
data class OverlayPalette(
    val isDark: Boolean,
    /** Card background (with slight transparency so it floats over any app). */
    val background: Int,
    /** Accent colour for titles and text actions. */
    val primary: Int,
    /** Main text colour. */
    val onSurface: Int,
    /** Secondary text colour (hints). */
    val onSurfaceVariant: Int,
    /** Hairline dividers and card borders. */
    val outlineVariant: Int,
    /** Tonal pill/surface colour (undo chip). */
    val surfaceContainer: Int
)

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var loadingView: View? = null
    private var undoView: FrameLayout? = null
    private var previewView: FrameLayout? = null 
    private val loadingAnimators = mutableListOf<Animator>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideUndoRunnable = Runnable { hideUndoButton() }
    private val hidePreviewRunnable = Runnable { hidePreviewDialog() }
    private var streamingView: FrameLayout? = null
    private var streamingTextView: TextView? = null
    private var streamingScrollView: android.widget.ScrollView? = null
    private var autoCompleteView: FrameLayout? = null
    private var selectionToolbarView: FrameLayout? = null
    
    var onUndoAction: (() -> Unit)? = null
    var onOverlayShown: (() -> Unit)? = null
    var onOverlayHidden: (() -> Unit)? = null

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density

    /** True when the device itself is in dark mode (used for the System theme option). */
    private fun isDeviceDarkMode(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Resolves the floating-card colours for the user's saved theme mode. */
    fun paletteFor(config: AppConfig?): OverlayPalette {
        val mode = AppThemeMode.sanitize(config?.appThemeMode)
        val dark = when (mode) {
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK, AppThemeMode.AMOLED -> true
            else -> isDeviceDarkMode()
        }
        val amoled = dark && mode == AppThemeMode.AMOLED
        return if (dark) {
            OverlayPalette(
                isDark = true,
                background = (if (amoled) 0xF0000000L else 0xF0131420L).toInt(),
                primary = 0xFFC4C0FF.toInt(),
                onSurface = 0xFFE3E1E9.toInt(),
                onSurfaceVariant = 0xFFB9B7C6.toInt(),
                outlineVariant = (if (amoled) 0xFF2A2A33L else 0xFF3D3C48L).toInt(),
                surfaceContainer = (if (amoled) 0xFF14141AL else 0xFF1E1F26L).toInt()
            )
        } else {
            OverlayPalette(
                isDark = false,
                background = 0xF7FBF9FF.toInt(),
                primary = 0xFF5348CE.toInt(),
                onSurface = 0xFF1A1B22.toInt(),
                onSurfaceVariant = 0xFF5B5A64.toInt(),
                outlineVariant = 0xFFD8D6E2.toInt(),
                surfaceContainer = 0xFFEFEDF5.toInt()
            )
        }
    }

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
            indicatorColor = LoadingIndicatorStyle.sanitizeColor(config.loadingIndicatorColor)
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
        val ring = NeonRingView(context, indicatorColor, sdpF(3.5f)).apply {
            layoutParams = FrameLayout.LayoutParams(sdp(52), sdp(52), Gravity.CENTER)
        }
        val core = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(sdp(8), sdp(8), Gravity.CENTER)
            background = circleDrawable(LoadingIndicatorStyle.contrastColor(indicatorColor))
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

    fun showUndoButton(config: AppConfig) {
        if (!config.enableUndoOverlay) return
        mainHandler.post {
            if (undoView != null) return@post
            val palette = paletteFor(config)
            undoView = FrameLayout(context)
            val btn = Button(context).apply {
                text = "UNDO"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.primary)
                stateListAnimator = null
                background = GradientDrawable().apply {
                    setColor(palette.surfaceContainer)
                    cornerRadius = dpF(26f)
                    setStroke(dpF(1f).toInt().coerceAtLeast(1), palette.outlineVariant)
                }
                setPadding(dpF(22f).toInt(), dpF(10f).toInt(), dpF(22f).toInt(), dpF(10f).toInt())
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

    fun showPreviewDialog(text: String, palette: OverlayPalette, onInsert: () -> Unit) {
        mainHandler.post {
            removePreviewInternal()

            val cardBgColor = palette.background
            val primaryTextColor = palette.primary
            val secondaryTextColor = palette.onSurface
            val discardTextColor = palette.onSurfaceVariant
            val insertTextColor = palette.primary
            val cardRadius = dpF(28f)

            val card = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dpF(22f).toInt(), dpF(20f).toInt(), dpF(22f).toInt(), dpF(12f).toInt())
                background = GradientDrawable().apply {
                    setColor(cardBgColor)
                    cornerRadius = cardRadius
                    setStroke(dpF(1f).toInt().coerceAtLeast(1), palette.outlineVariant)
                }
                isClickable = true
                elevation = dpF(12f)
            }

            val title = android.widget.TextView(context).apply {
                this.text = "Preview Response"
                textSize = 16f
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
                    stateListAnimator = null
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
    
    /** True while a modal overlay (loading, streaming, preview, snippet picker) is up. */
    fun isBusy(): Boolean =
        loadingView != null || streamingView != null || previewView != null || snippetSelectionView != null

    // ------------------------------------------------------------------
    // Streaming response card
    // ------------------------------------------------------------------

    /** Shows an M3 card with the accumulating AI response while tokens stream in. */
    fun showStreamingCard(config: AppConfig) {
        mainHandler.post {
            hideStreamingInternal()
            val palette = paletteFor(config)
            val card = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dpF(20f).toInt(), dpF(16f).toInt(), dpF(20f).toInt(), dpF(14f).toInt())
                background = GradientDrawable().apply {
                    setColor(palette.background)
                    cornerRadius = dpF(26f)
                    setStroke(dpF(1f).toInt().coerceAtLeast(1), palette.outlineVariant)
                }
                isClickable = true
                elevation = dpF(12f)
            }

            val title = TextView(context).apply {
                text = "✍️ AI is writing…"
                textSize = 13f
                setTextColor(palette.primary)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            card.addView(title)

            val scrollView = android.widget.ScrollView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    0
                ).apply { weight = 1f }
            }
            val text = TextView(context).apply {
                textSize = 14f
                setTextColor(palette.onSurface)
                setPadding(0, dpF(10f).toInt(), 0, 0)
            }
            scrollView.addView(text)
            card.addView(scrollView)

            streamingTextView = text
            streamingScrollView = scrollView

            streamingView = FrameLayout(context).apply { addView(card) }
            val params = WindowManager.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.72f).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.30f).toInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            try { windowManager?.addView(streamingView, params) } catch (_: Exception) {}
        }
    }

    /** Appends-free update: replaces the card body with the full accumulated text and autoscrolls. */
    fun updateStreamingText(accumulated: String) {
        mainHandler.post {
            streamingTextView?.text = accumulated
            streamingScrollView?.post {
                streamingScrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    fun hideStreamingCard() {
        mainHandler.post { hideStreamingInternal() }
    }

    private fun hideStreamingInternal() {
        if (streamingView != null) {
            try { windowManager?.removeView(streamingView) } catch (_: Exception) {}
            streamingView = null
            streamingTextView = null
            streamingScrollView = null
        }
    }

    // ------------------------------------------------------------------
    // Trigger autocomplete hint
    // ------------------------------------------------------------------

    /**
     * Small hint popup listing commands that match what the user is typing.
     * [anchorY] is the screen Y of the input field's top edge; the popup floats just above it.
     */
    fun showAutocomplete(
        candidates: List<Pair<String, String>>,
        palette: OverlayPalette,
        anchorY: Int,
        onPick: (String) -> Unit
    ) {
        mainHandler.post {
            hideAutocompleteInternal()
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dpF(12f).toInt(), dpF(8f).toInt(), dpF(12f).toInt(), dpF(8f).toInt())
                background = GradientDrawable().apply {
                    setColor(palette.background)
                    cornerRadius = dpF(20f)
                    setStroke(dpF(1f).toInt().coerceAtLeast(1), palette.outlineVariant)
                }
                elevation = dpF(10f)
            }

            candidates.forEach { (pattern, prompt) ->
                val row = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dpF(12f).toInt(), dpF(10f).toInt(), dpF(12f).toInt(), dpF(10f).toInt())
                    isClickable = true
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        onPick(pattern)
                        hideAutocomplete()
                    }
                }
                val patternView = TextView(context).apply {
                    text = pattern
                    textSize = 14f
                    setTextColor(palette.primary)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val promptView = TextView(context).apply {
                    text = prompt
                    textSize = 11f
                    setTextColor(palette.onSurfaceVariant)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                row.addView(patternView)
                row.addView(promptView)
                container.addView(row)
            }

            autoCompleteView = FrameLayout(context).apply { addView(container) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = anchorY.coerceAtLeast(dpF(56f).toInt())
            }
            try { windowManager?.addView(autoCompleteView, params) } catch (_: Exception) {}
        }
    }

    fun hideAutocomplete() {
        mainHandler.post { hideAutocompleteInternal() }
    }

    private fun hideAutocompleteInternal() {
        if (autoCompleteView != null) {
            try { windowManager?.removeView(autoCompleteView) } catch (_: Exception) {}
            autoCompleteView = null
        }
    }

    // ------------------------------------------------------------------
    // Selection toolbar
    // ------------------------------------------------------------------

    /** One tappable action on the selection toolbar; [onClick] runs on the main thread. */
    data class SelectionAction(val label: String, val onClick: () -> Unit)

    /** Floating action bar shown just above the field while the user has text selected. */
    fun showSelectionToolbar(actions: List<SelectionAction>, palette: OverlayPalette, anchorTopY: Int) {
        mainHandler.post {
            hideSelectionToolbarInternal()
            val row = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(dpF(6f).toInt(), dpF(4f).toInt(), dpF(6f).toInt(), dpF(4f).toInt())
            }
            actions.forEach { action ->
                val chip = TextView(context).apply {
                    text = action.label
                    textSize = 13f
                    setTextColor(palette.onSurface)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    val pad = dpF(13f).toInt()
                    setPadding(pad, dpF(9f).toInt(), pad, dpF(9f).toInt())
                    isClickable = true
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        hideSelectionToolbar()
                        action.onClick()
                    }
                }
                row.addView(chip)
            }
            val card = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(palette.surfaceContainer)
                    cornerRadius = dpF(26f)
                    setStroke(dpF(1f).toInt().coerceAtLeast(1), palette.outlineVariant)
                }
                elevation = dpF(10f)
                addView(row)
            }
            selectionToolbarView = FrameLayout(context).apply { addView(card) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (anchorTopY - dpF(64f).toInt()).coerceAtLeast(dpF(56f).toInt())
            }
            try { windowManager?.addView(selectionToolbarView, params) } catch (_: Exception) {}
        }
    }

    fun hideSelectionToolbar() {
        mainHandler.post { hideSelectionToolbarInternal() }
    }

    private fun hideSelectionToolbarInternal() {
        if (selectionToolbarView != null) {
            try { windowManager?.removeView(selectionToolbarView) } catch (_: Exception) {}
            selectionToolbarView = null
        }
    }

    fun showToast(message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    
    private var snippetSelectionView: FrameLayout? = null
    private var currentSnippetTrigger: String? = null

    fun showSnippetSelection(trigger: String, variations: List<String>, palette: OverlayPalette, onSelected: (String) -> Unit) {
        if (currentSnippetTrigger == trigger) return

        currentSnippetTrigger = trigger
        onOverlayShown?.invoke()

        mainHandler.post {
            removeSnippetSelectionInternal()

            val cardBgColor = palette.background
            val primaryTextColor = palette.primary
            val secondaryTextColor = palette.onSurface
            val surfaceVariantColor = palette.outlineVariant

            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dpF(22f).toInt(), dpF(20f).toInt(), dpF(22f).toInt(), dpF(12f).toInt())
                background = GradientDrawable().apply {
                    setColor(cardBgColor)
                    cornerRadius = dpF(28f)
                    setStroke(dpF(1f).toInt().coerceAtLeast(1), palette.outlineVariant)
                }
                isClickable = true
                elevation = dpF(12f)
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
        hideStreamingCard()
        hideAutocomplete()
        hideSelectionToolbar()
    }
}

/**
 * A glowing ring, drawn rather than tinted: a dim track, a comet-like arc whose tail fades out,
 * a wide soft halo and a bright leading head. [glowStrength] makes the glow breathe.
 */
private class NeonRingView(
    context: Context,
    private val color: Int,
    private val strokeWidth: Float
) : View(context) {

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
        this.color = LoadingIndicatorStyle.withAlpha(this@NeonRingView.color, 44)
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = this@NeonRingView.strokeWidth * 3f
        this.color = LoadingIndicatorStyle.withAlpha(this@NeonRingView.color, 70)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = this@NeonRingView.strokeWidth
    }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = LoadingIndicatorStyle.contrastColor(this@NeonRingView.color)
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
        arcPaint.shader = SweepGradient(
            w / 2f,
            h / 2f,
            intArrayOf(
                LoadingIndicatorStyle.withAlpha(color, 0),
                LoadingIndicatorStyle.withAlpha(color, 90),
                color,
                color
            ),
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

        haloPaint.alpha = (80f * glow).toInt().coerceIn(0, 255)
        haloPaint.setShadowLayer(strokeWidth * 1.5f * glow, 0f, 0f, color)
        canvas.drawArc(arcRect, 0f, sweepAngle, false, haloPaint)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        arcPaint.setShadowLayer(strokeWidth * 2f * glow, 0f, 0f, color)
        canvas.drawArc(arcRect, 0f, sweepAngle, false, arcPaint)

        headPaint.setShadowLayer(strokeWidth * 2.5f * glow, 0f, 0f, color)
        canvas.drawCircle(headX, headY, strokeWidth * 0.75f, headPaint)
    }
}
