package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * One piece of text in one language. The single shape every multilingual field on this API uses —
 * replacing what used to be a fixed `xxxFr`/`xxxEn` field pair per piece of text.
 *
 * The point of the shape: a new language is a knowledge-base data change only. Nothing that reads
 * a `List<LocalizedText>` needs to know how many languages exist or what their codes are.
 */
@Schema(description = "One piece of text in one language")
data class LocalizedText(
    @Schema(description = "BCP-47 language code (fr, en, ...)", example = "fr")
    val lang: String,
    @Schema(description = "The text itself in that language", example = "Poulet, filet")
    val text: String,
)
