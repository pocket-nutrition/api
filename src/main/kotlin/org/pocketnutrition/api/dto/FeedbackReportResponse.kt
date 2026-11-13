package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "One public feedback report about an ingredient — moderation-only fields (reporter name, IP hash, user agent, status) are never exposed here")
data class FeedbackReportResponse(
    val id: Long,
    @Schema(description = "Structured corrections attached to this report (may be empty for a comment-only report)")
    val corrections: List<FeedbackCorrection>,
    val comment: String?,
    val cookingMethod: String?,
    val measuredState: String?,
    @Schema(description = "Which client surface this report came from", example = "community")
    val sourceSurface: String,
    val createdAt: Instant,
)

@Schema(description = "A page of public feedback reports for one ingredient")
data class FeedbackReportsResponse(
    val reports: List<FeedbackReportResponse>,
    @Schema(description = "Total number of reports for this ingredient, for deciding whether to offer 'load more'")
    val total: Long,
)
