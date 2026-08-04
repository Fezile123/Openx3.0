package com.openex.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "trades")
class Trade(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val buyOrderId: UUID,

    @Column(nullable = false)
    val sellOrderId: UUID,

    @Column(nullable = false, precision = 20, scale = 8)
    val price: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 8)
    val quantity: BigDecimal,

    @Column(nullable = false)
    val executedAt: Instant = Instant.now()
)
