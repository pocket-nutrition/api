package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.FeedbackCorrection
import org.pocketnutrition.api.dto.FeedbackReportResponse
import org.pocketnutrition.api.dto.FeedbackReportsResponse
import org.pocketnutrition.api.model.FeedbackReport
import org.pocketnutrition.api.service.FeedbackService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@WebMvcTest(FeedbackController::class)
class FeedbackControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var feedbackService: FeedbackService

    private fun stubSave() {
        whenever(feedbackService.submit(any(), anyOrNull(), anyOrNull()))
            .thenReturn(FeedbackReport(reportedName = "poivre noir"))
    }

    @Test
    fun `POST feedback returns 201 with structured corrections`() {
        stubSave()
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","corrections":[{"category":"measures","code":"reference_weight","value":"5","unit":"g"},{"category":"measures","code":"serving_unit","value":"pinch"}]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("received") }
        }
    }

    @Test
    fun `POST feedback returns 201 with comment only`() {
        stubSave()
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","corrections":[],"comment":"devrait permettre une pincée"}"""
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `POST feedback with no corrections and no comment returns 400`() {
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","corrections":[]}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST feedback with blank name returns 400`() {
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"   ","corrections":[{"category":"measures","code":"reference_weight","value":"5"}]}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST feedback with unknown code returns 400`() {
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","corrections":[{"category":"measures","code":"nonsense"}]}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST feedback with unknown category returns 400`() {
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","corrections":[{"category":"bogus","code":"free"}]}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST feedback with a code from a different category returns 400`() {
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","corrections":[{"category":"nutrition","code":"yield"}]}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST feedback accepts a known sourceSurface`() {
        stubSave()
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","comment":"x","sourceSurface":"community"}"""
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `POST feedback with an unknown sourceSurface returns 400`() {
        mockMvc.post("/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"poivre noir","comment":"x","sourceSurface":"bogus"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET feedback returns reports for the ingredient`() {
        whenever(feedbackService.listByIngredient(eq("rice"), eq(0), eq(5))).thenReturn(
            FeedbackReportsResponse(
                reports = listOf(
                    FeedbackReportResponse(
                        id = 1L,
                        corrections = listOf(FeedbackCorrection("measures", "reference_weight", "180", "g")),
                        comment = null,
                        cookingMethod = "raw",
                        measuredState = "raw",
                        sourceSurface = "community",
                        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                    ),
                ),
                total = 1,
            ),
        )
        mockMvc.get("/feedback") {
            param("ingredientId", "rice")
        }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(1) }
            jsonPath("$.reports[0].sourceSurface") { value("community") }
            jsonPath("$.reports[0].corrections[0].code") { value("reference_weight") }
        }
    }

    @Test
    fun `GET feedback with a blank ingredientId returns 400`() {
        mockMvc.get("/feedback") {
            param("ingredientId", "  ")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET feedback with a negative offset returns 400`() {
        mockMvc.get("/feedback") {
            param("ingredientId", "rice")
            param("offset", "-1")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET feedback with a limit above the max returns 400`() {
        mockMvc.get("/feedback") {
            param("ingredientId", "rice")
            param("limit", "51")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
