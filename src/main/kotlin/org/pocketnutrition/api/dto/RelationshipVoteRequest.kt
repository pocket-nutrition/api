package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "An anonymous confidence vote on one curated cooking relationship")
data class RelationshipVoteRequest(
    @Schema(description = "Canonical ingredient id", example = "chicken_breast")
    val ingredientId: String,
    @Schema(description = "Cooking method the relationship applies to", example = "grilled")
    val cookingMethod: String,
    @Schema(description = "Vote direction", example = "up")
    val direction: String,
    @Schema(description = "Which client surface this vote came from", nullable = true, example = "community")
    val sourceSurface: String? = null,
)

@Schema(description = "Current confidence tally for one cooking relationship")
data class RelationshipVoteTallyResponse(
    val upvotes: Int,
    val downvotes: Int,
)
