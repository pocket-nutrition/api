package org.pocketnutrition.api.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

@Immutable
@Entity
@Table(name = "ciqual_foods")
data class CiqualFoodEntity(
    @Id
    @Column(name = "alim_code")
    val alimCode: Long,

    @Column(name = "alim_nom_fr")
    val alimNomFr: String,
)
