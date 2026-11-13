package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "An anonymous confidence vote on one ingredient↔source mapping association")
data class MappingVoteRequest(
    @Schema(description = "Source database", example = "ciqual")
    val source: String,
    @Schema(description = "The source's own identifier for the item", example = "9102")
    val sourceId: String,
    @Schema(description = "Cooking method / preparation state the mapping applies to", example = "raw")
    val cookingMethod: String,
    @Schema(description = "Vote direction", example = "up")
    val direction: String,
    @Schema(description = "Which client surface this vote came from", nullable = true, example = "community")
    val sourceSurface: String? = null,
)

@Schema(description = "Current confidence tally for one mapping association")
data class MappingVoteTallyResponse(
    val upvotes: Int,
    val downvotes: Int,
)
