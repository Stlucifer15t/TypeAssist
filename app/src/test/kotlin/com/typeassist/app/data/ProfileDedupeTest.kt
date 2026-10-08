package com.typeassist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDedupeTest {

    // --- identity keys ---

    @Test
    fun customIdentityIgnoresTrailingPathNoise() {
        val plain = CustomApiConfig(baseUrl = "https://api.openai.com/v1", apiKey = "sk-1", model = "gpt-4o")
        val trailingSlash = CustomApiConfig(baseUrl = "https://api.openai.com/v1/", apiKey = "sk-1", model = "gpt-4o-mini")
        val modelsPath = CustomApiConfig(baseUrl = "https://api.openai.com/v1/models", apiKey = "sk-1", model = "gpt-4o")
        val completions = CustomApiConfig(baseUrl = "https://api.openai.com/v1/chat/completions", apiKey = "sk-1", model = "gpt-4o")

        assertEquals(plain.identityKey(), trailingSlash.identityKey())
        assertEquals(plain.identityKey(), modelsPath.identityKey())
        assertEquals(plain.identityKey(), completions.identityKey())
    }

    @Test
    fun customIdentitySeparatesDifferentKeysAndEndpoints() {
        val base = CustomApiConfig(baseUrl = "https://api.openai.com/v1", apiKey = "sk-1", model = "gpt-4o")
        val otherKey = base.copy(apiKey = "sk-2")
        val otherHost = base.copy(baseUrl = "https://openrouter.ai/api/v1")

        assertTrue(base.identityKey() != otherKey.identityKey())
        assertTrue(base.identityKey() != otherHost.identityKey())
    }

    @Test
    fun geminiAndCloudflareIdentitiesUseTheirCredentials() {
        assertEquals(
            SavedGeminiConfig(apiKey = " key ", model = "a").identityKey(),
            SavedGeminiConfig(apiKey = "key", model = "b").identityKey()
        )
        assertTrue(
            SavedGeminiConfig(apiKey = "key-1").identityKey() !=
                SavedGeminiConfig(apiKey = "key-2").identityKey()
        )

        val account = CloudflareConfig(accountId = "acct", apiToken = "token", model = "@cf/meta/llama-3-8b-instruct")
        assertEquals(account.identityKey(), account.copy(model = "@cf/other").identityKey())
        assertTrue(account.identityKey() != account.copy(apiToken = "other").identityKey())
        assertTrue(account.identityKey() != account.copy(accountId = "other").identityKey())
    }

    // --- mergeDuplicateProfiles ---

    @Test
    fun sameEndpointAndKeyCollapsesToOneProfileKeepingTheNewestModel() {
        val config = createDefaultConfig().copy(
            savedCustomConfigs = mutableListOf(
                CustomApiConfig("https://api.openai.com/v1", "sk-1", "gpt-4o"),
                CustomApiConfig("https://api.openai.com/v1/", "sk-1", "gpt-4o-mini"),
                CustomApiConfig("https://api.openai.com/v1/models", "sk-1", "o3-mini")
            )
        )

        val merged = mergeDuplicateProfiles(config)

        assertEquals(1, merged.savedCustomConfigs.size)
        // Last saved entry wins, so the model the user actually picked is the one kept.
        assertEquals("o3-mini", merged.savedCustomConfigs.single().model)
    }

    @Test
    fun mergeKeepsDistinctEndpointsAndKeys() {
        val config = createDefaultConfig().copy(
            savedCustomConfigs = mutableListOf(
                CustomApiConfig("https://api.openai.com/v1", "sk-1", "gpt-4o"),
                CustomApiConfig("https://api.openai.com/v1", "sk-2", "gpt-4o"),
                CustomApiConfig("https://openrouter.ai/api/v1", "sk-1", "llama-3.3-70b")
            )
        )

        val merged = mergeDuplicateProfiles(config)

        assertEquals(3, merged.savedCustomConfigs.size)
    }

    @Test
    fun mergePreservesThePositionOfTheFirstOccurrence() {
        val config = createDefaultConfig().copy(
            savedCustomConfigs = mutableListOf(
                CustomApiConfig("https://one.example/v1", "k", "m1"),
                CustomApiConfig("https://two.example/v1", "k", "m2"),
                CustomApiConfig("https://one.example/v1", "k", "m1-new")
            ),
            savedGeminiConfigs = mutableListOf(
                SavedGeminiConfig("gem-key", "gemini-2.0-flash"),
                SavedGeminiConfig("gem-key", "gemini-2.5-pro"),
                SavedGeminiConfig("other-key", "gemini-2.0-flash")
            ),
            savedCloudflareConfigs = mutableListOf(
                CloudflareConfig("acct", "token", "@cf/one"),
                CloudflareConfig("acct", "token", "@cf/two")
            ),
            savedLocalModels = mutableListOf("content://models/a.gguf", " content://models/a.gguf ", "content://models/b.gguf")
        )

        val merged = mergeDuplicateProfiles(config)

        assertEquals(listOf("https://one.example/v1", "https://two.example/v1"), merged.savedCustomConfigs.map { it.baseUrl })
        assertEquals("m1-new", merged.savedCustomConfigs.first().model)
        assertEquals(listOf("gem-key", "other-key"), merged.savedGeminiConfigs.map { it.apiKey })
        assertEquals("gemini-2.5-pro", merged.savedGeminiConfigs.first().model)
        assertEquals(1, merged.savedCloudflareConfigs.size)
        assertEquals("@cf/two", merged.savedCloudflareConfigs.single().model)
        assertEquals(listOf("content://models/a.gguf", "content://models/b.gguf"), merged.savedLocalModels)
    }

    @Test
    fun mergeReturnsTheSameInstanceWhenThereIsNothingToDo() {
        val config = createDefaultConfig().copy(
            savedCustomConfigs = mutableListOf(CustomApiConfig("https://one.example/v1", "k", "m1")),
            savedGeminiConfigs = mutableListOf(SavedGeminiConfig("gem-key", "gemini-2.0-flash"))
        )

        assertSame(config, mergeDuplicateProfiles(config))
    }

    @Test
    fun mergeDropsBlankLocalModelEntries() {
        val config = createDefaultConfig().copy(
            savedLocalModels = mutableListOf("", "   ", "content://models/a.gguf")
        )

        val merged = mergeDuplicateProfiles(config)

        assertNotSame(config, merged)
        assertEquals(listOf("content://models/a.gguf"), merged.savedLocalModels)
    }

    @Test
    fun mergeKeepsOtherConfigFieldsUntouched() {
        val config = createDefaultConfig().copy(
            provider = "custom",
            apiKey = "sk-1",
            model = "gpt-4o",
            snippets = mutableListOf(Snippet("email", contents = mutableListOf("user@example.com"))),
            savedCustomConfigs = mutableListOf(
                CustomApiConfig("https://one.example/v1", "k", "m1"),
                CustomApiConfig("https://one.example/v1", "k", "m2")
            )
        )

        val merged = mergeDuplicateProfiles(config)

        assertEquals("custom", merged.provider)
        assertEquals("sk-1", merged.apiKey)
        assertEquals("gpt-4o", merged.model)
        assertEquals(1, merged.snippets.size)
    }

    // --- model history ---

    @Test
    fun sanitizeCollapsesDuplicateModelEntriesAndMergesFlags() {
        val preferences = mutableListOf(
            ModelSelectionPreference("custom", "https://one.example/v1", "gpt-4o", isFavorite = false, lastSelectedAt = 10),
            ModelSelectionPreference("custom", "https://one.example/v1/", "GPT-4O", isFavorite = true, lastSelectedAt = 20),
            ModelSelectionPreference("custom", "https://one.example/v1", "gpt-4o-mini", lastSelectedAt = 30)
        )

        val sanitized = ModelSelectionPreferences.sanitize(preferences)

        assertEquals(2, sanitized.size)
        val gpt4o = sanitized.first { it.modelId.equals("gpt-4o", ignoreCase = true) }
        assertTrue(gpt4o.isFavorite)
        assertEquals(20L, gpt4o.lastSelectedAt)
        assertEquals("https://one.example/v1", gpt4o.endpoint)
    }

    @Test
    fun sanitizeKeepsSameModelOnDifferentEndpointsApart() {
        val preferences = mutableListOf(
            ModelSelectionPreference("custom", "https://one.example/v1", "gpt-4o"),
            ModelSelectionPreference("custom", "https://two.example/v1", "gpt-4o"),
            ModelSelectionPreference("gemini", "gemini", "gpt-4o")
        )

        assertEquals(3, ModelSelectionPreferences.sanitize(preferences).size)
    }

    @Test
    fun sanitizeDropsBlankEntries() {
        val preferences = mutableListOf(
            ModelSelectionPreference("custom", "https://one.example/v1", "   "),
            ModelSelectionPreference("", "https://one.example/v1", "gpt-4o"),
            ModelSelectionPreference("custom", "https://one.example/v1", "gpt-4o")
        )

        val sanitized = ModelSelectionPreferences.sanitize(preferences)

        assertEquals(1, sanitized.size)
        assertEquals("gpt-4o", sanitized.single().modelId)
    }
}
