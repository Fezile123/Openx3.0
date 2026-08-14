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
    private val tradeRepository: TradeRepository,
    private val walletService: WalletService,
    private val broadcastService: BroadcastService
) {

    /**
     * Attempts to match [incomingOrderId] against resting orders on the
     * opposite side of the same symbol's book, using price-time priority.
     * Each match executes at the RESTING order's price (maker price), and
     * settles funds between the two real accounts via
     * WalletService.settleTrade — no system account involved, since this
     * moves real assets between two real counterparties.
     */
    @Transactional
    fun match(incomingOrderId: UUID) {
        val incoming = orderRepository.findById(incomingOrderId).orElseThrow {
            IllegalArgumentException("Order $incomingOrderId not found")
        }

        if (incoming.type != OrderType.LIMIT) return // market matching: future work
        if (incoming.status != OrderStatus.OPEN && incoming.status != OrderStatus.PARTIALLY_FILLED) return

        val (base, quote) = parseSymbol(incoming.symbol)
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

            val trade = tradeRepository.save(
                Trade(
                    symbol = incoming.symbol,
                    buyOrderId = buyOrder.id,
                    sellOrderId = sellOrder.id,
                    price = tradePrice,
                    quantity = fillQuantity
                )
            )
            broadcastService.broadcastTrade(trade)

            walletService.settleTrade(
                buyerId = buyOrder.accountId,
                sellerId = sellOrder.accountId,
                baseAsset = base,
                quoteAsset = quote,
                baseQuantity = fillQuantity,
                tradePrice = tradePrice,
                buyerLimitPrice = buyOrder.price!!,
                referenceId = trade.id
            )

            incoming.remainingQuantity = incoming.remainingQuantity.subtract(fillQuantity)
            resting.remainingQuantity = resting.remainingQuantity.subtract(fillQuantity)

            incoming.status = statusFor(incoming.remainingQuantity)
            resting.status = statusFor(resting.remainingQuantity)

            orderRepository.save(resting)
        }

        orderRepository.save(incoming)
        broadcastService.broadcastOrderBook(incoming.symbol)
    }

    private fun crosses(incoming: Order, resting: Order): Boolean {
        val incomingPrice = incoming.price ?: return false
        val restingPrice = resting.price ?: return false
        return if (incoming.side == OrderSide.BUY) {
            incomingPrice >= restingPrice
        } else {
            incomingPrice <= restingPrice
        }
    }

    private fun priceRank(incomingSide: OrderSide, restingPrice: BigDecimal): BigDecimal =
        if (incomingSide == OrderSide.BUY) restingPrice else restingPrice.negate()

    private fun statusFor(remaining: BigDecimal): OrderStatus =
        if (remaining <= BigDecimal.ZERO) OrderStatus.FILLED else OrderStatus.PARTIALLY_FILLED

    private fun parseSymbol(symbol: String): Pair<String, String> {
        val parts = symbol.split("-")
        require(parts.size == 2) { "Symbol must be in BASE-QUOTE form, e.g. BTC-USD" }
        return parts[0] to parts[1]
    }
}