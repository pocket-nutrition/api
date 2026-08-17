package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.FeedbackRequest
import org.pocketnutrition.api.model.FeedbackReport
import org.pocketnutrition.api.repository.FeedbackRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
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
            sourceSurface = "sandbox",
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
