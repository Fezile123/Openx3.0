package com.openex.core.service

import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.repository.OrderRepository
import com.openex.core.repository.WalletRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var walletRepository: WalletRepository

    private val alice: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val bob: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `placing a limit buy order reserves price times quantity in quote currency`() {
        val usdBefore = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance

        val order = orderService.placeOrder(
            accountId = alice,
            symbol = "BTC-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("50000.00"),
            quantity = BigDecimal("0.1"),
            idempotencyKey = "test-buy-1"
        )

        assertEquals(OrderStatus.OPEN, order.status)

        val wallet = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        val expectedReserved = BigDecimal("50000.00").multiply(BigDecimal("0.1"))
        assertEquals(0, expectedReserved.compareTo(wallet.reserved))
        assertEquals(0, usdBefore.subtract(expectedReserved).compareTo(wallet.balance))
    }

    @Test
    fun `placing a limit sell order reserves quantity in base currency`() {
        val btcBefore = walletRepository.findByAccountIdAndAsset(bob, "BTC")!!.balance

        orderService.placeOrder(
            accountId = bob,
            symbol = "BTC-USD",
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = BigDecimal("50000.00"),
            quantity = BigDecimal("0.2"),
            idempotencyKey = "test-sell-1"
        )

        val wallet = walletRepository.findByAccountIdAndAsset(bob, "BTC")!!
        assertEquals(0, BigDecimal("0.2").compareTo(wallet.reserved))
        assertEquals(0, btcBefore.subtract(BigDecimal("0.2")).compareTo(wallet.balance))
    }

    @Test
    fun `placing an order with insufficient funds throws and reserves nothing`() {
        val wallet = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        val hugeQuantity = wallet.balance.add(BigDecimal("1")) // way more than she can afford at price 1

        assertThrows(InsufficientFundsException::class.java) {
            orderService.placeOrder(
                accountId = alice,
                symbol = "BTC-USD",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                price = BigDecimal("1.00"),
                quantity = hugeQuantity,
                idempotencyKey = "test-buy-toomuch"
            )
        }

        val orders = orderRepository.findByIdempotencyKey("test-buy-toomuch")
        assertEquals(null, orders)
    }

    @Test
    fun `placing a limit order without a price throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            orderService.placeOrder(
                accountId = alice,
                symbol = "BTC-USD",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                price = null,
                quantity = BigDecimal("0.1"),
                idempotencyKey = "test-buy-nopricce"
            )
        }
    }

    @Test
    fun `placing an order twice with the same idempotency key returns the same order, does not double-reserve`() {
        val order1 = orderService.placeOrder(
            accountId = alice,
            symbol = "BTC-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "test-idempotent-1"
        )

        val order2 = orderService.placeOrder(
            accountId = alice,
            symbol = "BTC-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "test-idempotent-1"
        )

        assertEquals(order1.id, order2.id)

        val wallet = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        assertTrue(wallet.reserved < BigDecimal("200.00"), "Reserved should reflect one order, not two")
    }
}
