package com.openex.core.api

import com.openex.core.domain.Trade
import com.openex.core.repository.TradeRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TradeResponse(
    val id: UUID,
    val symbol: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val executedAt: Instant
) {
    companion object {
        fun from(trade: Trade) = TradeResponse(
            id = trade.id,
            symbol = trade.symbol,
            price = trade.price,
            quantity = trade.quantity,
            executedAt = trade.executedAt
        )
    }
}

@RestController
@RequestMapping("/trades")
class TradeController(
    private val tradeRepository: TradeRepository
) {

    @GetMapping
    fun getRecentTrades(
        @RequestParam symbol: String
    ): ResponseEntity<List<TradeResponse>> {

        val trades =
            tradeRepository
                .findTop50BySymbolOrderByExecutedAtDesc(symbol)

        return ResponseEntity.ok(
            trades.map { TradeResponse.from(it) }
        )
    }
}