package org.pocketnutrition.api.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Public confidence tally on one curated cooking relationship (upvote/downvote).
 *
 * Side-channel, like FeedbackReport and MappingVoteTally: written only by pocket-nutrition-api, never
 * mirrored to Redis, knowledge YAML files, or the raw source tables. Keyed by (ingredientId,
 * cookingMethod) — deliberately scoped to the curated relationship (yieldFactor/confidence,
 * sourced from pocket-nutrition-knowledge) rather than the v2_training_pairs row it may join to,
 * since that row is rebuilt by ingest on every run and a "theoretical" pair has no stable
 * identity to vote on.
 */
@Entity
@Table(name = "relationship_vote_tally")
class RelationshipVoteTally(
    @Column(name = "ingredient_id", nullable = false)
    var ingredientId: String,

    @Column(name = "cooking_method", nullable = false)
    var cookingMethod: String,

    @Column(name = "upvotes", nullable = false)
    var upvotes: Int = 0,

    @Column(name = "downvotes", nullable = false)
    var downvotes: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
