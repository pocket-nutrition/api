package org.pocketnutrition.api.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Public confidence tally on one ingredient↔source mapping association (upvote/downvote).
 *
 * Side-channel, like FeedbackReport: written only by pocket-nutrition-api, never mirrored to
 * Redis, knowledge YAML files, or the raw source tables. Keyed by (source, sourceId, cookingMethod).
 */
@Entity
@Table(name = "mapping_vote_tally")
class MappingVoteTally(
    @Column(name = "source", nullable = false)
    var source: String,

    @Column(name = "source_id", nullable = false)
    var sourceId: String,

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
