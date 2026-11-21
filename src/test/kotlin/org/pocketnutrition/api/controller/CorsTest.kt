package org.pocketnutrition.api.controller

import org.junit.jupiter.api.Test
import org.pocketnutrition.api.service.ReferenceIntakeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options

@WebMvcTest(ReferenceIntakeController::class)
class CorsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var referenceIntakeService: ReferenceIntakeService

    private fun preflight(origin: String) = mockMvc.options("/reference-intakes") {
        header("Origin", origin)
        header("Access-Control-Request-Method", "GET")
    }

    @Test
    fun `the published site, its subdomains and local ports are allowed`() {
        listOf(
            "https://pocketnutrition.org",
            "https://me.pocketnutrition.org",
            "https://sandbox.pocketnutrition.org",
            "http://localhost:5173",
            "http://localhost:3000",
        ).forEach { origin ->
            preflight(origin).andExpect {
                status { isOk() }
                header { string("Access-Control-Allow-Origin", origin) }
            }
        }
    }

    @Test
    fun `other origins are refused, including look-alike domains`() {
        listOf(
            "https://evil.example",
            "https://pocketnutrition.org.evil.example",
            "https://evilpocketnutrition.org",
            "http://pocketnutrition.org",
        ).forEach { origin ->
            preflight(origin).andExpect {
                status { isForbidden() }
                header { doesNotExist("Access-Control-Allow-Origin") }
            }
        }
    }

    @Test
    fun `credentials are never allowed`() {
        preflight("https://pocketnutrition.org").andExpect {
            header { doesNotExist("Access-Control-Allow-Credentials") }
        }
    }
}
