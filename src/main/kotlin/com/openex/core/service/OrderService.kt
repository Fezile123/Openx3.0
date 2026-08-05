package com.openex.core.service

import com.openex.core.domain.Order
import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val walletService: WalletService,
    private val matchingEngine: MatchingEngine
) {

    /**
     * Places an order, reserving the funds it needs, then immediately
     * attempts to match it against the resting order book.
     *
     * Idempotent: calling this twice with the same idempotencyKey returns
     * the original order as it currently stands — it does NOT re-run
     * matching, since the original call already did that.
     */
    @Transactional
    fun placeOrder(
        accountId: UUID,
        symbol: String,
        side: OrderSide,
        type: OrderType,
        price: BigDecimal?,
        quantity: BigDecimal,
        idempotencyKey: String
    ): Order {
        orderRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
        if (type == OrderType.LIMIT) {
            require(price != null && price > BigDecimal.ZERO) { "Limit orders require a positive price" }
        }

        val (base, quote) = parseSymbol(symbol)

        if (type == OrderType.LIMIT) {
            when (side) {
                OrderSide.BUY -> walletService.reserve(accountId, quote, price!!.multiply(quantity))
                OrderSide.SELL -> walletService.reserve(accountId, base, quantity)
            }
        }
        // MARKET: no reservation yet — TODO(Day 6): reserve against matched price.

        val order = Order(
            accountId = accountId,
            symbol = symbol,
            side = side,
            type = type,
            price = price,
            quantity = quantity,
            remainingQuantity = quantity,
            idempotencyKey = idempotencyKey
        )

        val saved = orderRepository.save(order)

        if (saved.type == OrderType.LIMIT) {
            matchingEngine.match(saved.id)
        }

        return orderRepository.findById(saved.id).orElseThrow {
            IllegalStateException("Order ${saved.id} vanished immediately after being saved")
        }
    }

    @Transactional
    fun cancelOrder(orderId: UUID, accountId: UUID): Order {
        val order = orderRepository.findById(orderId).orElseThrow {
            IllegalArgumentException("Order $orderId not found")
        }

        require(order.accountId == accountId) {
            "Order $orderId does not belong to account $accountId"
        }

        if (order.status == OrderStatus.CANCELLED || order.status == OrderStatus.FILLED) {
            return order
        }

        val (base, quote) = parseSymbol(order.symbol)

        if (order.type == OrderType.LIMIT) {
            when (order.side) {
                OrderSide.BUY -> walletService.release(accountId, quote, order.price!!.multiply(order.remainingQuantity))
                OrderSide.SELL -> walletService.release(accountId, base, order.remainingQuantity)
            }
        }

        order.status = OrderStatus.CANCELLED
        return orderRepository.save(order)
    }

    private fun parseSymbol(symbol: String): Pair<String, String> {
        val parts = symbol.split("-")
        require(parts.size == 2) { "Symbol must be in BASE-QUOTE form, e.g. BTC-USD" }
        return parts[0] to parts[1]
    }
}
