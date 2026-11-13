package org.pocketnutrition.api.repository

import org.pocketnutrition.api.model.RelationshipVoteTally
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RelationshipVoteTallyRepository : JpaRepository<RelationshipVoteTally, Long> {

    fun findByIngredientIdAndCookingMethod(ingredientId: String, cookingMethod: String): RelationshipVoteTally?

    // Batch lookup fetches every tally for the requested ingredients; the service filters down
    // to the exact (ingredientId, cookingMethod) tuples the caller asked for — an IN/IN query
    // would wrongly cross-match tuples that were never requested together.
    fun findByIngredientIdIn(ingredientIds: List<String>): List<RelationshipVoteTally>

    // Atomic increment — avoids lost updates when two votes on the same relationship race.
    @Modifying
    @Query("""
        UPDATE RelationshipVoteTally t
        SET t.upvotes = t.upvotes + :upDelta, t.downvotes = t.downvotes + :downDelta, t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.ingredientId = :ingredientId AND t.cookingMethod = :cookingMethod
    """)
    fun incrementCounts(
        @Param("ingredientId") ingredientId: String,
        @Param("cookingMethod") cookingMethod: String,
        @Param("upDelta") upDelta: Int,
        @Param("downDelta") downDelta: Int,
    ): Int
}
