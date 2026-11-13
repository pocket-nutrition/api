package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.FeedbackReportsResponse
import org.pocketnutrition.api.dto.FeedbackRequest
import org.pocketnutrition.api.service.FeedbackService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Feedback", description = "Anonymous user feedback on ingredient data quality")
@RestController
@RequestMapping("/feedback")
class FeedbackController(private val feedbackService: FeedbackService) {

    @Operation(summary = "Submit structured corrections and/or a comment about an ingredient")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Feedback received"),
        ApiResponse(responseCode = "400", description = "No corrections and no comment, unknown category/code, or oversized input"),
    )
    @PostMapping
    fun submit(
        @RequestBody request: FeedbackRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Map<String, String>> {
        // NOTE: No rate-limiting in the MVP — this endpoint is intentionally public and
        // unauthenticated. See README for how to add a Traefik per-IP rate limit later.
        val name = request.name.trim()
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) return ResponseEntity.badRequest().build()

        val comment = request.comment?.trim().orEmpty()
        if (comment.length > MAX_COMMENT_LENGTH) return ResponseEntity.badRequest().build()

        // A report must carry at least one structured correction OR a non-blank comment.
        if (request.corrections.isEmpty() && comment.isBlank()) return ResponseEntity.badRequest().build()
        if (request.corrections.size > MAX_CORRECTIONS) return ResponseEntity.badRequest().build()
        val sourceSurface = request.sourceSurface?.trim()
        if (!sourceSurface.isNullOrBlank() && sourceSurface !in ALLOWED_SOURCE_SURFACES) return ResponseEntity.badRequest().build()
        for (c in request.corrections) {
            // The code must belong to its declared category (rejects malformed-but-plausible payloads).
            val codes = CODES_BY_CATEGORY[c.category] ?: return ResponseEntity.badRequest().build()
            if (c.code !in codes) return ResponseEntity.badRequest().build()
            if ((c.value?.length ?: 0) > MAX_VALUE_LENGTH) return ResponseEntity.badRequest().build()
            if ((c.unit?.length ?: 0) > MAX_UNIT_LENGTH) return ResponseEntity.badRequest().build()
        }

        feedbackService.submit(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"))
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("status" to "received"))
    }

    @Operation(summary = "List public feedback reports for one ingredient, newest first")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Reports for the ingredient, with moderation-only fields stripped"),
        ApiResponse(responseCode = "400", description = "Blank ingredientId, or offset/limit out of range"),
    )
    @GetMapping
    fun list(
        @RequestParam ingredientId: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "5") limit: Int,
    ): ResponseEntity<FeedbackReportsResponse> {
        if (ingredientId.isBlank()) return ResponseEntity.badRequest().build()
        if (offset < 0 || limit < 1 || limit > MAX_LIST_LIMIT) return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(feedbackService.listByIngredient(ingredientId, offset, limit))
    }

    // Cloudflare forwards the real client IP; Traefik trusts it. Fall back to the proxy chain / remote addr.
    private fun clientIp(req: HttpServletRequest): String? =
        req.getHeader("CF-Connecting-IP")?.takeIf { it.isNotBlank() }
            ?: req.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: req.remoteAddr

    companion object {
        private const val MAX_NAME_LENGTH = 200
        private const val MAX_COMMENT_LENGTH = 2000
        private const val MAX_VALUE_LENGTH = 200
        private const val MAX_UNIT_LENGTH = 20
        private const val MAX_CORRECTIONS = 20
        private const val MAX_LIST_LIMIT = 50

        // Known client surfaces. Defaults to "sandbox" in FeedbackService when absent, so
        // older clients that don't send this field yet keep their current behaviour.
        private val ALLOWED_SOURCE_SURFACES = setOf("sandbox", "community", "ios", "android")

        // Allowed specific-error codes per category — mirrors the sandbox feedbackCatalog.ts.
        private val CODES_BY_CATEGORY: Map<String, Set<String>> = mapOf(
            "nutrition" to setOf("energy", "protein", "fat", "carbs", "fiber", "other_nutrient"),
            "identity" to setOf("name_fr", "name_en", "aliases", "food_group", "wrong_ingredient"),
            "measures" to setOf("reference_weight", "serving_unit", "display_unit", "serving_mode", "density"),
            "cooking" to setOf("yield", "cooking_method", "raw_cooked"),
            "other" to setOf("free"),
        )
    }
}
