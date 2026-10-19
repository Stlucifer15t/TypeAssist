package com.typeassist.app.api

/**
 * An image sent to the model alongside the text, as base64 with its MIME type.
 *
 * Produced by the screen screenshot for `.reply`/`.sum` when screen images are enabled. Kept as a
 * plain value so the providers can be unit tested without touching Android's Bitmap.
 */
data class PromptImage(
    val mimeType: String,
    val base64: String
)
