package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.MappingVoteRequest
import org.pocketnutrition.api.dto.MappingVoteTallyResponse
import org.pocketnutrition.api.model.MappingVoteTally
import org.pocketnutrition.api.repository.MappingVoteTallyRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MappingVoteService(private val mappingVoteTallyRepository: MappingVoteTallyRepository) {

    private val log = LoggerFactory.getLogger(MappingVoteService::class.java)

    /**
     * Record one vote on a mapping association, creating its tally row on first vote.
     * Existing rows are incremented atomically (UPDATE ... SET x = x + 1) so concurrent
     * votes on the same association never lose an increment.
     */
    @Transactional
    fun vote(request: MappingVoteRequest): MappingVoteTallyResponse {
        val upDelta = if (request.direction == "up") 1 else 0
        val downDelta = if (request.direction == "down") 1 else 0

        val updated = mappingVoteTallyRepository.incrementCounts(
            request.source, request.sourceId, request.cookingMethod, upDelta, downDelta,
        )
        if (updated == 0) {
            createInitialRow(request, upDelta, downDelta)
        }

        val tally = mappingVoteTallyRepository.findBySourceAndSourceIdAndCookingMethod(
            request.source, request.sourceId, request.cookingMethod,
        ) ?: return MappingVoteTallyResponse(upDelta, downDelta)
        return MappingVoteTallyResponse(tally.upvotes, tally.downvotes)
    }

    // A first vote on a never-seen association races against any concurrent first vote on the
    // same one; the unique index resolves that race, so a collision here just means another
    // request already created the row — fall through and let incrementCounts' 0-row case retry.
    private fun createInitialRow(request: MappingVoteRequest, upDelta: Int, downDelta: Int) {
        try {
            mappingVoteTallyRepository.save(
                MappingVoteTally(
                    source = request.source,
                    sourceId = request.sourceId,
                    cookingMethod = request.cookingMethod,
                    upvotes = upDelta,
                    downvotes = downDelta,
                ),
            )
        } catch (ex: DataIntegrityViolationException) {
            log.debug("Concurrent first vote on {}/{}/{}, retrying as increment", request.source, request.sourceId, request.cookingMethod)
            mappingVoteTallyRepository.incrementCounts(request.source, request.sourceId, request.cookingMethod, upDelta, downDelta)
        }
    }

    fun batchTallies(keys: List<Triple<String, String, String>>): Map<Triple<String, String, String>, MappingVoteTallyResponse> {
        if (keys.isEmpty()) return emptyMap()
        val sources = keys.map { it.first }.distinct()
        val bySource = mappingVoteTallyRepository.findBySourceIn(sources)
            .associateBy { Triple(it.source, it.sourceId, it.cookingMethod) }
        return keys.associateWith { key ->
            bySource[key]?.let { MappingVoteTallyResponse(it.upvotes, it.downvotes) }
                ?: MappingVoteTallyResponse(0, 0)
        }
    }
}
