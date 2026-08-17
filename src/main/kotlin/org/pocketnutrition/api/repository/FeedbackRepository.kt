package org.pocketnutrition.api.repository

import org.pocketnutrition.api.model.FeedbackReport
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackRepository : JpaRepository<FeedbackReport, Long>
