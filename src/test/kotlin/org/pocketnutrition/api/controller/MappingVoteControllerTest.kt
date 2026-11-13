package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.MappingVoteTallyResponse
import org.pocketnutrition.api.service.MappingVoteService
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

@WebMvcTest(MappingVoteController::class)
class MappingVoteControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var mappingVoteService: MappingVoteService

    @Test
    fun `POST vote returns 201 with the updated tally`() {
        whenever(mappingVoteService.vote(any())).thenReturn(MappingVoteTallyResponse(upvotes = 1, downvotes = 0))
        mockMvc.post("/mappings/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"source":"ciqual","sourceId":"9102","cookingMethod":"raw","direction":"up"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.upvotes") { value(1) }
            jsonPath("$.downvotes") { value(0) }
        }
    }

    @Test
    fun `POST vote with unknown source returns 400`() {
        mockMvc.post("/mappings/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"source":"bogus","sourceId":"9102","cookingMethod":"raw","direction":"up"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST vote with invalid direction returns 400`() {
        mockMvc.post("/mappings/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"source":"ciqual","sourceId":"9102","cookingMethod":"raw","direction":"sideways"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST vote with blank sourceId returns 400`() {
        mockMvc.post("/mappings/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"source":"ciqual","sourceId":"","cookingMethod":"raw","direction":"up"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST vote with unknown sourceSurface returns 400`() {
        mockMvc.post("/mappings/vote") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"source":"ciqual","sourceId":"9102","cookingMethod":"raw","direction":"up","sourceSurface":"bogus"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET votes returns tallies keyed by the requested key, defaulting to zero`() {
        whenever(mappingVoteService.batchTallies(any())).thenReturn(
            mapOf(Triple("ciqual", "9102", "raw") to MappingVoteTallyResponse(4, 1)),
        )

        mockMvc.get("/mappings/votes") {
            param("key", "ciqual:9102:raw", "usda:5000:boiled")
        }.andExpect {
            status { isOk() }
            jsonPath("$['ciqual:9102:raw'].upvotes") { value(4) }
            jsonPath("$['usda:5000:boiled'].upvotes") { value(0) }
            jsonPath("$['usda:5000:boiled'].downvotes") { value(0) }
        }
    }

    @Test
    fun `GET votes with a malformed key returns 400`() {
        mockMvc.get("/mappings/votes") {
            param("key", "ciqual:9102")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET votes with an unknown source in a key returns 400`() {
        mockMvc.get("/mappings/votes") {
            param("key", "bogus:1:raw")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
