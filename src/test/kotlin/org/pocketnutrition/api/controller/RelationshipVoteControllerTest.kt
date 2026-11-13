package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.RelationshipVoteTallyResponse
import org.pocketnutrition.api.service.RelationshipVoteService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(RelationshipVoteController::class)
class RelationshipVoteControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var relationshipVoteService: RelationshipVoteService

    @Test
    fun `POST vote returns 201 with the updated tally`() {
        whenever(relationshipVoteService.vote(any())).thenReturn(RelationshipVoteTallyResponse(upvotes = 1, downvotes = 0))
        mockMvc.post("/relationships/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ingredientId":"chicken_breast","cookingMethod":"grilled","direction":"up"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.upvotes") { value(1) }
            jsonPath("$.downvotes") { value(0) }
        }
    }

    @Test
    fun `POST vote with invalid direction returns 400`() {
        mockMvc.post("/relationships/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ingredientId":"chicken_breast","cookingMethod":"grilled","direction":"sideways"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST vote with blank ingredientId returns 400`() {
        mockMvc.post("/relationships/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ingredientId":"","cookingMethod":"grilled","direction":"up"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST vote with blank cookingMethod returns 400`() {
        mockMvc.post("/relationships/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ingredientId":"chicken_breast","cookingMethod":"","direction":"up"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST vote with unknown sourceSurface returns 400`() {
        mockMvc.post("/relationships/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ingredientId":"chicken_breast","cookingMethod":"grilled","direction":"up","sourceSurface":"bogus"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET votes returns tallies keyed by the requested key, defaulting to zero`() {
        whenever(relationshipVoteService.batchTallies(any())).thenReturn(
            mapOf(Pair("chicken_breast", "grilled") to RelationshipVoteTallyResponse(4, 1)),
        )

        mockMvc.get("/relationships/votes") {
            param("key", "chicken_breast:grilled", "brown_rice:boiled")
        }.andExpect {
            status { isOk() }
            jsonPath("$['chicken_breast:grilled'].upvotes") { value(4) }
            jsonPath("$['brown_rice:boiled'].upvotes") { value(0) }
            jsonPath("$['brown_rice:boiled'].downvotes") { value(0) }
        }
    }

    @Test
    fun `GET votes with a malformed key returns 400`() {
        mockMvc.get("/relationships/votes") {
            param("key", "chicken_breast")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
