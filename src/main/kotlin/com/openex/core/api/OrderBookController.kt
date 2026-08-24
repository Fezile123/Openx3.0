package com.openex.core.api

import com.openex.core.service.OrderBookService
import com.openex.core.service.OrderBookSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orderbook")
class OrderBookController(
    private val orderBookService: OrderBookService
) {

    @GetMapping
    fun getOrderBook(
        @RequestParam symbol: String
    ): ResponseEntity<OrderBookSnapshot> {

        val snapshot =
            orderBookService.getOrderBook(symbol)

        return ResponseEntity.ok(snapshot)
    }
}