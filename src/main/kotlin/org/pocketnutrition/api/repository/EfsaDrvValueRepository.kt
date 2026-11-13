package org.pocketnutrition.api.repository

import org.pocketnutrition.api.model.EfsaDrvValueEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EfsaDrvValueRepository : JpaRepository<EfsaDrvValueEntity, Long> {
    fun findAllByOrderByNutrientAscSourceRowAsc(): List<EfsaDrvValueEntity>
}
