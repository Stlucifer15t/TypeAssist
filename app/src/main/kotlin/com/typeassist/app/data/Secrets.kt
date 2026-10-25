package com.typeassist.app.data

/**
 * ============================== SECRETS ==============================
 *
 * The API keys are the one thing in the settings that must not be readable by anyone who gets
 * hold of the phone's storage, so they are kept encrypted on disk (see [SecretStore]).
 *
 * This object decides *which* values are secret and moves them between their plain and their
 * encrypted form. It is free of Android imports, so the rules can be unit tested; the actual
 * cryptography lives in [SecretStore].
 *
 * An encrypted value carries a prefix, so a config written by an older version - whose keys are
 * still plain text - is recognised as such and migrated the next time it is saved.
 *
 * Every field is read tolerantly (Gson can leave fields that did not exist in an older config
 * as null at runtime), so a half-loaded config cannot crash the encryption.
 */
object Secrets {

    /** Marks a value as encrypted. */
    const val PREFIX: String = "enc1:"

    /** True when the value is stored in its encrypted form. */
    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    /** True when any key in the config is still stored in plain text. */
    fun hasPlainSecrets(config: AppConfig): Boolean =
        secretValues(config).any { it.isNotEmpty() && !isEncrypted(it) }

    /**
     * A copy of the config with every key encrypted. The original is left alone: it is the
     * in-memory config the app works with, and it holds the keys in plain text.
     */
    fun encryptConfig(config: AppConfig, encrypt: (String) -> String): AppConfig = config.copy(
        apiKey = encryptValue(config.apiKey.orEmpty(), encrypt),
        customApiConfig = (config.customApiConfig ?: CustomApiConfig()).copy(
            apiKey = encryptValue(config.customApiConfig?.apiKey.orEmpty(), encrypt)
        ),
        cloudflareConfig = (config.cloudflareConfig ?: CloudflareConfig()).copy(
            apiToken = encryptValue(config.cloudflareConfig?.apiToken.orEmpty(), encrypt)
        ),
        savedCustomConfigs = (config.savedCustomConfigs.orEmpty())
            .map { it.copy(apiKey = encryptValue(it.apiKey, encrypt)) }
            .toMutableList(),
        savedGeminiConfigs = (config.savedGeminiConfigs.orEmpty())
            .map { it.copy(apiKey = encryptValue(it.apiKey, encrypt)) }
            .toMutableList(),
        savedCloudflareConfigs = (config.savedCloudflareConfigs.orEmpty())
            .map { it.copy(apiToken = encryptValue(it.apiToken, encrypt)) }
            .toMutableList()
    )

    /** A copy of the config with every key back in plain text. */
    fun decryptConfig(config: AppConfig, decrypt: (String) -> String): AppConfig = config.copy(
        apiKey = decryptValue(config.apiKey.orEmpty(), decrypt),
        customApiConfig = config.customApiConfig?.let { nested ->
            nested.copy(apiKey = decryptValue(nested.apiKey.orEmpty(), decrypt))
        } ?: CustomApiConfig(),
        cloudflareConfig = config.cloudflareConfig?.let { nested ->
            nested.copy(apiToken = decryptValue(nested.apiToken.orEmpty(), decrypt))
        } ?: com.typeassist.app.data.CloudflareConfig(),
        savedCustomConfigs = (config.savedCustomConfigs.orEmpty())
            .map { it.copy(apiKey = decryptValue(it.apiKey.orEmpty(), decrypt)) }
            .toMutableList(),
        savedGeminiConfigs = (config.savedGeminiConfigs.orEmpty())
            .map { it.copy(apiKey = decryptValue(it.apiKey.orEmpty(), decrypt)) }
            .toMutableList(),
        savedCloudflareConfigs = (config.savedCloudflareConfigs.orEmpty())
            .map { it.copy(apiToken = decryptValue(it.apiToken.orEmpty(), decrypt)) }
            .toMutableList()
    )

    /** Encrypts a value, unless it is empty or already encrypted. */
    private fun encryptValue(value: String, transform: (String) -> String): String =
        if (value.isEmpty() || isEncrypted(value)) value else transform(value)

    /** Decrypts a value, unless it is empty or was never encrypted. */
    private fun decryptValue(value: String, transform: (String) -> String): String =
        if (value.isEmpty() || !isEncrypted(value)) value else transform(value)

    /** Every value in the config that has to be kept secret. */
    private fun secretValues(config: AppConfig): List<String> = buildList {
        add(config.apiKey.orEmpty())
        add(config.customApiConfig?.apiKey.orEmpty())
        add(config.cloudflareConfig?.apiToken.orEmpty())
        config.savedCustomConfigs.orEmpty().forEach { add(it.apiKey.orEmpty()) }
        config.savedGeminiConfigs.orEmpty().forEach { add(it.apiKey.orEmpty()) }
        config.savedCloudflareConfigs.orEmpty().forEach { add(it.apiToken.orEmpty()) }
    }
}
