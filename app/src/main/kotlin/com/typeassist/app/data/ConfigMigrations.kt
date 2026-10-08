package com.typeassist.app.data

/**
 * Version of the persisted [AppConfig] shape. Bump it whenever new fields are added whose default
 * value cannot be reconstructed after a reload: Gson builds configs without running the
 * constructor, so a field that is missing from an older `config_json` arrives as `false` / `0` /
 * `null` instead of its Kotlin default (the same problem [LoadingIndicatorStyle.sanitize] solves
 * for the indicator colour and size).
 */
const val CURRENT_CONFIG_VERSION: Int = 2

/**
 * Apps where screen reading is never used, preloaded into Settings → Screen context.
 * A short list of common banking and password-manager packages: users can add or remove entries.
 */
fun defaultBlockedScreenPackages(): MutableList<String> = mutableListOf(
    // Banking and payments
    "com.chase.sig.android",
    "com.wf.wellsfargomobile",
    "com.infonow.bofa",
    "com.bankofamerica.cashpromobile",
    "com.usbank.mobilebanking",
    "com.citi.citimobile",
    "com.paypal.android.p2pmobile",
    "com.venmo",
    "com.squareup.cash",
    "com.revolut.revolut",
    "com.monzo.android",
    // Password managers and authenticators
    "com.bitwarden.android",
    "com.x8bit.bitwarden",
    "com.lastpass.lpandroid",
    "com.onepassword.android",
    "com.agilebits.onepassword",
    "com.dashlane",
    "com.keepersecurity.passwordmanager",
    "com.nordpass.android.app.password.manager",
    "org.keepassdroid",
    "com.google.android.apps.authenticator2",
    "com.authy.authy"
)

/**
 * Applies the changes needed to bring a config loaded from disk (or from a `.tabak` backup) up to
 * the current shape. Only fields that cannot fall back to their Kotlin defaults need entries here.
 *
 * Returns the same instance when nothing had to change, so callers can compare by reference.
 */
object ConfigMigrations {

    fun apply(config: AppConfig): AppConfig {
        var result = config

        // Configs written before version 2 predate the result chip, streaming and screen context,
        // so those settings must start from their intended defaults instead of Gson's zero values.
        // A config from a newer version is left untouched (only its own app knows those fields).
        if (result.configVersion < CURRENT_CONFIG_VERSION) {
            result = result.copy(
                configVersion = CURRENT_CONFIG_VERSION,
                showResultChip = true,
                streamResponses = true,
                screenContextChatLabels = true
            )
        }

        // A missing (or cleared) blocklist must never leave screen reading unguarded.
        if (result.screenContextBlockedPackages == null) {
            result = result.copy(screenContextBlockedPackages = defaultBlockedScreenPackages())
        }

        return result
    }
}
