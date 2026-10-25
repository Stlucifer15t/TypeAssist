package com.typeassist.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep private text away from AI providers, pinned down here.
 *
 * A password field must never be touched at all - not by an AI command, not by
 * an offline tool, and nothing may be written to the history. An app on the
 * blocklist refuses only the AI commands, so the offline tools keep working.
 */
class CommandGuardTest {

    @Test
    fun noCommandRunsInAPasswordField() {
        assertFalse(CommandGuard.allowsAnyCommand(isPassword = true))
        assertFalse(CommandGuard.allowsAiCommand(isPassword = true, blockedPackages = null, packageName = null))
    }

    @Test
    fun ordinaryFieldsAllowCommands() {
        assertTrue(CommandGuard.allowsAnyCommand(isPassword = false))
        assertTrue(
            CommandGuard.allowsAiCommand(
                isPassword = false,
                blockedPackages = null,
                packageName = "com.example.chat"
            )
        )
    }

    @Test
    fun aPasswordFieldIsRefusedEvenWhenTheAppIsAllowed() {
        assertEquals(
            CommandGuard.Refusal.PASSWORD_FIELD,
            CommandGuard.refusal(
                isPassword = true,
                blockedPackages = listOf("com.example.bank"),
                packageName = "com.example.chat"
            )
        )
    }

    @Test
    fun aBlockedAppRefusesAiCommands() {
        val blocked = listOf("com.example.bank", "com.example.passwords")

        assertFalse(
            CommandGuard.allowsAiCommand(
                isPassword = false,
                blockedPackages = blocked,
                packageName = "com.example.bank"
            )
        )
        assertEquals(
            CommandGuard.Refusal.BLOCKED_APP,
            CommandGuard.refusal(
                isPassword = false,
                blockedPackages = blocked,
                packageName = "com.example.bank"
            )
        )
    }

    @Test
    fun aBlockedAppStillAllowsTheOfflineTools() {
        // Offline tools never leave the phone, so a blocked app keeps working for them.
        assertTrue(CommandGuard.allowsAnyCommand(isPassword = false))
    }

    @Test
    fun anAppThatIsNotOnTheListIsFine() {
        assertTrue(
            CommandGuard.allowsAiCommand(
                isPassword = false,
                blockedPackages = listOf("com.example.bank"),
                packageName = "com.example.chat"
            )
        )
        assertEquals(
            CommandGuard.Refusal.NONE,
            CommandGuard.refusal(
                isPassword = false,
                blockedPackages = listOf("com.example.bank"),
                packageName = "com.example.chat"
            )
        )
    }

    @Test
    fun theBlocklistIgnoresCaseAndSurroundingSpace() {
        assertFalse(
            CommandGuard.allowsAiCommand(
                isPassword = false,
                blockedPackages = listOf(" com.example.Bank "),
                packageName = "com.example.bank"
            )
        )
    }

    @Test
    fun anUnknownPackageIsNeverBlocked() {
        assertTrue(
            CommandGuard.allowsAiCommand(
                isPassword = false,
                blockedPackages = listOf("com.example.bank"),
                packageName = null
            )
        )
        assertTrue(
            CommandGuard.allowsAiCommand(
                isPassword = false,
                blockedPackages = listOf("com.example.bank"),
                packageName = ""
            )
        )
    }

    @Test
    fun everyRefusalHasItsOwnWording() {
        assertTrue(CommandGuard.message(CommandGuard.Refusal.PASSWORD_FIELD).contains("password"))
        assertTrue(CommandGuard.message(CommandGuard.Refusal.BLOCKED_APP).contains("this app"))
        assertEquals("", CommandGuard.message(CommandGuard.Refusal.NONE))
    }
}
