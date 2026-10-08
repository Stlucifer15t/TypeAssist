package com.typeassist.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextExtractorTest {

    private val screenWidth = 1080

    private fun block(
        text: String,
        top: Int,
        left: Int = 0,
        right: Int = 600,
        packageName: String = "com.example.chat"
    ) = ScreenTextBlock(
        text = text,
        left = left,
        top = top,
        right = right,
        bottom = top + 60,
        packageName = packageName
    )

    @Test
    fun ordersBlocksTopToBottomThenLeftToRight() {
        val context = ScreenTextExtractor.extract(
            blocks = listOf(
                block("second line", top = 200, left = 40),
                block("first line right", top = 100, left = 500),
                block("first line left", top = 100, left = 20)
            ),
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = false)
        )

        assertEquals("first line left\nfirst line right\nsecond line", context)
    }

    @Test
    fun skipsPasswordEditableOurOwnAndSystemBlocks() {
        val context = ScreenTextExtractor.extract(
            blocks = listOf(
                block("visible text", top = 100),
                block("hunter2", top = 140).copy(isPassword = true),
                block("the user is typing", top = 180).copy(isEditable = true),
                block("Accept Reject Retry", top = 220, packageName = "com.typeassist.app"),
                block("12:41", top = 20, packageName = "com.android.systemui"),
                block("   ", top = 240)
            ),
            screenWidth = screenWidth,
            options = ScreenContextOptions(
                chatLabels = false,
                excludedPackages = ScreenTextExtractor.EXCLUDED_PACKAGES + "com.typeassist.app"
            )
        )

        assertEquals("visible text", context)
    }

    @Test
    fun dropsZeroSizedBlocks() {
        val context = ScreenTextExtractor.extract(
            blocks = listOf(
                ScreenTextBlock("no bounds", 0, 0, 0, 0, packageName = "com.example.chat"),
                block("real text", top = 50)
            ),
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = false)
        )

        assertEquals("real text", context)
    }

    @Test
    fun labelsChatSidesByHorizontalPosition() {
        val context = ScreenTextExtractor.extract(
            blocks = listOf(
                block("Are we still on for tomorrow?", top = 100, left = 40, right = 700),
                block("Yes, 10am works", top = 200, left = 500, right = 1040)
            ),
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = true)
        )

        assertEquals(
            "Them: Are we still on for tomorrow?\nMe: Yes, 10am works",
            context
        )
    }

    @Test
    fun doesNotLabelUniformPages() {
        // A web page or document: every block hugs the left edge, so guessing a speaker would be noise.
        val context = ScreenTextExtractor.extract(
            blocks = listOf(
                block("Terms of service", top = 100, left = 40, right = 900),
                block("Last updated yesterday", top = 200, left = 40, right = 900)
            ),
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = true)
        )

        assertEquals("Terms of service\nLast updated yesterday", context)
    }

    @Test
    fun chatLabelsCanBeTurnedOff() {
        val context = ScreenTextExtractor.extract(
            blocks = listOf(
                block("left", top = 100, left = 40, right = 700),
                block("right", top = 200, left = 500, right = 1040)
            ),
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = false)
        )

        assertEquals("left\nright", context)
    }

    @Test
    fun collapsesRepeatedNeighbouringText() {
        // Accessibility trees often expose the same text on a container and its child.
        val context = ScreenTextExtractor.extract(
            blocks = listOf(
                block("Delivery by Friday", top = 100),
                block("Delivery by Friday", top = 100),
                block("Tracking: 1234", top = 200)
            ),
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = false)
        )

        assertEquals("Delivery by Friday\nTracking: 1234", context)
    }

    @Test
    fun truncationKeepsTheNewestContentAndSaysSo() {
        val blocks = (1..40).map { index -> block("message number $index", top = index * 100) }

        val context = ScreenTextExtractor.extract(
            blocks = blocks,
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = false, maxChars = 200)
        )!!

        assertTrue(context.length <= 200 + ScreenTextExtractor.MAX_CONTEXT_CHARS) // sane upper bound
        assertTrue(context.length <= 200)
        assertTrue(context.startsWith("[earlier screen content omitted]"))
        assertTrue(context.contains("message number 40"))
        assertFalse(context.contains("message number 1\n"))
        assertFalse(context.contains("message number 20"))
    }

    @Test
    fun aSingleHugeBlockKeepsItsNewestPart() {
        val huge = "x".repeat(500) + "THE-END"

        val context = ScreenTextExtractor.extract(
            blocks = listOf(block(huge, top = 100)),
            screenWidth = screenWidth,
            options = ScreenContextOptions(chatLabels = false, maxChars = 60)
        )!!

        assertEquals(60, context.length)
        assertTrue(context.endsWith("THE-END"))
    }

    @Test
    fun returnsNullWhenNothingUsableIsOnScreen() {
        assertNull(ScreenTextExtractor.extract(emptyList(), screenWidth))
        assertNull(
            ScreenTextExtractor.extract(
                blocks = listOf(block("secret", top = 10).copy(isPassword = true)),
                screenWidth = screenWidth
            )
        )
    }

    @Test
    fun blocklistMatchesTrimmedPackageNamesCaseInsensitively() {
        val blocked = mutableListOf(" com.bitwarden.android ", "com.chase.sig.android")

        assertTrue(ScreenTextExtractor.isPackageBlocked(blocked, "com.bitwarden.android"))
        assertTrue(ScreenTextExtractor.isPackageBlocked(blocked, "COM.CHASE.SIG.ANDROID"))
        assertFalse(ScreenTextExtractor.isPackageBlocked(blocked, "com.example.app"))
        assertFalse(ScreenTextExtractor.isPackageBlocked(emptyList(), "com.example.app"))
        assertFalse(ScreenTextExtractor.isPackageBlocked(null, "com.example.app"))
        assertFalse(ScreenTextExtractor.isPackageBlocked(blocked, null))
    }
}
