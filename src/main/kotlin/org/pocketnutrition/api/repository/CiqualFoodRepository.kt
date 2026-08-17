package org.pocketnutrition.api.repository

import org.pocketnutrition.api.model.CiqualFoodEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CiqualFoodRepository : JpaRepository<CiqualFoodEntity, Long> {

    @Query("""
        SELECT c FROM CiqualFoodEntity c
        WHERE LOWER(c.alimNomFr) LIKE LOWER(CONCAT(:prefix, '%'))
        ORDER BY LENGTH(c.alimNomFr) ASC
    """)
    fun findByNameStartingWith(@Param("prefix") prefix: String, pageable: Pageable): List<CiqualFoodEntity>
}
