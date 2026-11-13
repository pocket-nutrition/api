package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.RelationshipVoteRequest
import org.pocketnutrition.api.dto.RelationshipVoteTallyResponse
import org.pocketnutrition.api.model.RelationshipVoteTally
import org.pocketnutrition.api.repository.RelationshipVoteTallyRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RelationshipVoteService(private val relationshipVoteTallyRepository: RelationshipVoteTallyRepository) {

    private val log = LoggerFactory.getLogger(RelationshipVoteService::class.java)

    /**
     * Record one vote on a cooking relationship, creating its tally row on first vote.
     * Existing rows are incremented atomically (UPDATE ... SET x = x + 1) so concurrent
     * votes on the same relationship never lose an increment.
     */
    @Transactional
    fun vote(request: RelationshipVoteRequest): RelationshipVoteTallyResponse {
        val upDelta = if (request.direction == "up") 1 else 0
        val downDelta = if (request.direction == "down") 1 else 0

        val updated = relationshipVoteTallyRepository.incrementCounts(
            request.ingredientId, request.cookingMethod, upDelta, downDelta,
        )
        if (updated == 0) {
            createInitialRow(request, upDelta, downDelta)
        }

        val tally = relationshipVoteTallyRepository.findByIngredientIdAndCookingMethod(
            request.ingredientId, request.cookingMethod,
        ) ?: return RelationshipVoteTallyResponse(upDelta, downDelta)
        return RelationshipVoteTallyResponse(tally.upvotes, tally.downvotes)
    }

    // A first vote on a never-seen relationship races against any concurrent first vote on the
    // same one; the unique index resolves that race, so a collision here just means another
    // request already created the row — fall through and let incrementCounts' 0-row case retry.
    private fun createInitialRow(request: RelationshipVoteRequest, upDelta: Int, downDelta: Int) {
        try {
            relationshipVoteTallyRepository.save(
                RelationshipVoteTally(
                    ingredientId = request.ingredientId,
                    cookingMethod = request.cookingMethod,
                    upvotes = upDelta,
                    downvotes = downDelta,
                ),
            )
        } catch (ex: DataIntegrityViolationException) {
            log.debug("Concurrent first vote on {}/{}, retrying as increment", request.ingredientId, request.cookingMethod)
            relationshipVoteTallyRepository.incrementCounts(request.ingredientId, request.cookingMethod, upDelta, downDelta)
        }
    }

    fun batchTallies(keys: List<Pair<String, String>>): Map<Pair<String, String>, RelationshipVoteTallyResponse> {
        if (keys.isEmpty()) return emptyMap()
        val ingredientIds = keys.map { it.first }.distinct()
        val byIngredient = relationshipVoteTallyRepository.findByIngredientIdIn(ingredientIds)
            .associateBy { Pair(it.ingredientId, it.cookingMethod) }
        return keys.associateWith { key ->
            byIngredient[key]?.let { RelationshipVoteTallyResponse(it.upvotes, it.downvotes) }
                ?: RelationshipVoteTallyResponse(0, 0)
        }
    }
}
