package org.pocketnutrition.api.service

import java.text.Normalizer

private val NON_ASCII = Regex("[^\\p{ASCII}]")
private val NON_WORD = Regex("[^\\w]+")

/**
 * Convert a food name to the same ASCII slug used as ingredient_id by pocket-nutrition-ingest.
 *
 * Must produce identical output to pipeline/utils.py:slugify() for the same inputs.
 */
fun slugify(name: String): String {
    var s = name.lowercase()
        .replace("œ", "oe")
        .replace("æ", "ae")
    s = Normalizer.normalize(s, Normalizer.Form.NFKD)
    s = NON_ASCII.replace(s, "")
    s = NON_WORD.replace(s, "_").trim('_')
    return s
}
