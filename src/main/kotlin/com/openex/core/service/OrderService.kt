package com.openex.core.service

import com.openex.core.domain.Order
import com.openex.core.domain.OrderSide
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

    /**
     * Places an order, reserving the funds it needs against the account's
     * wallet before the order becomes visible to the matching engine.
     *
     * - BUY reserves price * quantity in the quote asset (e.g. USD in BTC-USD)
     * - SELL reserves quantity in the base asset (e.g. BTC in BTC-USD)
     *
     * Idempotent: calling this twice with the same idempotencyKey returns
     * the original order without reserving funds a second time.
     *
     * MARKET orders are accepted and saved, but reservation is currently
     * skipped for them — TODO(Day 5): once the matching engine knows the
     * best available price, reserve against that instead of a null price.
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
        // MARKET: no reservation yet, see TODO above.

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

    private fun parseSymbol(symbol: String): Pair<String, String> {
        val parts = symbol.split("-")
        require(parts.size == 2) { "Symbol must be in BASE-QUOTE form, e.g. BTC-USD" }
        return parts[0] to parts[1]
    }
}
