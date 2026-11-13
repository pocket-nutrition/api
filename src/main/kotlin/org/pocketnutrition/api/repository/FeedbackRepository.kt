package org.pocketnutrition.api.repository

import org.pocketnutrition.api.model.FeedbackReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackRepository : JpaRepository<FeedbackReport, Long> {

    fun findByIngredientIdOrderByCreatedAtDesc(ingredientId: String, pageable: Pageable): Page<FeedbackReport>
}
