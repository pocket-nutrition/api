package org.pocketnutrition.api.repository

import org.pocketnutrition.api.model.MappingVoteTally
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MappingVoteTallyRepository : JpaRepository<MappingVoteTally, Long> {

    fun findBySourceAndSourceIdAndCookingMethod(source: String, sourceId: String, cookingMethod: String): MappingVoteTally?

    // Batch lookup fetches every tally for the requested sources; the service filters down
    // to the exact (source, sourceId, cookingMethod) tuples the caller asked for — an IN/IN/IN
    // query would wrongly cross-match tuples that were never requested together.
    fun findBySourceIn(sources: List<String>): List<MappingVoteTally>

    // Atomic increment — avoids lost updates when two votes on the same association race.
    @Modifying
    @Query("""
        UPDATE MappingVoteTally t
        SET t.upvotes = t.upvotes + :upDelta, t.downvotes = t.downvotes + :downDelta, t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.source = :source AND t.sourceId = :sourceId AND t.cookingMethod = :cookingMethod
    """)
    fun incrementCounts(
        @Param("source") source: String,
        @Param("sourceId") sourceId: String,
        @Param("cookingMethod") cookingMethod: String,
        @Param("upDelta") upDelta: Int,
        @Param("downDelta") downDelta: Int,
    ): Int
}
