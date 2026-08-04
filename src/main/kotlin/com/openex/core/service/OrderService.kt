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
    private val walletService: WalletService
) {

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
        // MARKET: no reservation yet — TODO(Day 5): reserve against matched price.

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

        return orderRepository.save(order)
    }

    /**
     * Cancels an open order and releases its reserved funds back to the
     * owner's spendable balance. Only the order's own account may cancel
     * it. Cancelling an order that's already CANCELLED or FILLED is a
     * silent no-op — returns the order as-is rather than erroring, so
     * repeated cancel calls (e.g. from a retried client request) are safe.
     */
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
