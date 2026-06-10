package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: ContentResponse?
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    @Json(name = "parts") val parts: List<PartResponse>?
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GeminiCategoryResult(
    @Json(name = "category") val category: String,
    @Json(name = "amount") val amount: Double?,
    @Json(name = "confidence") val confidence: Double?
)

@JsonClass(generateAdapter = true)
data class GeminiReceiptResult(
    @Json(name = "description") val description: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "category") val category: String,
    @Json(name = "notes") val notes: String
)
