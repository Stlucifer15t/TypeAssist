package com.typeassist.app.utils

import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.createDefaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * The backup format: a plain export, a password-protected one, and the ways both can go wrong.
 * This is the code that carries every setting off the device, so the round trip and the refusal
 * to open a wrong password are pinned down here.
 */
class BackupManagerTest {

    private fun sampleConfig() = createDefaultConfig().copy(
        isAppEnabled = true,
        provider = "custom",
        apiKey = "sk-test-key",
        model = "gpt-4o"
    )

    @Test
    fun aPlainBackupRestoresEveryValue() {
        val original = sampleConfig()

        val bytes = BackupManager.exportBackup(original, null)
        val (restored, needsPassword) = BackupManager.importBackup(ByteArrayInputStream(bytes), null)

        assertFalse("A plain backup must not ask for a password", needsPassword)
        assertNotNull(restored)
        assertEquals(original.apiKey, restored!!.apiKey)
        assertEquals(original.model, restored.model)
        assertEquals(original.provider, restored.provider)
        assertTrue(restored.isAppEnabled)
    }

    @Test
    fun anEncryptedBackupAsksForAPasswordWhenNoneIsGiven() {
        val bytes = BackupManager.exportBackup(sampleConfig(), "correct horse")

        val (restored, needsPassword) = BackupManager.importBackup(ByteArrayInputStream(bytes), null)

        assertTrue("The caller must be told a password is needed", needsPassword)
        assertNull("Nothing may be restored without the password", restored)
    }

    @Test
    fun anEncryptedBackupOpensWithTheRightPassword() {
        val original = sampleConfig()
        val bytes = BackupManager.exportBackup(original, "correct horse")

        val (restored, needsPassword) = BackupManager.importBackup(ByteArrayInputStream(bytes), "correct horse")

        assertFalse(needsPassword)
        assertEquals(original.apiKey, restored!!.apiKey)
        assertEquals(original.model, restored.model)
    }

    @Test
    fun aWrongPasswordDoesNotOpenTheBackup() {
        val bytes = BackupManager.exportBackup(sampleConfig(), "correct horse")

        val result = runCatching {
            BackupManager.importBackup(ByteArrayInputStream(bytes), "wrong password")
        }

        assertTrue("A wrong password must fail rather than return a config", result.isFailure)
    }

    @Test
    fun theFileIsRecognisedAsABackup() {
        val plain = BackupManager.exportBackup(sampleConfig(), null)
        val encrypted = BackupManager.exportBackup(sampleConfig(), "pw")

        assertTrue(BackupManager.isEncrypted(ByteArrayInputStream(plain)).not())
        assertTrue(BackupManager.isEncrypted(ByteArrayInputStream(encrypted)))
    }

    @Test
    fun somethingThatIsNotABackupIsRejected() {
        val result = runCatching {
            BackupManager.importBackup(ByteArrayInputStream("random text".toByteArray()), null)
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun anEmptyFileIsRejected() {
        val result = runCatching {
            BackupManager.importBackup(ByteArrayInputStream(ByteArray(0)), null)
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun theBackupFileIsNeverEmpty() {
        assertTrue(BackupManager.exportBackup(AppConfig(), null).isNotEmpty())
        assertTrue(BackupManager.exportBackup(AppConfig(), "pw").isNotEmpty())
    }
}
