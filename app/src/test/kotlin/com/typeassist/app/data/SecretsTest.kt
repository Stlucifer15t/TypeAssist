package com.typeassist.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep the API keys out of the readable settings file, using a reversible fake in
 * place of the keystore: an "encrypted" value here is just the plain text wrapped in brackets, so
 * the rules themselves - what gets encrypted, what stays as it is, and what comes back - are what
 * is being tested.
 */
class SecretsTest {

    /** A stand-in for the real encryption: visible, reversible, and clearly marked. */
    private fun fakeEncrypt(value: String): String = Secrets.PREFIX + "[" + value + "]"
    private fun fakeDecrypt(value: String): String =
        value.removePrefix(Secrets.PREFIX).removeSurrounding("[", "]")

    private fun sampleConfig() = AppConfig(
        apiKey = "gemini-key",
        customApiConfig = CustomApiConfig(apiKey = "custom-key"),
        cloudflareConfig = CloudflareConfig(apiToken = "cf-token")
    ).copy(
        savedCustomConfigs = mutableListOf(CustomApiConfig(apiKey = "saved-custom")),
        savedGeminiConfigs = mutableListOf(SavedGeminiConfig(apiKey = "saved-gemini")),
        savedCloudflareConfigs = mutableListOf(CloudflareConfig(apiToken = "saved-cf"))
    )

    @Test
    fun everyKeyIsEncryptedOnSave() {
        val encrypted = Secrets.encryptConfig(sampleConfig(), ::fakeEncrypt)

        assertTrue(Secrets.isEncrypted(encrypted.apiKey))
        assertTrue(Secrets.isEncrypted(encrypted.customApiConfig.apiKey))
        assertTrue(Secrets.isEncrypted(encrypted.cloudflareConfig.apiToken))
        assertTrue(encrypted.savedCustomConfigs.all { Secrets.isEncrypted(it.apiKey) })
        assertTrue(encrypted.savedGeminiConfigs.all { Secrets.isEncrypted(it.apiKey) })
        assertTrue(encrypted.savedCloudflareConfigs.all { Secrets.isEncrypted(it.apiToken) })
    }

    @Test
    fun decryptingBringsEveryKeyBack() {
        val config = sampleConfig()

        val roundTrip = Secrets.decryptConfig(
            Secrets.encryptConfig(config, ::fakeEncrypt),
            ::fakeDecrypt
        )

        assertEquals(config.apiKey, roundTrip.apiKey)
        assertEquals(config.customApiConfig.apiKey, roundTrip.customApiConfig.apiKey)
        assertEquals(config.cloudflareConfig.apiToken, roundTrip.cloudflareConfig.apiToken)
        assertEquals(config.savedGeminiConfigs[0].apiKey, roundTrip.savedGeminiConfigs[0].apiKey)
        assertEquals(config.savedCustomConfigs[0].apiKey, roundTrip.savedCustomConfigs[0].apiKey)
        assertEquals(config.savedCloudflareConfigs[0].apiToken, roundTrip.savedCloudflareConfigs[0].apiToken)
    }

    @Test
    fun theInMemoryConfigIsNeverChanged() {
        val config = AppConfig(apiKey = "gemini-key")

        Secrets.encryptConfig(config, ::fakeEncrypt)

        assertEquals("gemini-key", config.apiKey)
    }

    @Test
    fun emptyKeysAreLeftAlone() {
        val config = AppConfig(apiKey = "", customApiConfig = CustomApiConfig(apiKey = ""))

        val encrypted = Secrets.encryptConfig(config, ::fakeEncrypt)

        assertEquals("", encrypted.apiKey)
        assertEquals("", encrypted.customApiConfig.apiKey)
    }

    @Test
    fun aValueThatIsAlreadyEncryptedIsNotEncryptedTwice() {
        val config = AppConfig(apiKey = Secrets.PREFIX + "already-encrypted")

        val encrypted = Secrets.encryptConfig(config, ::fakeEncrypt)

        assertEquals(Secrets.PREFIX + "already-encrypted", encrypted.apiKey)
    }

    @Test
    fun aLegacyConfigWithPlainKeysIsDetected() {
        val legacy = AppConfig(
            apiKey = "sk-legacy",
            customApiConfig = CustomApiConfig(apiKey = "sk-custom")
        )

        assertTrue(Secrets.hasPlainSecrets(legacy))
    }

    @Test
    fun anEncryptedConfigHasNoPlainSecrets() {
        val migrated = Secrets.encryptConfig(sampleConfig(), ::fakeEncrypt)

        assertFalse(Secrets.hasPlainSecrets(migrated))
    }

    @Test
    fun aConfigWithNoKeysHasNoPlainSecrets() {
        assertFalse(Secrets.hasPlainSecrets(AppConfig()))
    }

    @Test
    fun aRoundTripKeepsEverythingButTheKeys() {
        val config = AppConfig(
            apiKey = "gemini-key",
            customApiConfig = CustomApiConfig(apiKey = "custom-key", model = "gpt-x"),
            cloudflareConfig = CloudflareConfig(apiToken = "cf-token", accountId = "acc")
        )

        val roundTripped = Secrets.decryptConfig(
            Secrets.encryptConfig(config, ::fakeEncrypt),
            ::fakeDecrypt
        )

        assertEquals("gemini-key", roundTripped.apiKey)
        assertEquals("custom-key", roundTripped.customApiConfig.apiKey)
        assertEquals("gpt-x", roundTripped.customApiConfig.model)
        assertEquals("cf-token", roundTripped.cloudflareConfig.apiToken)
        assertEquals("acc", roundTripped.cloudflareConfig.accountId)
    }

    @Test
    fun valuesThatWereNeverEncryptedPassThroughUnchanged() {
        assertEquals("plain-key", fakeDecrypt("plain-key"))
        assertFalse(Secrets.isEncrypted("plain-key"))
        assertTrue(Secrets.isEncrypted(Secrets.PREFIX + "whatever"))
    }

    @Test
    fun aConfigWithMissingFieldsDoesNotCrashTheEncryption() {
        // Gson can leave fields that did not exist in an older config as null at runtime.
        val config = Gson().fromJson("""{"isAppEnabled":true}""", AppConfig::class.java)

        val encrypted = Secrets.encryptConfig(config, ::fakeEncrypt)
        val decrypted = Secrets.decryptConfig(encrypted, ::fakeDecrypt)

        assertEquals("", encrypted.apiKey)
        assertEquals("", decrypted.apiKey)
        assertEquals("", encrypted.customApiConfig.apiKey)
        assertEquals("", decrypted.cloudflareConfig.apiToken)
    }
}
