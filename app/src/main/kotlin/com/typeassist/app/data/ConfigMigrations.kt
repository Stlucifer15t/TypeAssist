package com.typeassist.app.data

/**
 * Version of the persisted [AppConfig] shape. Bump it whenever new fields are added whose default
 * value cannot be reconstructed after a reload: Gson builds configs without running the
 * constructor, so a field that is missing from an older `config_json` arrives as `false` / `0` /
 * `null` instead of its Kotlin default (the same problem [LoadingIndicatorStyle.sanitize] solves
 * for the indicator colour and size).
 */
const val CURRENT_CONFIG_VERSION: Int = 5

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
        // The version the config was written with. The blocks below bump the stored version as they
        // go, so the original number is what says which repairs are still owed.
        val writtenVersion = result.configVersion

        // Configs written before version 2 predate the result chip, streaming and screen context,
        // so those settings must start from their intended defaults instead of Gson's zero values.
        if (result.configVersion < 2) {
            result = result.copy(
                showResultChip = true,
                streamResponses = true,
                screenContextChatLabels = true
            )
        }

        // A config from a newer version keeps its number: only its own build knows those fields.
        if (result.configVersion < CURRENT_CONFIG_VERSION) {
            result = result.copy(configVersion = CURRENT_CONFIG_VERSION)
        }

        // A missing (or cleared) blocklist must never leave screen reading unguarded.
        if (result.screenContextBlockedPackages == null) {
            result = result.copy(screenContextBlockedPackages = defaultBlockedScreenPackages())
        }

        // Version 5 added the neon look: a toggle that applies the neon treatment to any style,
        // plus the neon colour the spectrum is built from. The colour's default is a non-zero
        // hue, so a config written before this version arrives with 0 and must be repaired.
        if (writtenVersion < 5) {
            var style = result.loadingIndicatorStyle
            var neon = result.loadingIndicatorNeon
            // The old "neon" style is now the spectrum ring with the neon look switched on.
            if (style == LoadingIndicatorStyle.LEGACY_STYLE_NEON) {
                style = LoadingIndicatorStyle.STYLE_SPECTRUM
                neon = true
            }
            result = result.copy(
                loadingIndicatorStyle = style,
                loadingIndicatorNeon = neon,
                loadingIndicatorNeonColor = LoadingIndicatorStyle.sanitizeNeonColor(
                    result.loadingIndicatorNeonColor
                )
            )
        }

        // Version 3 added the generation parameters, the reply style and the history retention.
        // Their defaults cannot come from the Kotlin constructor on a loaded config, so they are
        // repaired here: a missing `generationConfig` would otherwise be dereferenced by the API
        // clients, and a missing retention would expire the history instantly. Everything in this
        // block returns the same instance when there is nothing to fix.
        if (result.generationConfig == null) {
            result = result.copy(generationConfig = GenerationSettings.defaults())
        } else {
            val sanitizedGeneration = GenerationSettings.sanitize(result.generationConfig)
            if (sanitizedGeneration != result.generationConfig) {
                result = result.copy(generationConfig = sanitizedGeneration)
            }
        }

        val tone = ReplyStyle.sanitizeTone(result.replyTone)
        if (result.replyTone != tone) result = result.copy(replyTone = tone)

        val length = ReplyStyle.sanitizeLength(result.replyLength)
        if (result.replyLength != length) result = result.copy(replyLength = length)

        val retention = HistoryManager.sanitizeRetentionMinutes(result.historyRetentionMinutes)
        if (result.historyRetentionMinutes != retention) {
            result = result.copy(historyRetentionMinutes = retention)
        }

        return result
    }
}
