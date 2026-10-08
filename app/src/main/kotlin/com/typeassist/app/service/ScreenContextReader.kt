package com.typeassist.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.typeassist.app.data.AppConfig
import com.typeassist.app.utils.ScreenContextOptions
import com.typeassist.app.utils.ScreenTextBlock
import com.typeassist.app.utils.ScreenTextExtractor

/**
 * Reads the visible text of the screen the user is looking at and turns it into the context sent to
 * the model. Only ever called for an explicit screen command; nothing here runs in the background.
 *
 * The walking happens on the accessibility tree; every decision about what counts as usable text
 * and how it is ordered is in [ScreenTextExtractor], which is unit tested.
 */
class ScreenContextReader(private val service: AccessibilityService) {

    fun readScreenContext(config: AppConfig): String? {
        val root = service.rootInActiveWindow ?: return null
        val ownPackage = service.packageName
        val blocks = mutableListOf<ScreenTextBlock>()

        try {
            collect(root, blocks, ownPackage, depth = 0, budget = intArrayOf(MAX_NODES))
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the accessibility tree: ${e.message}")
            return null
        }

        val screenWidth = service.resources.displayMetrics.widthPixels
        return ScreenTextExtractor.extract(
            blocks = blocks,
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = config.screenContextChatLabels)
        )
    }

    /** The package currently on screen, used to check the user's "never read" list. */
    fun foregroundPackage(): String? = try {
        service.rootInActiveWindow?.packageName?.toString()
    } catch (e: Exception) {
        null
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        blocks: MutableList<ScreenTextBlock>,
        ownPackage: String,
        depth: Int,
        budget: IntArray
    ) {
        if (depth > MAX_DEPTH || budget[0] <= 0) return
        budget[0]--

        val packageName = node.packageName?.toString().orEmpty()

        // Never leave the app the user is looking at, and never read our own overlay.
        val visibleApp = packageName.isNotEmpty() && packageName != ownPackage
        if (visibleApp && node.isVisibleToUser) {
            val text = textOf(node)
            if (text != null && !node.isPassword && !node.isEditable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                blocks.add(
                    ScreenTextBlock(
                        text = text,
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                        isEditable = node.isEditable,
                        isPassword = node.isPassword,
                        packageName = packageName
                    )
                )
            }
        }

        for (index in 0 until node.childCount) {
            val child = try { node.getChild(index) } catch (e: Exception) { null } ?: continue
            collect(child, blocks, ownPackage, depth + 1, budget)
        }
    }

    /** A node's own text, falling back to its content description. */
    private fun textOf(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        return when {
            text.isNotEmpty() -> text
            description.isNotEmpty() -> description
            else -> null
        }
    }

    companion object {
        private const val TAG = "TypeAssistScreen"
        private const val MAX_DEPTH = 30
        private const val MAX_NODES = 600
    }
}
