package com.openex.core.service

import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.repository.OrderRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

data class OrderBookLevel(
    val price: BigDecimal,
    val quantity: BigDecimal
)

data class OrderBookSnapshot(
    val symbol: String,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>
)

@Service
class OrderBookService(
    private val orderRepository: OrderRepository
) {
    private val activeStatuses = listOf(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)

    /**
     * Aggregates all active (OPEN/PARTIALLY_FILLED) LIMIT orders for [symbol]
     * into price levels — multiple orders at the same price collapse into
     * one level with summed quantity, matching what a real order book
     * display shows. Bids sorted highest-first, asks lowest-first (best
     * price at index 0 for both sides).
     */
    fun getOrderBook(symbol: String): OrderBookSnapshot {
        val orders = orderRepository.findBySymbolAndStatusIn(symbol, activeStatuses)
            .filter { it.type == OrderType.LIMIT && it.price != null }

        val bids = orders.filter { it.side == OrderSide.BUY }
            .groupBy { it.price!! }
            .map { (price, group) -> OrderBookLevel(price, group.sumOf { it.remainingQuantity }) }
            .sortedByDescending { it.price }

        val asks = orders.filter { it.side == OrderSide.SELL }
            .groupBy { it.price!! }
            .map { (price, group) -> OrderBookLevel(price, group.sumOf { it.remainingQuantity }) }
            .sortedBy { it.price }

        return OrderBookSnapshot(symbol, bids, asks)
    }
}