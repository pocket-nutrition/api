package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.MappingVoteRequest
import org.pocketnutrition.api.model.MappingVoteTally
import org.pocketnutrition.api.repository.MappingVoteTallyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MappingVoteServiceTest {

    private val repository: MappingVoteTallyRepository = mock()
    private val service = MappingVoteService(repository)

    @Test
    fun `first vote on an untracked association creates its tally row`() {
        whenever(repository.incrementCounts("ciqual", "9102", "raw", 1, 0)).thenReturn(0)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as MappingVoteTally }
        whenever(repository.findBySourceAndSourceIdAndCookingMethod("ciqual", "9102", "raw"))
            .thenReturn(MappingVoteTally("ciqual", "9102", "raw", upvotes = 1, downvotes = 0))

        val result = service.vote(MappingVoteRequest("ciqual", "9102", "raw", "up"))

        assertEquals(1, result.upvotes)
        assertEquals(0, result.downvotes)
        verify(repository).save(any())
    }

    @Test
    fun `a vote on an existing association increments it atomically without inserting`() {
        whenever(repository.incrementCounts("ciqual", "9102", "raw", 0, 1)).thenReturn(1)
        whenever(repository.findBySourceAndSourceIdAndCookingMethod("ciqual", "9102", "raw"))
            .thenReturn(MappingVoteTally("ciqual", "9102", "raw", upvotes = 3, downvotes = 5))

        val result = service.vote(MappingVoteRequest("ciqual", "9102", "raw", "down"))

        assertEquals(3, result.upvotes)
        assertEquals(5, result.downvotes)
        verify(repository, never()).save(any())
    }

    @Test
    fun `batch tallies default to zero for untracked associations`() {
        whenever(repository.findBySourceIn(listOf("ciqual"))).thenReturn(
            listOf(MappingVoteTally("ciqual", "9102", "raw", upvotes = 2, downvotes = 1)),
        )

        val result = service.batchTallies(
            listOf(
                Triple("ciqual", "9102", "raw"),
                Triple("ciqual", "9999", "raw"),
            ),
        )

        assertEquals(2, result[Triple("ciqual", "9102", "raw")]?.upvotes)
        assertEquals(0, result[Triple("ciqual", "9999", "raw")]?.upvotes)
        assertEquals(0, result[Triple("ciqual", "9999", "raw")]?.downvotes)
    }

    @Test
    fun `batch tallies with no keys does not query the repository`() {
        val result = service.batchTallies(emptyList())

        assertEquals(emptyMap<Triple<String, String, String>, Any>(), result)
        verify(repository, never()).findBySourceIn(any())
    }
}
