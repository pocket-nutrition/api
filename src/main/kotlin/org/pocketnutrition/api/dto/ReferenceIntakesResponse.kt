package org.pocketnutrition.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Dietary reference values for every nutrient and population group, the same full table for every caller (the request carries no personal data); the client picks the rows that match its user")
data class ReferenceIntakesResponse(
    @Schema(description = "Where the values come from, for attribution", example = "EFSA Dietary Reference Values (DRV Finder)")
    val source: String,
    @Schema(description = "Public page of the source", example = "https://multimedia.efsa.europa.eu/drvs/index.htm")
    val sourceUrl: String,
    val values: List<ReferenceIntakeValue>,
)

// Pinned so the generated OpenAPI schema lists the properties in a stable order: without it the
// position of the `is*` booleans varies between runs and breaks OpenApiSnapshotTest.
@JsonPropertyOrder(
    "nutrient", "nutrientKey", "classification", "population", "populationDescription", "ageFrom", "ageTo",
    "ageUnit", "ageLabel", "pal", "sex", "valueKind", "raw", "valueMin", "valueMax", "unit", "sourceLink", "note",
    "isMale", "isFemale", "isIncrement",
)
@Schema(description = "One reference value: a nutrient, a population group, a value kind and its bounds")
data class ReferenceIntakeValue(
    @Schema(description = "Nutrient name as EFSA publishes it", example = "Iron")
    val nutrient: String,
    @Schema(description = "The NutritionalProfileResponse field this value can be compared with, when the units are compatible " +
        "(e.g. \"ironMg\"). Null when EFSA's unit or definition differs from the journal's (energy in MJ, niacin per MJ, " +
        "vitamin A in retinol equivalents, added and free sugars versus total sugars).", nullable = true, example = "ironMg")
    val nutrientKey: String?,
    @Schema(description = "EFSA grouping", example = "Minerals")
    val classification: String,
    @Schema(description = "Population group", example = "Adults")
    val population: String,
    @Schema(description = "Population detail, e.g. a pregnancy trimester or menopause status", example = "Premenopausal women")
    val populationDescription: String,
    @Schema(description = "Lower bound of the age band", nullable = true) val ageFrom: Double?,
    @Schema(description = "Upper bound of the age band (999 = no upper bound)", nullable = true) val ageTo: Double?,
    @Schema(description = "Unit of the age bounds", allowableValues = ["y", "mo"], nullable = true) val ageUnit: String?,
    @Schema(description = "Age band as EFSA labels it, verbatim", nullable = true, example = "≥ 18 years") val ageLabel: String?,
    @Schema(description = "Physical activity level, for energy values only", nullable = true, example = "1.6") val pal: Double?,
    @Schema(description = "Sex label as EFSA writes it, verbatim", nullable = true, example = "Male/Female") val sex: String?,
    @get:JsonProperty("isMale") @Schema(description = "The value applies to males") val isMale: Boolean,
    @get:JsonProperty("isFemale") @Schema(description = "The value applies to females") val isFemale: Boolean,
    @Schema(description = "ai = adequate intake, ar = average requirement, ar_kcal = the same energy requirement in kcal, " +
        "pri = population reference intake, ri = reference intake range (% of energy), ul = tolerable upper intake level, " +
        "ssi = safe and sufficient intake", allowableValues = ["ssi", "ai", "ar", "ar_kcal", "pri", "ri", "ul"])
    val valueKind: String,
    @Schema(description = "The value as EFSA writes it, verbatim (\"11\", \"(+) 70\", \"45–60\", \"ND\")", example = "11")
    val raw: String,
    @Schema(description = "Lower bound, equal to valueMax for a single value; null when EFSA gives no number (ND, NA.)", nullable = true)
    val valueMin: Double?,
    @Schema(description = "Upper bound; null when EFSA gives no number", nullable = true)
    val valueMax: Double?,
    @get:JsonProperty("isIncrement")
    @Schema(description = "The value is added to the non-pregnant, non-lactating value of the same age band")
    val isIncrement: Boolean,
    @Schema(description = "Unit as EFSA writes it", nullable = true, example = "mg/day")
    val unit: String?,
    @Schema(description = "The EFSA scientific opinion the value comes from", nullable = true)
    val sourceLink: String?,
    @Schema(description = "EFSA's explanatory note for this value", nullable = true)
    val note: String?,
)
