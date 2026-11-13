package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.RelationshipVoteRequest
import org.pocketnutrition.api.model.RelationshipVoteTally
import org.pocketnutrition.api.repository.RelationshipVoteTallyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RelationshipVoteServiceTest {

    private val repository: RelationshipVoteTallyRepository = mock()
    private val service = RelationshipVoteService(repository)

    @Test
    fun `first vote on an untracked relationship creates its tally row`() {
        whenever(repository.incrementCounts("chicken_breast", "grilled", 1, 0)).thenReturn(0)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as RelationshipVoteTally }
        whenever(repository.findByIngredientIdAndCookingMethod("chicken_breast", "grilled"))
            .thenReturn(RelationshipVoteTally("chicken_breast", "grilled", upvotes = 1, downvotes = 0))

        val result = service.vote(RelationshipVoteRequest("chicken_breast", "grilled", "up"))

        assertEquals(1, result.upvotes)
        assertEquals(0, result.downvotes)
        verify(repository).save(any())
    }

    @Test
    fun `a vote on an existing relationship increments it atomically without inserting`() {
        whenever(repository.incrementCounts("chicken_breast", "grilled", 0, 1)).thenReturn(1)
        whenever(repository.findByIngredientIdAndCookingMethod("chicken_breast", "grilled"))
            .thenReturn(RelationshipVoteTally("chicken_breast", "grilled", upvotes = 3, downvotes = 5))

        val result = service.vote(RelationshipVoteRequest("chicken_breast", "grilled", "down"))

        assertEquals(3, result.upvotes)
        assertEquals(5, result.downvotes)
        verify(repository, never()).save(any())
    }

    @Test
    fun `batch tallies default to zero for untracked relationships`() {
        whenever(repository.findByIngredientIdIn(listOf("chicken_breast"))).thenReturn(
            listOf(RelationshipVoteTally("chicken_breast", "grilled", upvotes = 2, downvotes = 1)),
        )

        val result = service.batchTallies(
            listOf(
                Pair("chicken_breast", "grilled"),
                Pair("chicken_breast", "boiled"),
            ),
        )

        assertEquals(2, result[Pair("chicken_breast", "grilled")]?.upvotes)
        assertEquals(0, result[Pair("chicken_breast", "boiled")]?.upvotes)
        assertEquals(0, result[Pair("chicken_breast", "boiled")]?.downvotes)
    }

    @Test
    fun `batch tallies with no keys does not query the repository`() {
        val result = service.batchTallies(emptyList())

        assertEquals(emptyMap<Pair<String, String>, Any>(), result)
        verify(repository, never()).findByIngredientIdIn(any())
    }
}
