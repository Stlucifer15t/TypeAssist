package com.typeassist.app.utils

/**
 * ============================== COMMAND GUARD ==============================
 *
 * Decides whether a command may run in the field the user is typing in.
 *
 * Two independent rules, with different scopes:
 *
 *  * **Password field** — *nothing* runs there. Not an AI command, not an offline
 *    tool, and nothing is written to the history. A password field's text must
 *    never be captured, stored, or sent anywhere, and the cheapest way to
 *    guarantee that is to treat the field as untouchable.
 *
 *  * **Blocked app** — only *AI* commands are refused. The app is on the user's
 *    "never touch this app" list, so nothing may be sent to a provider from
 *    there. Offline tools (case changes, word count, the clipboard) stay
 *    available, because they never leave the phone and the app would otherwise
 *    feel broken for no reason.
 *
 * Kept free of Android imports so both rules can be unit tested; the service
 * reads the two facts it needs (`AccessibilityNodeInfo.isPassword` and the
 * package name) and hands them over.
 */
object CommandGuard {

    /** Why a command was refused. */
    enum class Refusal {
        /** Nothing is wrong; the command may run. */
        NONE,

        /** The field is a password field: no command may touch it. */
        PASSWORD_FIELD,

        /** The app is on the user's blocklist: only AI commands are refused. */
        BLOCKED_APP
    }

    /**
     * True when *any* command may run in the field. Password fields are never
     * touched; nothing else is excluded here.
     */
    fun allowsAnyCommand(isPassword: Boolean): Boolean = !isPassword

    /**
     * True when an AI command may run. Refused in password fields and in apps
     * on the user's blocklist.
     */
    fun allowsAiCommand(
        isPassword: Boolean,
        blockedPackages: List<String>?,
        packageName: String?
    ): Boolean = refusal(isPassword, blockedPackages, packageName) == Refusal.NONE

    /**
     * The single decision behind [allowsAnyCommand] and [allowsAiCommand]:
     * what, if anything, stops a command here.
     */
    fun refusal(
        isPassword: Boolean,
        blockedPackages: List<String>? = null,
        packageName: String? = null
    ): Refusal {
        if (isPassword) return Refusal.PASSWORD_FIELD
        if (ScreenTextExtractor.isPackageBlocked(blockedPackages, packageName)) {
            return Refusal.BLOCKED_APP
        }
        return Refusal.NONE
    }

    /** The wording shown to the user when a command is refused. */
    fun message(refusal: Refusal): String = when (refusal) {
        Refusal.PASSWORD_FIELD -> "Prompt AI never sends text from password fields"
        Refusal.BLOCKED_APP -> "Prompt AI is switched off in this app"
        Refusal.NONE -> ""
    }
}
