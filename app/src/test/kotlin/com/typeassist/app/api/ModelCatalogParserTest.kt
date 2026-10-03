package com.typeassist.app.api

import com.typeassist.app.data.ModelSelectionPreference
import com.typeassist.app.data.ModelSelectionPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogParserTest {
    @Test
    fun parsesAndSortsOpenAiCompatibleIds() {
        val models = ModelCatalogParser.parseOpenAiCompatibleModels(
            """{"data":[{"id":"gpt-z"},{"id":"gpt-a"},{"id":"gpt-z"},{"object":"model"}]}"""
        )
        assertEquals(listOf("gpt-a", "gpt-z"), models)
    }

    @Test
    fun supportsSimpleModelsArray() {
        assertEquals(
            listOf("model-one"),
            ModelCatalogParser.parseOpenAiCompatibleModels("""{"models":[{"id":"model-one"}]}""")
        )
    }

    @Test
    fun parsesOnlyGeminiModelsSupportingGenerateContent() {
        val models = ModelCatalogParser.parseGeminiModels(
            """{"models":[
                {"name":"models/gemini-fast","supportedGenerationMethods":["generateContent"]},
                {"name":"models/embedding-only","supportedGenerationMethods":["embedContent"]},
                {"name":"models/gemini-basic"}
            ]}"""
        )
        assertEquals(listOf("gemini-fast"), models)
    }

    @Test
    fun normalizesModelEndpointsWithoutLosingApiVersion() {
        assertEquals(
            "https://api.openai.com/v1/models",
            ModelCatalogClient.modelsEndpoint("https://api.openai.com/v1/").toString()
        )
        assertEquals(
            "https://example.test/v1/models",
            ModelCatalogClient.modelsEndpoint("https://example.test/v1/chat/completions").toString()
        )
        assertEquals(
            "https://example.test/v1/models",
            ModelCatalogClient.modelsEndpoint("https://example.test/v1/models").toString()
        )
    }

    @Test
    fun preferencesAreScopedAndFavoritesSurviveRecentUpdates() {
        var preferences: MutableList<ModelSelectionPreference> = mutableListOf()
        preferences = ModelSelectionPreferences.toggleFavorite(preferences, "custom", "https://api.one/v1", "model-a")
        preferences = ModelSelectionPreferences.recordSelection(preferences, "custom", "https://api.one/v1/", "model-a", 100)
        preferences = ModelSelectionPreferences.recordSelection(preferences, "custom", "https://api.one/v1", "model-b", 200)

        val endpointModels = ModelSelectionPreferences.forEndpoint(preferences, "custom", "https://api.one/v1")
        assertTrue(endpointModels.first { it.modelId == "model-a" }.isFavorite)
        assertEquals(100L, endpointModels.first { it.modelId == "model-a" }.lastSelectedAt)
        assertEquals(listOf("model-a", "model-b").toSet(), endpointModels.map { it.modelId }.toSet())
        assertTrue(ModelSelectionPreferences.forEndpoint(preferences, "custom", "https://api.one/v1/models").isNotEmpty())
        assertTrue(ModelSelectionPreferences.forEndpoint(preferences, "custom", "https://api.two/v1").isEmpty())

        preferences = ModelSelectionPreferences.toggleFavorite(preferences, "custom", "https://api.one/v1", "model-a")
        assertFalse(ModelSelectionPreferences.forEndpoint(preferences, "custom", "https://api.one/v1").first { it.modelId == "model-a" }.isFavorite)
    }

    @Test
    fun formatsCommonConnectionErrorsActionably() {
        assertTrue(ApiErrorFormatter.explain(IllegalStateException("HTTP 401: unauthorized")).contains("API key"))
        assertTrue(ApiErrorFormatter.explain(IllegalStateException("HTTP 429: limit")).contains("rate-limiting"))
        assertTrue(ApiErrorFormatter.explain(ModelCatalogHttpException(404, "" )).contains("model ID manually"))
    }
}
