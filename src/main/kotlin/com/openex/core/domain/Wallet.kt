package com.openex.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "wallets",
    uniqueConstraints = [UniqueConstraint(columnNames = ["accountId", "asset"])]
)
class Wallet(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val accountId: UUID,

    @Column(nullable = false)
    val asset: String,

    @Column(nullable = false, precision = 20, scale = 8)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 20, scale = 8)
    var reserved: BigDecimal = BigDecimal.ZERO,

    @Version
    var version: Long = 0,

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
