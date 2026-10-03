package com.typeassist.app.data

import java.io.Serializable

/** Per-provider model history; endpoint scoping prevents mixing IDs from unrelated custom APIs. */
data class ModelSelectionPreference(
    var provider: String = "",
    var endpoint: String = "",
    var modelId: String = "",
    var isFavorite: Boolean = false,
    var lastSelectedAt: Long = 0L
) : Serializable

object ModelSelectionPreferences {
    private const val MAX_STORED_MODELS = 100

    fun forEndpoint(
        preferences: List<ModelSelectionPreference>?,
        provider: String,
        endpoint: String
    ): List<ModelSelectionPreference> {
        val key = endpointKey(provider, endpoint)
        return preferences.orEmpty()
            .filter { it.provider == provider && it.endpoint == key && it.modelId.isNotBlank() }
    }

    fun recordSelection(
        preferences: List<ModelSelectionPreference>?,
        provider: String,
        endpoint: String,
        modelId: String,
        selectedAt: Long = System.currentTimeMillis()
    ): MutableList<ModelSelectionPreference> {
        val model = modelId.trim()
        if (model.isBlank()) return preferences.orEmpty().toMutableList()
        val key = endpointKey(provider, endpoint)
        val entries = preferences.orEmpty().toMutableList()
        val index = entries.indexOfFirst {
            it.provider == provider && it.endpoint == key && it.modelId.equals(model, ignoreCase = true)
        }
        val existing = if (index >= 0) entries.removeAt(index) else null
        entries.add(
            existing?.copy(modelId = model, lastSelectedAt = selectedAt)
                ?: ModelSelectionPreference(provider, key, model, isFavorite = false, lastSelectedAt = selectedAt)
        )
        return trim(entries)
    }

    fun toggleFavorite(
        preferences: List<ModelSelectionPreference>?,
        provider: String,
        endpoint: String,
        modelId: String
    ): MutableList<ModelSelectionPreference> {
        val model = modelId.trim()
        if (model.isBlank()) return preferences.orEmpty().toMutableList()
        val key = endpointKey(provider, endpoint)
        val entries = preferences.orEmpty().toMutableList()
        val index = entries.indexOfFirst {
            it.provider == provider && it.endpoint == key && it.modelId.equals(model, ignoreCase = true)
        }
        if (index >= 0) {
            val current = entries[index]
            entries[index] = current.copy(isFavorite = !current.isFavorite)
        } else {
            entries.add(ModelSelectionPreference(provider, key, model, isFavorite = true))
        }
        return trim(entries)
    }

    fun endpointKey(provider: String, endpoint: String): String {
        if (provider == "gemini") return "gemini"
        return endpoint.trim()
            .removeSuffix("/")
            .removeSuffix("/chat/completions")
            .removeSuffix("/models")
            .removeSuffix("/")
    }

    private fun trim(entries: MutableList<ModelSelectionPreference>): MutableList<ModelSelectionPreference> {
        if (entries.size <= MAX_STORED_MODELS) return entries
        val retained = entries
            .sortedWith(compareByDescending<ModelSelectionPreference> { it.isFavorite }
                .thenByDescending { it.lastSelectedAt })
            .take(MAX_STORED_MODELS)
        return retained.toMutableList()
    }
}
