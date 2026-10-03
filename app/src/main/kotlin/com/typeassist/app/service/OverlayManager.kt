package com.typeassist.app.service

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.typeassist.app.data.AppConfig

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

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density

    fun showLoading(config: AppConfig) {
        if (!config.enableLoadingOverlay) return
        mainHandler.post {
            if (loadingView != null) return@post
            clearLoadingAnimators()
            val rawStyle = try { config.loadingIndicatorStyle } catch (_: Exception) { "classic" }
            val style = (rawStyle as? String ?: "classic").ifBlank { "classic" }
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

    private fun roundedBg(color: Int, radiusF: Float, strokeColor: Int? = null, strokeW: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusF
            if (strokeColor != null) setStroke(strokeW, strokeColor)
        }
    }

    private fun circleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun createClassicView(): View {
        val container = FrameLayout(context).apply {
            setPadding(30, 30, 30, 30)
            background = roundedBg(0x99000000.toInt(), 40f)
        }
        val progressBar = ProgressBar(context).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        container.addView(progressBar)
        return container
    }

    private fun createDotsView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(16), dp(22), dp(16))
            background = roundedBg(0xEE1E1E1E.toInt(), dpF(28f), 0x33FFFFFF, dp(1))
            elevation = dpF(8f)
        }
        repeat(3) { index ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                    setMargins(dp(5), 0, dp(5), 0)
                }
                background = circleDrawable(Color.WHITE)
            }
            container.addView(dot)
            val anim = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.25f, 1f).apply {
                duration = 600
                startDelay = (index * 180L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            loadingAnimators.add(anim)
            val scaleX = ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.7f, 1.15f).apply {
                duration = 600
                startDelay = (index * 180L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
            val scaleY = ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.7f, 1.15f).apply {
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
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = roundedBg(0xEE1E1E1E.toInt(), dpF(28f), 0xFF4F46E5.toInt(), dp(2))
            elevation = dpF(10f)
        }
        val outer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
            background = circleDrawable(0x334F46E5)
        }
        val inner = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
            background = circleDrawable(Color.WHITE)
            elevation = dpF(4f)
        }
        container.addView(outer)
        container.addView(inner)

        val pulseScaleX = ObjectAnimator.ofFloat(inner, View.SCALE_X, 0.8f, 1.35f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val pulseScaleY = ObjectAnimator.ofFloat(inner, View.SCALE_Y, 0.8f, 1.35f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val outerScaleX = ObjectAnimator.ofFloat(outer, View.SCALE_X, 0.8f, 1.5f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        val outerScaleY = ObjectAnimator.ofFloat(outer, View.SCALE_Y, 0.8f, 1.5f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        val outerAlpha = ObjectAnimator.ofFloat(outer, View.ALPHA, 0.9f, 0.2f).apply {
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
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBg(0xEE1E1E1E.toInt(), dpF(24f), 0x33FFFFFF, dp(1))
            elevation = dpF(8f)
        }
        repeat(3) { index ->
            val bar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(22)).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                    gravity = Gravity.BOTTOM
                }
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dpF(3f)
                }
            }
            container.addView(bar)
            val anim = ObjectAnimator.ofFloat(bar, View.SCALE_Y, 0.4f, 1.6f).apply {
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
            setPadding(dp(22), dp(14), dp(22), dp(14))
            background = roundedBg(0xFF2A2A2E.toInt(), dpF(28f), 0xFF4F46E5.toInt(), dp(2))
            elevation = dpF(8f)
        }
        repeat(3) { index ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
                background = circleDrawable(0xFF818CF8.toInt())
            }
            container.addView(dot)
            val anim = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -dpF(8f)).apply {
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
            setPadding(dp(18), dp(12), dp(20), dp(12))
            background = roundedBg(0xEE1E1E1E.toInt(), dpF(50f), 0xFF4F46E5.toInt(), dp(2))
            elevation = dpF(10f)
        }
        val progress = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                setMargins(0, 0, dp(12), 0)
            }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFF818CF8.toInt())
        }
        val text = TextView(context).apply {
            this.text = "AI thinking..."
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        container.addView(progress)
        container.addView(text)
        return container
    }

    private fun createNeonRingView(): View {
        val container = FrameLayout(context).apply {
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBg(0xDD111113.toInt(), dpF(32f), 0xFF818CF8.toInt(), dp(1))
            elevation = dpF(12f)
        }
        val ring = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER)
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFF818CF8.toInt())
        }
        val dot = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER)
            background = circleDrawable(Color.WHITE)
        }
        container.addView(ring)
        container.addView(dot)

        val rot = ObjectAnimator.ofFloat(ring, View.ROTATION, 0f, 360f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
        loadingAnimators.add(rot)
        return container
    }

    fun showUndoButton(config: AppConfig) {
        if (!config.enableUndoOverlay) return
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

    fun showPreviewDialog(text: String, isDarkMode: Boolean, onInsert: () -> Unit) {
        mainHandler.post {
            removePreviewInternal()
            
            val cardBgColor = if (isDarkMode) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
            val primaryTextColor = if (isDarkMode) 0xFF818CF8.toInt() else 0xFF4F46E5.toInt()
            val secondaryTextColor = if (isDarkMode) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val discardTextColor = if (isDarkMode) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
            val insertTextColor = if (isDarkMode) 0xFF818CF8.toInt() else 0xFF4F46E5.toInt()

            val card = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                background = GradientDrawable().apply { 
                    setColor(cardBgColor)
                    cornerRadius = 32f 
                    setStroke(3, insertTextColor) 
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

    fun showSnippetSelection(trigger: String, variations: List<String>, isDarkMode: Boolean, onSelected: (String) -> Unit) {
        if (currentSnippetTrigger == trigger) return
        
        currentSnippetTrigger = trigger
        onOverlayShown?.invoke()

        mainHandler.post {
            removeSnippetSelectionInternal()

            val cardBgColor = if (isDarkMode) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
            val primaryTextColor = if (isDarkMode) 0xFF818CF8.toInt() else 0xFF4F46E5.toInt()
            val secondaryTextColor = if (isDarkMode) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val surfaceVariantColor = if (isDarkMode) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()
            val primaryColor = if (isDarkMode) 0xFF818CF8.toInt() else 0xFF4F46E5.toInt()

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
    }
}
