package org.pocketnutrition.api.controller

import org.pocketnutrition.api.model.FeedbackReport
import org.pocketnutrition.api.service.FeedbackService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

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
}
