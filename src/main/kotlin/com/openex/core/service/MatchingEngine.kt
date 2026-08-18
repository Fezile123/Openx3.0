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
     * Match an incoming order against eligible resting orders.
     *
     * Current implementation supports LIMIT orders only.
     *
     * Matching rules:
     *
     * BUY:
     *   - matches SELL orders where sell.price <= buy.price
     *   - cheapest SELL first
     *   - earliest order first when prices are equal
     *
     * SELL:
     *   - matches BUY orders where buy.price >= sell.price
     *   - highest BUY first
     *   - earliest order first when prices are equal
     *
     * Trades execute at the RESTING order's price.
     */
    @Transactional
    fun match(incomingOrderId: UUID) {

        val incoming =
            orderRepository.findById(incomingOrderId).orElseThrow {
                IllegalArgumentException(
                    "Order $incomingOrderId not found"
                )
            }

        // Only LIMIT orders are supported at this stage.
        if (incoming.type != OrderType.LIMIT) {
            return
        }

        // Nothing to match if the order is no longer active.
        if (!isActive(incoming)) {
            return
        }

        val (baseAsset, quoteAsset) =
            parseSymbol(incoming.symbol)

        val oppositeSide =
            when (incoming.side) {
                OrderSide.BUY -> OrderSide.SELL
                OrderSide.SELL -> OrderSide.BUY
            }

        /*
         * Find active opposite-side LIMIT orders
         * that cross the incoming order.
         */
        val candidates =
            orderRepository.findAll()
                .asSequence()
                .filter { resting ->
                    resting.id != incoming.id
                }
                .filter { resting ->
                    resting.symbol == incoming.symbol
                }
                .filter { resting ->
                    resting.side == oppositeSide
                }
                .filter { resting ->
                    resting.type == OrderType.LIMIT
                }
                .filter { resting ->
                    isActive(resting)
                }
                .filter { resting ->
                    crosses(incoming, resting)
                }
                .toList()

        /*
         * PRICE-TIME PRIORITY
         *
         * Incoming BUY:
         *   Lowest SELL price first.
         *
         * Incoming SELL:
         *   Highest BUY price first.
         *
         * Earlier created order wins when prices are equal.
         */
        val sortedCandidates =
            candidates.sortedWith(
                compareBy<Order>(
                    { resting ->
                        if (incoming.side == OrderSide.BUY) {
                            resting.price!!
                        } else {
                            resting.price!!.negate()
                        }
                    },
                    { resting ->
                        resting.createdAt
                    }
                )
            )

        for (resting in sortedCandidates) {

            if (incoming.remainingQuantity <= BigDecimal.ZERO) {
                break
            }

            if (!isActive(resting)) {
                continue
            }

            val fillQuantity =
                minOf(
                    incoming.remainingQuantity,
                    resting.remainingQuantity
                )

            if (fillQuantity <= BigDecimal.ZERO) {
                continue
            }

            val tradePrice =
                resting.price
                    ?: continue

            /*
             * Determine which order is BUY
             * and which order is SELL.
             */
            val buyOrder: Order
            val sellOrder: Order

            if (incoming.side == OrderSide.BUY) {
                buyOrder = incoming
                sellOrder = resting
            } else {
                buyOrder = resting
                sellOrder = incoming
            }

            /*
             * A trade always executes at the resting
             * order's price.
             */
            val trade =
                tradeRepository.save(
                    Trade(
                        symbol = incoming.symbol,
                        buyOrderId = buyOrder.id,
                        sellOrderId = sellOrder.id,
                        price = tradePrice,
                        quantity = fillQuantity
                    )
                )

            /*
             * Settle the trade.
             *
             * WalletService is responsible for:
             *
             * BUY:
             *   - consuming the buyer's reserved quote
             *   - refunding price improvement
             *   - crediting base asset
             *
             * SELL:
             *   - consuming reserved base asset
             *   - crediting quote asset
             */
            walletService.settleTrade(
                buyerId = buyOrder.accountId,
                sellerId = sellOrder.accountId,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                baseQuantity = fillQuantity,
                tradePrice = tradePrice,
                buyerLimitPrice = buyOrder.price!!,
                referenceId = trade.id
            )

            /*
             * Reduce remaining quantities.
             */
            incoming.remainingQuantity =
                incoming.remainingQuantity.subtract(fillQuantity)

            resting.remainingQuantity =
                resting.remainingQuantity.subtract(fillQuantity)

            /*
             * Update statuses.
             */
            incoming.status =
                statusAfterFill(
                    remainingQuantity = incoming.remainingQuantity
                )

            resting.status =
                statusAfterFill(
                    remainingQuantity = resting.remainingQuantity
                )

            /*
             * Save the resting order after the fill.
             */
            orderRepository.save(resting)

            /*
             * Notify clients about the new trade.
             */
            broadcastService.broadcastTrade(trade)

            /*
             * If incoming order has been completely
             * filled, stop looking for more matches.
             */
            if (incoming.status == OrderStatus.FILLED) {
                break
            }
        }

        /*
         * Persist the incoming order after all fills.
         */
        orderRepository.save(incoming)

        /*
         * Notify clients that the order book changed.
         */
        broadcastService.broadcastOrderBook(
            incoming.symbol
        )
    }

    /**
     * Returns true when an order can still participate
     * in matching.
     */
    private fun isActive(order: Order): Boolean {
        return (
            order.status == OrderStatus.OPEN ||
                order.status == OrderStatus.PARTIALLY_FILLED
            ) &&
            order.remainingQuantity > BigDecimal.ZERO
    }

    /**
     * Determines the resulting status after a fill.
     */
    private fun statusAfterFill(
        remainingQuantity: BigDecimal
    ): OrderStatus {

        return if (remainingQuantity <= BigDecimal.ZERO) {
            OrderStatus.FILLED
        } else {
            OrderStatus.PARTIALLY_FILLED
        }
    }

    /**
     * Determines whether the incoming order can
     * trade against the resting order.
     *
     * BUY crosses SELL when:
     *
     *   buyPrice >= sellPrice
     *
     * SELL crosses BUY when:
     *
     *   sellPrice <= buyPrice
     */
    private fun crosses(
        incoming: Order,
        resting: Order
    ): Boolean {

        val incomingPrice =
            incoming.price ?: return false

        val restingPrice =
            resting.price ?: return false

        return when (incoming.side) {

            OrderSide.BUY ->
                incomingPrice >= restingPrice

            OrderSide.SELL ->
                incomingPrice <= restingPrice
        }
    }

    /**
     * Converts:
     *
     *   BTC-USD
     *
     * into:
     *
     *   BTC / USD
     */
    private fun parseSymbol(
        symbol: String
    ): Pair<String, String> {

        val parts = symbol.split("-")

        require(parts.size == 2) {
            "Symbol must be in BASE-QUOTE form, e.g. BTC-USD"
        }

        require(
            parts[0].isNotBlank() &&
                parts[1].isNotBlank()
        ) {
            "Symbol must contain both BASE and QUOTE assets"
        }

        return parts[0] to parts[1]
    }
}