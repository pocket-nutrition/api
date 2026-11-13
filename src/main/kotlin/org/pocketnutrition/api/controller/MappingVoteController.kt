package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.MappingVoteRequest
import org.pocketnutrition.api.dto.MappingVoteTallyResponse
import org.pocketnutrition.api.service.MappingVoteService
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

@Tag(name = "Mapping votes", description = "Anonymous confidence votes on ingredient↔source mapping associations")
@RestController
@RequestMapping("/mappings")
class MappingVoteController(private val mappingVoteService: MappingVoteService) {

    @Operation(summary = "Upvote or downvote confidence in one ingredient↔source mapping association")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Vote recorded"),
        ApiResponse(responseCode = "400", description = "Unknown source, blank sourceId/cookingMethod, invalid direction, or unknown sourceSurface"),
    )
    @PostMapping("/vote")
    fun vote(@RequestBody request: MappingVoteRequest): ResponseEntity<MappingVoteTallyResponse> {
        // NOTE: No rate-limiting in the MVP, same precedent as FeedbackController — this
        // endpoint is intentionally public and unauthenticated.
        if (request.source !in KNOWN_SOURCES) return ResponseEntity.badRequest().build()
        if (request.sourceId.isBlank() || request.sourceId.length > MAX_ID_LENGTH) return ResponseEntity.badRequest().build()
        if (request.cookingMethod.isBlank() || request.cookingMethod.length > MAX_METHOD_LENGTH) return ResponseEntity.badRequest().build()
        if (request.direction !in setOf("up", "down")) return ResponseEntity.badRequest().build()
        val sourceSurface = request.sourceSurface?.trim()
        if (!sourceSurface.isNullOrBlank() && sourceSurface !in ALLOWED_SOURCE_SURFACES) return ResponseEntity.badRequest().build()

        val tally = mappingVoteService.vote(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(tally)
    }

    @Operation(summary = "Batch-fetch current vote tallies for a set of mapping associations")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Tallies keyed by the requested 'source:sourceId:cookingMethod' key, defaulting to {0,0} when untracked"),
        ApiResponse(responseCode = "400", description = "A key is malformed or its source is unknown"),
    )
    @GetMapping("/votes")
    fun batchVotes(
        @Parameter(description = "Repeated 'source:sourceId:cookingMethod' keys", example = "ciqual:9102:raw")
        @RequestParam key: List<String>,
    ): ResponseEntity<Map<String, MappingVoteTallyResponse>> {
        val parsed = mutableListOf<Triple<String, String, String>>()
        for (raw in key) {
            val parts = raw.split(":", limit = 3)
            if (parts.size != 3 || parts.any { it.isBlank() } || parts[0] !in KNOWN_SOURCES) {
                return ResponseEntity.badRequest().build()
            }
            parsed.add(Triple(parts[0], parts[1], parts[2]))
        }

        val tallies = mappingVoteService.batchTallies(parsed)
        val body = key.zip(parsed).associate { (raw, triple) -> raw to (tallies[triple] ?: MappingVoteTallyResponse(0, 0)) }
        return ResponseEntity.ok(body)
    }

    companion object {
        private const val MAX_ID_LENGTH = 100
        private const val MAX_METHOD_LENGTH = 50

        // Mirrors community's app/completeness.py SOURCES — the per-ingredient mapping
        // sources shown on the ingredient detail page.
        private val KNOWN_SOURCES = setOf("ciqual", "mccance", "usda", "usda_foundation", "usda_sr")

        // Same allowlist as FeedbackController.
        private val ALLOWED_SOURCE_SURFACES = setOf("sandbox", "community", "ios", "android")
    }
}
