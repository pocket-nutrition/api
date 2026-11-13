package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.FeedbackCorrection
import org.pocketnutrition.api.dto.FeedbackReportResponse
import org.pocketnutrition.api.dto.FeedbackReportsResponse
import org.pocketnutrition.api.dto.FeedbackRequest
import org.pocketnutrition.api.model.FeedbackReport
import org.pocketnutrition.api.repository.FeedbackRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.security.MessageDigest

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val ingredientResolutionService: IngredientResolutionService,
    private val objectMapper: ObjectMapper,
    @param:Value("\${feedback.ip-hash-salt:}") private val ipHashSalt: String,
) {

    private val log = LoggerFactory.getLogger(FeedbackService::class.java)

    /**
     * Resolve the reported name to a canonical ingredient slug (reusing the same
     * resolver as the nutrition flow, with a slugify fallback), serialise the structured
     * corrections to JSON, and persist the report. Writes only to feedback_report.
     */
    fun submit(request: FeedbackRequest, clientIp: String?, userAgent: String?): FeedbackReport {
        val name = request.name.trim()
        val resolvedId = ingredientResolutionService.resolve(name)
        val ingredientId = resolvedId ?: slugify(name).ifBlank { null }

        val report = FeedbackReport(
            reportedName = name,
            corrections = if (request.corrections.isEmpty()) null else objectMapper.writeValueAsString(request.corrections),
            comment = request.comment?.trim()?.ifBlank { null },
            ingredientId = ingredientId,
            resolved = resolvedId != null,
            cookingMethod = request.cookingMethod?.trim()?.ifBlank { null },
            measuredState = request.measuredState?.trim()?.ifBlank { null },
            source = request.source?.trim()?.ifBlank { null },
            // FeedbackController already rejects any non-blank value outside its allowlist.
            sourceSurface = request.sourceSurface?.trim()?.ifBlank { null } ?: "sandbox",
            ipHash = hashIp(clientIp),
            userAgent = userAgent?.take(MAX_USER_AGENT_LENGTH),
            status = "new",
        )
        return feedbackRepository.save(report).also {
            log.info(
                "Stored feedback id={} ingredient_id={} resolved={} corrections={} hasComment={}",
                it.id, it.ingredientId, it.resolved, request.corrections.size, !it.comment.isNullOrBlank(),
            )
        }
    }

    /**
     * Public read path: reports for one ingredient, newest first, with moderation-only fields
     * (reporter name, IP hash, user agent, status) stripped out. `offset` is expected to be a
     * multiple of `limit` — callers only ever page forward by fixed-size increments — since it
     * is translated into a Spring Data page index (offset / limit).
     */
    fun listByIngredient(ingredientId: String, offset: Int, limit: Int): FeedbackReportsResponse {
        val page = feedbackRepository.findByIngredientIdOrderByCreatedAtDesc(
            ingredientId, PageRequest.of(offset / limit, limit),
        )
        val reports = page.content.map { report ->
            FeedbackReportResponse(
                id = requireNotNull(report.id),
                corrections = report.corrections?.let { objectMapper.readValue<List<FeedbackCorrection>>(it) } ?: emptyList(),
                comment = report.comment,
                cookingMethod = report.cookingMethod,
                measuredState = report.measuredState,
                sourceSurface = report.sourceSurface,
                createdAt = report.createdAt,
            )
        }
        return FeedbackReportsResponse(reports, page.totalElements)
    }

    // Privacy: store only a salted SHA-256 of the IP, never the raw value.
    // Returns null when no salt is configured (rate-limiting is not implemented in the MVP).
    private fun hashIp(clientIp: String?): String? {
        if (clientIp.isNullOrBlank() || ipHashSalt.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((ipHashSalt + clientIp).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_USER_AGENT_LENGTH = 250
    }
}
