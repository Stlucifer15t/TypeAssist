package com.typeassist.app.utils

/**
 * One piece of visible screen text, as read from the accessibility tree. Deliberately free of any
 * Android import so the filtering, sorting and truncation rules can be unit tested.
 */
data class ScreenTextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isEditable: Boolean = false,
    val isPassword: Boolean = false,
    val packageName: String = ""
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isUsable: Boolean get() = right > left && bottom > top
}

data class ScreenContextOptions(
    /** Label each block "Them:" / "Me:" when the layout looks like a conversation. */
    val chatLabels: Boolean = true,
    val maxChars: Int = ScreenTextExtractor.MAX_CONTEXT_CHARS,
    /** Packages whose text is never used: our own overlay, and system chrome. */
    val excludedPackages: Set<String> = ScreenTextExtractor.EXCLUDED_PACKAGES
)

/**
 * Turns the blocks read from the accessibility tree into the text sent to the model.
 *
 * Kept pure on purpose: the reading (walking `AccessibilityNodeInfo`) lives in the service, while
 * every decision about what counts as usable text, how it is ordered and how it is trimmed is here.
 */
object ScreenTextExtractor {

    /** Roughly 4000 characters: enough for a screenful of chat without blowing up the prompt. */
    const val MAX_CONTEXT_CHARS = 4000

    /** Status bar, navigation bar and anything else that is not the app the user is looking at. */
    val EXCLUDED_PACKAGES: Set<String> = setOf("com.android.systemui", "android")

    const val LABEL_THEM = "Them:"
    const val LABEL_ME = "Me:"

    /** Left/right halves decide who wrote a block; only clearly off-centre blocks are labelled. */
    private const val ME_ZONE = 0.65
    private const val THEM_ZONE = 0.45

    private const val TRUNCATION_NOTICE = "[earlier screen content omitted]\n"

    /**
     * @return the screen context, or null when there is nothing usable to send.
     */
    fun extract(
        blocks: List<ScreenTextBlock>,
        screenWidth: Int,
        options: ScreenContextOptions = ScreenContextOptions()
    ): String? {
        val usable = blocks
            .filter { isUsable(it, options) }
            .sortedWith(compareBy({ it.top }, { it.left }))

        if (usable.isEmpty()) return null

        val withLabels = if (options.chatLabels && looksLikeConversation(usable, screenWidth)) {
            usable.map { block ->
                val label = if (block.centerX >= (screenWidth * ME_ZONE)) LABEL_ME else LABEL_THEM
                "$label ${block.text}"
            }
        } else {
            usable.map { it.text }
        }

        return truncateKeepingBottom(withLabels.distinctConsecutive(), options.maxChars)
    }

    /** True when the visible package is on the user's "never read" list. */
    fun isPackageBlocked(blockedPackages: List<String>?, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return blockedPackages.orEmpty().any { it.trim().equals(packageName, ignoreCase = true) }
    }

    private fun isUsable(block: ScreenTextBlock, options: ScreenContextOptions): Boolean {
        if (block.text.isBlank()) return false
        if (!block.isUsable) return false
        if (block.isEditable || block.isPassword) return false
        val pkg = block.packageName.trim().lowercase()
        if (pkg.isNotEmpty() && (pkg in options.excludedPackages || options.excludedPackages.any { it.equals(pkg, true) })) {
            return false
        }
        return true
    }

    /**
     * A screen is treated as a conversation when it has both left-aligned and clearly right-aligned
     * blocks - the signature of chat bubbles. A normal page with uniformly aligned text is left
     * unlabelled, because guessing who wrote it would be noise.
     */
    private fun looksLikeConversation(blocks: List<ScreenTextBlock>, screenWidth: Int): Boolean {
        if (screenWidth <= 0 || blocks.size < 2) return false
        val hasRight = blocks.any { it.centerX >= screenWidth * ME_ZONE }
        val hasLeft = blocks.any { it.centerX <= screenWidth * THEM_ZONE }
        return hasRight && hasLeft
    }

    private fun List<String>.distinctConsecutive(): List<String> {
        val result = mutableListOf<String>()
        var previous: String? = null
        for (entry in this) {
            if (entry == previous) continue
            result.add(entry)
            previous = entry
        }
        return result
    }

    /**
     * Keeps the newest content: when the context is too long, the oldest blocks are dropped from
     * the top (a chat shows its latest messages at the bottom) and a short notice is prepended.
     */
    private fun truncateKeepingBottom(lines: List<String>, maxChars: Int): String? {
        if (lines.isEmpty()) return null
        val budget = maxChars.coerceAtLeast(1)
        val kept = ArrayDeque<String>()
        var used = 0

        for (line in lines.asReversed()) {
            val cost = line.length + 1
            if (used + cost > budget) break
            kept.addFirst(line)
            used += cost
        }

        if (kept.isEmpty()) {
            // One single block is longer than the whole budget: keep its newest part.
            return lines.last().takeLast(budget).takeIf { it.isNotBlank() }
        }

        val body = kept.joinToString("\n")
        if (kept.size == lines.size) return body

        val allowed = (budget - TRUNCATION_NOTICE.length).coerceAtLeast(0)
        val trimmed = if (body.length > allowed) body.takeLast(allowed) else body
        return (TRUNCATION_NOTICE + trimmed).takeIf { it.isNotBlank() }
    }
}
