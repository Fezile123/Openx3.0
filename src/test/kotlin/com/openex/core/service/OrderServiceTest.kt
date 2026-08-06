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
    lateinit var walletService: WalletService

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
            symbol = "OSTBUY-USD",
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
        walletService.deposit(bob, "OSTSELL", BigDecimal("10"))
        val baseBefore = walletRepository.findByAccountIdAndAsset(bob, "OSTSELL")!!.balance

        orderService.placeOrder(
            accountId = bob,
            symbol = "OSTSELL-USD",
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = BigDecimal("50000.00"),
            quantity = BigDecimal("0.2"),
            idempotencyKey = "test-sell-1"
        )

        val wallet = walletRepository.findByAccountIdAndAsset(bob, "OSTSELL")!!
        assertEquals(0, BigDecimal("0.2").compareTo(wallet.reserved))
        assertEquals(0, baseBefore.subtract(BigDecimal("0.2")).compareTo(wallet.balance))
    }

    @Test
    fun `placing an order with insufficient funds throws and reserves nothing`() {
        val wallet = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        val hugeQuantity = wallet.balance.add(BigDecimal("1"))

        assertThrows(InsufficientFundsException::class.java) {
            orderService.placeOrder(
                accountId = alice,
                symbol = "OSTTOOMUCH-USD",
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
                symbol = "OSTNOPRICE-USD",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                price = null,
                quantity = BigDecimal("0.1"),
                idempotencyKey = "test-buy-noprice"
            )
        }
    }

    @Test
    fun `placing an order twice with the same idempotency key returns the same order, does not double-reserve`() {
        val order1 = orderService.placeOrder(
            accountId = alice,
            symbol = "OSTIDEM-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "test-idempotent-1"
        )

        val order2 = orderService.placeOrder(
            accountId = alice,
            symbol = "OSTIDEM-USD",
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

    @Test
    fun `cancelling an open buy order releases the reserved USD`() {
        val order = orderService.placeOrder(
            accountId = alice,
            symbol = "OSTCANCEL-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("10000.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "test-cancel-1"
        )
        val walletAfterPlace = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        val balanceAfterPlace = walletAfterPlace.balance
        val reservedAfterPlace = walletAfterPlace.reserved

        val cancelled = orderService.cancelOrder(order.id, alice)

        assertEquals(OrderStatus.CANCELLED, cancelled.status)

        val walletAfterCancel = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        assertEquals(0, reservedAfterPlace.subtract(BigDecimal("10000.00")).compareTo(walletAfterCancel.reserved))
        assertEquals(0, balanceAfterPlace.add(BigDecimal("10000.00")).compareTo(walletAfterCancel.balance))
    }

    @Test
    fun `cancelling someone else's order throws`() {
        val order = orderService.placeOrder(
            accountId = alice,
            symbol = "OSTWRONG-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "test-cancel-wrongowner"
        )

        assertThrows(IllegalArgumentException::class.java) {
            orderService.cancelOrder(order.id, bob)
        }
    }

    @Test
    fun `cancelling an already-cancelled order is a no-op`() {
        val order = orderService.placeOrder(
            accountId = alice,
            symbol = "OSTCANC2-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("1"),
            idempotencyKey = "test-cancel-twice"
        )

        orderService.cancelOrder(order.id, alice)
        val walletAfterFirstCancel = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.reserved

        val secondResult = orderService.cancelOrder(order.id, alice)

        assertEquals(OrderStatus.CANCELLED, secondResult.status)
        val walletAfterSecondCancel = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.reserved
        assertEquals(0, walletAfterFirstCancel.compareTo(walletAfterSecondCancel))
    }

    @Test
    fun `end-to-end - placing a crossing order settles both wallets correctly through placeOrder alone`() {
        walletService.deposit(bob, "E2ECOIN", BigDecimal("10"))

        val aliceUsdBefore = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        val aliceCoinBefore = walletRepository.findByAccountIdAndAsset(alice, "E2ECOIN")?.balance ?: BigDecimal.ZERO
        val bobUsdBefore = walletRepository.findByAccountIdAndAsset(bob, "USD")?.balance ?: BigDecimal.ZERO
        val bobCoinBefore = walletRepository.findByAccountIdAndAsset(bob, "E2ECOIN")!!.balance

        val sellOrder = orderService.placeOrder(
            accountId = bob, symbol = "E2ECOIN-USD", side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("50.00"), quantity = BigDecimal("2"), idempotencyKey = "e2e-sell-e2ecoin-1"
        )
        assertEquals(OrderStatus.OPEN, sellOrder.status)

        val buyOrder = orderService.placeOrder(
            accountId = alice, symbol = "E2ECOIN-USD", side = OrderSide.BUY, type = OrderType.LIMIT,
            price = BigDecimal("50.00"), quantity = BigDecimal("2"), idempotencyKey = "e2e-buy-e2ecoin-1"
        )

        assertEquals(OrderStatus.FILLED, buyOrder.status)
        assertEquals(0, BigDecimal.ZERO.compareTo(buyOrder.remainingQuantity))

        val sellAfter = orderRepository.findById(sellOrder.id).get()
        assertEquals(OrderStatus.FILLED, sellAfter.status)

        val aliceUsdAfter = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        val bobUsdAfter = walletRepository.findByAccountIdAndAsset(bob, "USD")!!.balance
        assertEquals(0, aliceUsdBefore.subtract(BigDecimal("100.00")).compareTo(aliceUsdAfter))
        assertEquals(0, bobUsdBefore.add(BigDecimal("100.00")).compareTo(bobUsdAfter))

        val aliceCoinAfter = walletRepository.findByAccountIdAndAsset(alice, "E2ECOIN")!!.balance
        val bobCoinAfter = walletRepository.findByAccountIdAndAsset(bob, "E2ECOIN")!!.balance
        assertEquals(0, aliceCoinBefore.add(BigDecimal("2")).compareTo(aliceCoinAfter))
        assertEquals(0, bobCoinBefore.subtract(BigDecimal("2")).compareTo(bobCoinAfter))

        val aliceUsdWallet = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        val bobCoinWallet = walletRepository.findByAccountIdAndAsset(bob, "E2ECOIN")!!
        assertEquals(0, BigDecimal.ZERO.compareTo(aliceUsdWallet.reserved))
        assertEquals(0, BigDecimal.ZERO.compareTo(bobCoinWallet.reserved))
    }
}