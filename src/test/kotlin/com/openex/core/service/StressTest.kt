package com.openex.core.service

import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.repository.OrderRepository
import com.openex.core.repository.WalletRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class StressTest {

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var walletService: WalletService

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var walletRepository: WalletRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private fun createRealAccount(): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update("INSERT INTO accounts (id, email) VALUES (?, ?)", id, "stress-$id@openex.test")
        return id
    }

    @Test
    fun `20 concurrent buy orders against one resting sell order never over-fill or corrupt balances`() {
        val asset = "STR${UUID.randomUUID().toString().take(8)}"
        val symbol = "$asset-USD"
        val threadCount = 20
        val restingQuantity = BigDecimal("10")

        // Fresh seller account every run — no cross-run contamination possible.
        val seller = createRealAccount()
        walletService.deposit(seller, asset, BigDecimal("100"))

        val sellOrder = orderService.placeOrder(
            accountId = seller, symbol = symbol, side = OrderSide.SELL, type = OrderType.LIMIT,
            price = BigDecimal("10.00"), quantity = restingQuantity, idempotencyKey = "stress-sell-${UUID.randomUUID()}"
        )

        val buyers = (1..threadCount).map {
            val buyerId = createRealAccount()
            walletService.deposit(buyerId, "USD", BigDecimal("1000"))
            buyerId
        }

        val pool = Executors.newFixedThreadPool(threadCount)
        val startSignal = CountDownLatch(1)
        val doneSignal = CountDownLatch(threadCount)
        val results = java.util.Collections.synchronizedList(mutableListOf<Result<Unit>>())

        buyers.forEachIndexed { index, buyerId ->
            pool.submit {
                startSignal.await()
                val result = runCatching {
                    orderService.placeOrder(
                        accountId = buyerId, symbol = symbol, side = OrderSide.BUY, type = OrderType.LIMIT,
                        price = BigDecimal("10.00"), quantity = BigDecimal("1"),
                        idempotencyKey = "stress-buy-$index-${UUID.randomUUID()}"
                    )
                    Unit
                }
                results.add(result)
                doneSignal.countDown()
            }
        }

        startSignal.countDown()
        doneSignal.await(90, TimeUnit.SECONDS)
        pool.shutdown()

        val failures = results.filter { it.isFailure }
        assertTrue(failures.isEmpty(), "No placeOrder call should throw: ${failures.map { it.exceptionOrNull()?.message }}")

        val finalSell = orderRepository.findById(sellOrder.id).get()
        assertEquals(OrderStatus.FILLED, finalSell.status)
        assertEquals(0, BigDecimal.ZERO.compareTo(finalSell.remainingQuantity))

        val sellerWallet = walletRepository.findByAccountIdAndAsset(seller, asset)!!
        assertEquals(0, BigDecimal.ZERO.compareTo(sellerWallet.reserved))
        assertEquals(0, BigDecimal("90").compareTo(sellerWallet.balance), "Seller should have 90 left: deposited 100, sold 10")

        val totalReceived = buyers.sumOf { buyerId ->
            walletRepository.findByAccountIdAndAsset(buyerId, asset)?.balance ?: BigDecimal.ZERO
        }
        assertEquals(0, restingQuantity.compareTo(totalReceived))
    }
}