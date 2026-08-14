package com.openex.core.service

import com.openex.core.domain.Trade
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

/**
 * Pushes live updates to connected WebSocket clients. Called after any
 * event that changes an order book or produces a trade — placing an
 * order, matching, or cancelling. Clients subscribe to these topics to
 * receive pushes instead of polling the REST API.
 */
@Service
class BroadcastService(
    private val messagingTemplate: SimpMessagingTemplate,
    private val orderBookService: OrderBookService
) {

    fun broadcastOrderBook(symbol: String) {
        val snapshot = orderBookService.getOrderBook(symbol)
        messagingTemplate.convertAndSend("/topic/orderbook/$symbol", snapshot)
    }

    fun broadcastTrade(trade: Trade) {
        messagingTemplate.convertAndSend("/topic/trades/${trade.symbol}", trade)
    }
}