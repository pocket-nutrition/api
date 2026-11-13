package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.RelationshipVoteRequest
import org.pocketnutrition.api.dto.RelationshipVoteTallyResponse
import org.pocketnutrition.api.service.RelationshipVoteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Relationship votes", description = "Anonymous confidence votes on curated cooking relationships")
@RestController
@RequestMapping("/relationships")
class RelationshipVoteController(private val relationshipVoteService: RelationshipVoteService) {

    @Operation(summary = "Upvote or downvote confidence in one curated cooking relationship")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Vote recorded"),
        ApiResponse(responseCode = "400", description = "Blank ingredientId/cookingMethod, invalid direction, or unknown sourceSurface"),
    )
    @PostMapping("/vote")
    fun vote(@RequestBody request: RelationshipVoteRequest): ResponseEntity<RelationshipVoteTallyResponse> {
        // NOTE: No rate-limiting in the MVP, same precedent as FeedbackController/MappingVoteController
        // — this endpoint is intentionally public and unauthenticated.
        if (request.ingredientId.isBlank() || request.ingredientId.length > MAX_ID_LENGTH) return ResponseEntity.badRequest().build()
        if (request.cookingMethod.isBlank() || request.cookingMethod.length > MAX_METHOD_LENGTH) return ResponseEntity.badRequest().build()
        if (request.direction !in setOf("up", "down")) return ResponseEntity.badRequest().build()
        val sourceSurface = request.sourceSurface?.trim()
        if (!sourceSurface.isNullOrBlank() && sourceSurface !in ALLOWED_SOURCE_SURFACES) return ResponseEntity.badRequest().build()

        val tally = relationshipVoteService.vote(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(tally)
    }

    @Operation(summary = "Batch-fetch current vote tallies for a set of cooking relationships")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Tallies keyed by the requested 'ingredientId:cookingMethod' key, defaulting to {0,0} when untracked"),
        ApiResponse(responseCode = "400", description = "A key is malformed"),
    )
    @GetMapping("/votes")
    fun batchVotes(
        @Parameter(description = "Repeated 'ingredientId:cookingMethod' keys", example = "chicken_breast:grilled")
        @RequestParam key: List<String>,
    ): ResponseEntity<Map<String, RelationshipVoteTallyResponse>> {
        val parsed = mutableListOf<Pair<String, String>>()
        for (raw in key) {
            val parts = raw.split(":", limit = 2)
            if (parts.size != 2 || parts.any { it.isBlank() }) {
                return ResponseEntity.badRequest().build()
            }
            parsed.add(Pair(parts[0], parts[1]))
        }

        val tallies = relationshipVoteService.batchTallies(parsed)
        val body = key.zip(parsed).associate { (raw, pair) -> raw to (tallies[pair] ?: RelationshipVoteTallyResponse(0, 0)) }
        return ResponseEntity.ok(body)
    }

    companion object {
        private const val MAX_ID_LENGTH = 100
        private const val MAX_METHOD_LENGTH = 50

        // Same allowlist as FeedbackController/MappingVoteController.
        private val ALLOWED_SOURCE_SURFACES = setOf("sandbox", "community", "ios", "android")
    }
}
