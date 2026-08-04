package com.openex.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderSide { BUY, SELL }
enum class OrderType { LIMIT, MARKET }
enum class OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED }

@Entity
@Table(name = "orders")
class Order(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val accountId: UUID,

    @Column(nullable = false)
    val symbol: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val side: OrderSide,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: OrderType,

    @Column(precision = 20, scale = 8)
    val price: BigDecimal? = null,

    @Column(nullable = false, precision = 20, scale = 8)
    val quantity: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 8)
    var remainingQuantity: BigDecimal = quantity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.OPEN,

    @Column(nullable = false, unique = true)
    val idempotencyKey: String,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
