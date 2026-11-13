package org.pocketnutrition.api.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

/**
 * One EFSA dietary reference value, read-only.
 *
 * The `efsa_drv_values` table is owned and loaded by `pocket-nutrition-data` (`loaders/efsa_drv.py`),
 * which documents every column. The API only reads it.
 */
@Immutable
@Entity
@Table(name = "efsa_drv_values")
data class EfsaDrvValueEntity(
    @Id val id: Long,
    @Column(name = "source_row") val sourceRow: Int,
    val classification: String,
    val nutrient: String,
    val population: String,
    @Column(name = "population_description") val populationDescription: String,
    @Column(name = "age_from") val ageFrom: Double?,
    @Column(name = "age_to") val ageTo: Double?,
    @Column(name = "age_unit") val ageUnit: String?,
    @Column(name = "age_label") val ageLabel: String?,
    val pal: Double?,
    val sex: String?,
    @Column(name = "is_male") val isMale: Boolean,
    @Column(name = "is_female") val isFemale: Boolean,
    @Column(name = "value_kind") val valueKind: String,
    val raw: String,
    @Column(name = "value_min") val valueMin: Double?,
    @Column(name = "value_max") val valueMax: Double?,
    @Column(name = "is_increment") val isIncrement: Boolean,
    val unit: String?,
    @Column(name = "source_link") val sourceLink: String?,
    val note: String?,
)
