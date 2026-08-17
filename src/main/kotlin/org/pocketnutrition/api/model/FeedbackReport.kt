package org.pocketnutrition.api.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A public, anonymous feedback report about an ingredient's data quality.
 *
 * Side-channel inbox: written only by pocket-nutrition-api, read only by pocket-nutrition-community.
 * It is NOT part of the knowledge pipeline — never mirrored to Redis, the YAML files in a
 * private repository, or the raw source tables.
 */
@Entity
@Table(name = "feedback_report")
class FeedbackReport(
    @Column(name = "reported_name", nullable = false)
    var reportedName: String,

    // JSON array of {category, code, value, unit} — structured corrections (nullable: a
    // report may be a free-text comment only).
    @Column(name = "corrections", columnDefinition = "text")
    var corrections: String? = null,

    @Column(name = "comment", columnDefinition = "text")
    var comment: String? = null,

    @Column(name = "ingredient_id")
    var ingredientId: String? = null,

    @Column(name = "resolved", nullable = false)
    var resolved: Boolean = false,

    @Column(name = "cooking_method")
    var cookingMethod: String? = null,

    @Column(name = "measured_state")
    var measuredState: String? = null,

    @Column(name = "source")
    var source: String? = null,

    @Column(name = "source_surface", nullable = false)
    var sourceSurface: String = "sandbox",

    // Salted hash of the client IP — never the raw IP. Null when no salt is configured.
    @Column(name = "ip_hash")
    var ipHash: String? = null,

    @Column(name = "user_agent")
    var userAgent: String? = null,

    @Column(name = "status", nullable = false)
    var status: String = "new",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
