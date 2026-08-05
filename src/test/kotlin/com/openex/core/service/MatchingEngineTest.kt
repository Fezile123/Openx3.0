package com.openex.core.service

import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.repository.OrderRepository
import com.openex.core.repository.TradeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@Transactional
class MatchingEngineTest {

    @Autowired
    lateinit var matchingEngine: MatchingEngine

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var tradeRepository: TradeRepository

    private val alice: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val bob: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `incoming buy order fully fills a matching resting sell order`() {
        // Bob rests a SELL at 100.00 for 1 unit
        val sellOrder = orderService.placeOrder(
            accountId = bob,
            symbol = "BTC-USD",
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "match-test-sell-1"
        )

        // Alice comes in with a BUY at 100.00 for 1 unit — should fully match
        val buyOrder = orderService.placeOrder(
            accountId = alice,
            symbol = "BTC-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "match-test-buy-1"
        )

        matchingEngine.match(buyOrder.id)

        val filledBuy = orderRepository.findById(buyOrder.id).get()
        val filledSell = orderRepository.findById(sellOrder.id).get()

        assertEquals(OrderStatus.FILLED, filledBuy.status)
        assertEquals(OrderStatus.FILLED, filledSell.status)
        assertEquals(0, BigDecimal.ZERO.compareTo(filledBuy.remainingQuantity))
        assertEquals(0, BigDecimal.ZERO.compareTo(filledSell.remainingQuantity))

        val trades = tradeRepository.findAll().filter {
            it.buyOrderId == buyOrder.id || it.sellOrderId == sellOrder.id
        }
        assertEquals(1, trades.size)
        assertEquals(0, BigDecimal("100.00").compareTo(trades[0].price))
        assertEquals(0, BigDecimal("1").compareTo(trades[0].quantity))
    }
}
