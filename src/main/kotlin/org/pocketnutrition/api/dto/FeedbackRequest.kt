package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A single structured correction: which aspect is wrong and the proposed value")
data class FeedbackCorrection(
    @Schema(description = "Category code", example = "measures")
    val category: String,
    @Schema(description = "Specific error code within the category", example = "reference_weight")
    val code: String,
    @Schema(description = "Proposed correct value", nullable = true, example = "5")
    val value: String? = null,
    @Schema(description = "Unit for the value when relevant (e.g. g, ml)", nullable = true, example = "g")
    val unit: String? = null,
)

@Schema(description = "Anonymous report: structured corrections and/or a free-text comment about an ingredient")
data class FeedbackRequest(
    @Schema(description = "The food name as shown to the user", example = "poivre noir")
    val name: String,
    @Schema(description = "Structured corrections (may be empty when a comment is provided)")
    val corrections: List<FeedbackCorrection> = emptyList(),
    @Schema(description = "Optional free-text comment / new information", nullable = true)
    val comment: String? = null,
    @Schema(description = "Cooking method shown for the item", nullable = true)
    val cookingMethod: String? = null,
    @Schema(description = "Whether the weight was measured raw or cooked", nullable = true)
    val measuredState: String? = null,
    @Schema(description = "Origin of the result shown to the user (ml/cache/off_direct/…)", nullable = true)
    val source: String? = null,
)
