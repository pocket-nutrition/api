package org.pocketnutrition.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.pocketnutrition.api.dto.ReferenceIntakesResponse
import org.pocketnutrition.api.service.ReferenceIntakeService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@Tag(name = "Reference intakes", description = "Dietary reference values the clients compute daily targets from")
@RestController
class ReferenceIntakeController(private val referenceIntakeService: ReferenceIntakeService) {

    @Operation(
        summary = "The full EFSA dietary reference table",
        description = "Takes no parameter on purpose: the profile (age, sex, weight, pregnancy) never leaves the device. " +
            "The client caches this response and selects the rows for its user locally.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Every reference value, with its source"),
        ApiResponse(responseCode = "503", description = "The reference table has not been loaded on this server yet"),
    )
    @GetMapping("/reference-intakes")
    fun referenceIntakes(): ResponseEntity<ReferenceIntakesResponse> {
        val response = referenceIntakeService.referenceIntakes()
        if (response.values.isEmpty()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePublic())
            .body(response)
    }

    private companion object {
        val CACHE_MAX_AGE: Duration = Duration.ofDays(1)
    }
}
