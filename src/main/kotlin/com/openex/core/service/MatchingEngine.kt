package com.openex.core.service

import com.openex.core.domain.Order
import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.domain.Trade
import com.openex.core.repository.OrderRepository
import com.openex.core.repository.TradeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class MatchingEngine(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository
) {

    /**
     * Attempts to match [incomingOrderId] against resting orders on the
     * opposite side of the same symbol's book, using price-time priority:
     * best price first, then earliest createdAt among equal prices.
     *
     * Each match executes at the RESTING order's price (maker price).
     * Fills continue until the incoming order is fully filled or no more
     * resting orders cross its price. MARKET orders are not yet supported
     * here — TODO(Day 6): market orders need to walk the book without a
     * price bound of their own, sweeping best available prices.
     *
     * Wallet settlement (moving reserved funds, crediting the other side)
     * is intentionally NOT done here — that's Day 6's integration work.
     * This method only updates order state and records trades.
     */
    @Transactional
    fun match(incomingOrderId: UUID) {
        val incoming = orderRepository.findById(incomingOrderId).orElseThrow {
            IllegalArgumentException("Order $incomingOrderId not found")
        }

        if (incoming.type != OrderType.LIMIT) return // market matching: Day 6
        if (incoming.status != com.openex.core.domain.OrderStatus.OPEN &&
            incoming.status != com.openex.core.domain.OrderStatus.PARTIALLY_FILLED
        ) return

        val oppositeSide = if (incoming.side == OrderSide.BUY) OrderSide.SELL else OrderSide.BUY
        val candidates = orderRepository.findAll().filter {
            it.symbol == incoming.symbol &&
                it.side == oppositeSide &&
                it.type == OrderType.LIMIT &&
                (it.status == OrderStatus.OPEN || it.status == OrderStatus.PARTIALLY_FILLED) &&
                crosses(incoming, it)
        }

        val sorted = candidates.sortedWith(
            compareBy(
                { resting -> priceRank(incoming.side, resting.price!!) },
                { resting -> resting.createdAt }
            )
        )

        for (resting in sorted) {
            if (incoming.remainingQuantity <= BigDecimal.ZERO) break

            val fillQuantity = minOf(incoming.remainingQuantity, resting.remainingQuantity)
            if (fillQuantity <= BigDecimal.ZERO) continue

            val tradePrice = resting.price!! // maker price

            val (buyOrder, sellOrder) = if (incoming.side == OrderSide.BUY) incoming to resting else resting to incoming

            tradeRepository.save(
                Trade(
                    symbol = incoming.symbol,
                    buyOrderId = buyOrder.id,
                    sellOrderId = sellOrder.id,
                    price = tradePrice,
                    quantity = fillQuantity
                )
            )

            incoming.remainingQuantity = incoming.remainingQuantity.subtract(fillQuantity)
            resting.remainingQuantity = resting.remainingQuantity.subtract(fillQuantity)

            incoming.status = statusFor(incoming.remainingQuantity)
            resting.status = statusFor(resting.remainingQuantity)

            orderRepository.save(resting)
        }

        orderRepository.save(incoming)
    }

    private fun crosses(incoming: Order, resting: Order): Boolean {
        val incomingPrice = incoming.price ?: return false
        val restingPrice = resting.price ?: return false
        return if (incoming.side == OrderSide.BUY) {
            incomingPrice >= restingPrice // willing to pay at least the ask
        } else {
            incomingPrice <= restingPrice // willing to sell at or below the bid
        }
    }

    /** Lower rank = matched first. Best price for the incoming side wins. */
    private fun priceRank(incomingSide: OrderSide, restingPrice: BigDecimal): BigDecimal =
        if (incomingSide == OrderSide.BUY) restingPrice else restingPrice.negate()

    private fun statusFor(remaining: BigDecimal): OrderStatus =
        if (remaining <= BigDecimal.ZERO) OrderStatus.FILLED else OrderStatus.PARTIALLY_FILLED
}
