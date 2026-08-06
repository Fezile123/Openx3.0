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
    lateinit var walletService: WalletService

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var tradeRepository: TradeRepository

    private val alice: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val bob: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `incoming buy order fully fills a matching resting sell order`() {
        walletService.deposit(bob, "MATCHFULL", BigDecimal("10"))

        val sellOrder = orderService.placeOrder(
            accountId = bob, symbol = "MATCHFULL-USD", side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("100.00"), quantity = BigDecimal("1"), idempotencyKey = "match-test-sell-1"
        )
        val buyOrder = orderService.placeOrder(
            accountId = alice, symbol = "MATCHFULL-USD", side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("100.00"), quantity = BigDecimal("1"), idempotencyKey = "match-test-buy-1"
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

    @Test
    fun `incoming order partially fills against a smaller resting order`() {
        walletService.deposit(bob, "ETH", BigDecimal("10"))

        val sellOrder = orderService.placeOrder(
            accountId = bob, symbol = "ETH-USD", side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("10.00"), quantity = BigDecimal("1"), idempotencyKey = "partial-sell-1"
        )
        val buyOrder = orderService.placeOrder(
            accountId = alice, symbol = "ETH-USD", side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("10.00"), quantity = BigDecimal("3"), idempotencyKey = "partial-buy-1"
        )

        matchingEngine.match(buyOrder.id)

        val updatedBuy = orderRepository.findById(buyOrder.id).get()
        val updatedSell = orderRepository.findById(sellOrder.id).get()

        assertEquals(OrderStatus.PARTIALLY_FILLED, updatedBuy.status)
        assertEquals(OrderStatus.FILLED, updatedSell.status)
        assertEquals(0, BigDecimal("2").compareTo(updatedBuy.remainingQuantity))
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedSell.remainingQuantity))
    }

    @Test
    fun `cheaper resting sell order is matched before a pricier one`() {
        walletService.deposit(bob, "SOL", BigDecimal("10"))

        val expensiveSell = orderService.placeOrder(
            accountId = bob, symbol = "SOL-USD", side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("20.00"), quantity = BigDecimal("1"), idempotencyKey = "price-sell-expensive"
        )
        val cheapSell = orderService.placeOrder(
            accountId = bob, symbol = "SOL-USD", side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("15.00"), quantity = BigDecimal("1"), idempotencyKey = "price-sell-cheap"
        )
        val buyOrder = orderService.placeOrder(
            accountId = alice, symbol = "SOL-USD", side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("20.00"), quantity = BigDecimal("1"), idempotencyKey = "price-buy-1"
        )

        matchingEngine.match(buyOrder.id)

        val filledCheap = orderRepository.findById(cheapSell.id).get()
        val untouchedExpensive = orderRepository.findById(expensiveSell.id).get()

        assertEquals(OrderStatus.FILLED, filledCheap.status)
        assertEquals(OrderStatus.OPEN, untouchedExpensive.status)

        val trade = tradeRepository.findAll().first { it.sellOrderId == cheapSell.id }
        assertEquals(0, BigDecimal("15.00").compareTo(trade.price))
    }

    @Test
    fun `at equal price the earlier resting order is matched first`() {
        walletService.deposit(bob, "DOGE", BigDecimal("10"))

        val firstSell = orderService.placeOrder(
            accountId = bob, symbol = "DOGE-USD", side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("1.00"), quantity = BigDecimal("1"), idempotencyKey = "time-sell-first"
        )
        Thread.sleep(10)
        val secondSell = orderService.placeOrder(
            accountId = bob, symbol = "DOGE-USD", side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("1.00"), quantity = BigDecimal("1"), idempotencyKey = "time-sell-second"
        )
        val buyOrder = orderService.placeOrder(
            accountId = alice, symbol = "DOGE-USD", side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("1.00"), quantity = BigDecimal("1"), idempotencyKey = "time-buy-1"
        )

        matchingEngine.match(buyOrder.id)

        val filledFirst = orderRepository.findById(firstSell.id).get()
        val untouchedSecond = orderRepository.findById(secondSell.id).get()

        assertEquals(OrderStatus.FILLED, filledFirst.status)
        assertEquals(OrderStatus.OPEN, untouchedSecond.status)
    }
}