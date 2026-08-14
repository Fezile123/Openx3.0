package com.openex.core.service

import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@Transactional
class OrderBookServiceTest {

    @Autowired
    lateinit var orderBookService: OrderBookService

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var walletService: WalletService

    private val alice: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val bob: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `order book aggregates multiple orders at the same price level`() {
        val symbol = "OBTEST${UUID.randomUUID().toString().take(6)}-USD"

        walletService.deposit(bob, symbol.substringBefore("-"), BigDecimal("10"))

        orderService.placeOrder(
            accountId = bob, symbol = symbol, side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("50.00"), quantity = BigDecimal("1"), idempotencyKey = "obtest-sell-1"
        )
        orderService.placeOrder(
            accountId = bob, symbol = symbol, side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("50.00"), quantity = BigDecimal("2"), idempotencyKey = "obtest-sell-2"
        )

        val book = orderBookService.getOrderBook(symbol)

        assertEquals(1, book.asks.size, "Two sell orders at the same price should collapse into one level")
        assertEquals(0, BigDecimal("50.00").compareTo(book.asks[0].price))
        assertEquals(0, BigDecimal("3").compareTo(book.asks[0].quantity))
    }

    @Test
    fun `order book sorts bids highest-first and asks lowest-first`() {
        val symbol = "OBTEST${UUID.randomUUID().toString().take(6)}-USD"

        walletService.deposit(bob, symbol.substringBefore("-"), BigDecimal("10"))

        orderService.placeOrder(
            accountId = bob, symbol = symbol, side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("60.00"), quantity = BigDecimal("1"), idempotencyKey = "obtest-ask-high"
        )
        orderService.placeOrder(
            accountId = bob, symbol = symbol, side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("55.00"), quantity = BigDecimal("1"), idempotencyKey = "obtest-ask-low"
        )
        orderService.placeOrder(
            accountId = alice, symbol = symbol, side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("40.00"), quantity = BigDecimal("1"), idempotencyKey = "obtest-bid-low"
        )
        orderService.placeOrder(
            accountId = alice, symbol = symbol, side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("45.00"), quantity = BigDecimal("1"), idempotencyKey = "obtest-bid-high"
        )

        val book = orderBookService.getOrderBook(symbol)

        assertEquals(2, book.asks.size)
        assertEquals(0, BigDecimal("55.00").compareTo(book.asks[0].price), "Best (lowest) ask should be first")
        assertEquals(0, BigDecimal("60.00").compareTo(book.asks[1].price))

        assertEquals(2, book.bids.size)
        assertEquals(0, BigDecimal("45.00").compareTo(book.bids[0].price), "Best (highest) bid should be first")
        assertEquals(0, BigDecimal("40.00").compareTo(book.bids[1].price))
    }

    @Test
    fun `filled orders do not appear in the order book`() {
        val symbol = "OBTEST${UUID.randomUUID().toString().take(6)}-USD"

        walletService.deposit(bob, symbol.substringBefore("-"), BigDecimal("10"))

        orderService.placeOrder(
            accountId = bob, symbol = symbol, side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("50.00"), quantity = BigDecimal("1"), idempotencyKey = "obtest-fill-sell"
        )
        orderService.placeOrder(
            accountId = alice, symbol = symbol, side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("50.00"), quantity = BigDecimal("1"), idempotencyKey = "obtest-fill-buy"
        )

        val book = orderBookService.getOrderBook(symbol)

        assertEquals(0, book.asks.size, "Fully filled sell order should not appear")
        assertEquals(0, book.bids.size, "Fully filled buy order should not appear")
    }
}