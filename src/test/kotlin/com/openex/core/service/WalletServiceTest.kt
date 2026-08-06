package com.openex.core.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import com.openex.core.repository.LedgerEntryRepository
import com.openex.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch

@SpringBootTest
@Transactional
class WalletServiceTest {

    @Autowired
    lateinit var walletService: WalletService

    @Autowired
    lateinit var walletRepository: WalletRepository

    @Autowired
    lateinit var ledgerEntryRepository: LedgerEntryRepository

    private val alice: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val bob: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `deposit increases wallet balance`() {
        val before = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        walletService.deposit(alice, "USD", BigDecimal("100.00"))
        val after = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        assertEquals(before.add(BigDecimal("100.00")), after)
    }

    @Test
    fun `deposit writes two balanced ledger entries`() {
        val reference = walletService.deposit(alice, "USD", BigDecimal("50.00"))
        val entries = ledgerEntryRepository.findByReferenceId(reference)
        assertEquals(2, entries.size)

        val credit = entries.first { it.accountId == alice }
        val debit = entries.first { it.accountId != alice }

        assertEquals(BigDecimal("50.00"), credit.amount)
        assertEquals(BigDecimal("50.00"), debit.amount)
    }

    @Test
    fun `withdraw decreases wallet balance`() {
        val before = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        walletService.withdraw(alice, "USD", BigDecimal("30.00"))
        val after = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        assertEquals(before.subtract(BigDecimal("30.00")), after)
    }

    @Test
    fun `withdraw writes two balanced ledger entries`() {
        val reference = walletService.withdraw(alice, "USD", BigDecimal("20.00"))
        val entries = ledgerEntryRepository.findByReferenceId(reference)
        assertEquals(2, entries.size)

        val debit = entries.first { it.accountId == alice }
        val credit = entries.first { it.accountId != alice }

        assertEquals(BigDecimal("20.00"), debit.amount)
        assertEquals(BigDecimal("20.00"), credit.amount)
    }

    @Test
    fun `withdraw rejects insufficient funds`() {
        val balance = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        val tooMuch = balance.add(BigDecimal("1.00"))

        assertThrows(InsufficientFundsException::class.java) {
            walletService.withdraw(alice, "USD", tooMuch)
        }

        val after = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        assertEquals(balance, after)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `concurrent withdrawals cannot double-spend`() {
        val asset = "TESTCOIN"

        walletRepository.findByAccountIdAndAsset(alice, asset)?.let {
            it.balance = BigDecimal.ZERO
            walletRepository.save(it)
        }
        walletService.deposit(alice, asset, BigDecimal("100.00"))

        val results = java.util.Collections.synchronizedList(mutableListOf<Result<UUID>>())
        val startSignal = CountDownLatch(1)
        val doneSignal = CountDownLatch(2)

        val attempt = {
            startSignal.await()
            val result = runCatching { walletService.withdraw(alice, asset, BigDecimal("100.00")) }
            results.add(result)
            doneSignal.countDown()
        }

        val t1 = Thread(attempt)
        val t2 = Thread(attempt)
        t1.start()
        t2.start()
        startSignal.countDown()
        doneSignal.await()

        val successCount = results.count { it.isSuccess }
        assertEquals(1, successCount, "Exactly one concurrent withdrawal should succeed, not zero or two")

        val finalBalance = walletRepository.findByAccountIdAndAsset(alice, asset)!!.balance
        assertEquals(0, BigDecimal.ZERO.compareTo(finalBalance), "Balance must land at exactly zero, never negative")

        ledgerEntryRepository.findAll()
            .filter { it.asset == asset }
            .forEach { ledgerEntryRepository.delete(it) }
        walletRepository.findByAccountIdAndAsset(alice, asset)?.let {
            walletRepository.delete(it)
        }
    }

    @Test
    fun `reserve moves funds from balance to reserved without changing total`() {
        val walletBefore = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        val balanceBefore = walletBefore.balance
        val reservedBefore = walletBefore.reserved

        walletService.reserve(alice, "USD", BigDecimal("40.00"))

        val wallet = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        assertEquals(0, reservedBefore.add(BigDecimal("40.00")).compareTo(wallet.reserved))
        assertEquals(0, balanceBefore.subtract(BigDecimal("40.00")).compareTo(wallet.balance))
    }

    @Test
    fun `reserve rejects when available balance is too low`() {
        val wallet = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        val available = wallet.balance.subtract(wallet.reserved)
        val tooMuch = available.add(BigDecimal("1.00"))

        assertThrows(InsufficientFundsException::class.java) {
            walletService.reserve(alice, "USD", tooMuch)
        }
    }

    @Test
    fun `release moves funds from reserved back to balance`() {
        val reservedBeforeReserve = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.reserved

        walletService.reserve(alice, "USD", BigDecimal("25.00"))
        val balanceAfterReserve = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance

        walletService.release(alice, "USD", BigDecimal("25.00"))

        val afterRelease = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        assertEquals(0, balanceAfterReserve.add(BigDecimal("25.00")).compareTo(afterRelease.balance))
        assertEquals(0, reservedBeforeReserve.compareTo(afterRelease.reserved))
    }

    @Test
    fun `consumeReserved decreases reserved without touching balance`() {
        val reservedBefore = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.reserved

        walletService.reserve(alice, "USD", BigDecimal("60.00"))
        val balanceAfterReserve = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance

        walletService.consumeReserved(alice, "USD", BigDecimal("60.00"))

        val after = walletRepository.findByAccountIdAndAsset(alice, "USD")!!
        assertEquals(0, reservedBefore.compareTo(after.reserved))
        assertEquals(0, balanceAfterReserve.compareTo(after.balance))
    }

    @Test
    fun `consumeReserved rejects when reserved is less than requested amount`() {
        walletService.deposit(alice, "CONSUMETEST", BigDecimal("10.00"))
        walletService.reserve(alice, "CONSUMETEST", BigDecimal("10.00"))

        assertThrows(IllegalArgumentException::class.java) {
            walletService.consumeReserved(alice, "CONSUMETEST", BigDecimal("10.01"))
        }
    }

    @Test
    fun `settleTrade at exact buyer limit price moves funds correctly with no refund`() {
        walletService.deposit(bob, "SETTLEBASE", BigDecimal("10"))
        walletService.reserve(bob, "SETTLEBASE", BigDecimal("2"))
        walletService.deposit(alice, "SETTLEQUOTE", BigDecimal("1000"))
        walletService.reserve(alice, "SETTLEQUOTE", BigDecimal("200"))

        val aliceBaseBefore = walletRepository.findByAccountIdAndAsset(alice, "SETTLEBASE")?.balance ?: BigDecimal.ZERO
        val aliceQuoteBalanceBeforeSettle = walletRepository.findByAccountIdAndAsset(alice, "SETTLEQUOTE")!!.balance
        val bobQuoteBefore = walletRepository.findByAccountIdAndAsset(bob, "SETTLEQUOTE")?.balance ?: BigDecimal.ZERO

        val referenceId = UUID.randomUUID()
        walletService.settleTrade(
            buyerId = alice,
            sellerId = bob,
            baseAsset = "SETTLEBASE",
            quoteAsset = "SETTLEQUOTE",
            baseQuantity = BigDecimal("2"),
            tradePrice = BigDecimal("100"),
            buyerLimitPrice = BigDecimal("100"),
            referenceId = referenceId
        )

        val aliceQuote = walletRepository.findByAccountIdAndAsset(alice, "SETTLEQUOTE")!!
        val bobQuote = walletRepository.findByAccountIdAndAsset(bob, "SETTLEQUOTE")!!
        val aliceBase = walletRepository.findByAccountIdAndAsset(alice, "SETTLEBASE")!!
        val bobBase = walletRepository.findByAccountIdAndAsset(bob, "SETTLEBASE")!!

        assertEquals(0, aliceQuoteBalanceBeforeSettle.compareTo(aliceQuote.balance))
        assertEquals(0, BigDecimal.ZERO.compareTo(aliceQuote.reserved))
        assertEquals(0, bobQuoteBefore.add(BigDecimal("200")).compareTo(bobQuote.balance))
        assertEquals(0, BigDecimal.ZERO.compareTo(bobBase.reserved))
        assertEquals(0, aliceBaseBefore.add(BigDecimal("2")).compareTo(aliceBase.balance))

        val entries = ledgerEntryRepository.findByReferenceId(referenceId)
        assertEquals(4, entries.size, "Should write 4 ledger entries: quote DEBIT/CREDIT + base DEBIT/CREDIT")
    }

    @Test
    fun `settleTrade below buyer limit price refunds the price improvement to the buyer`() {
        walletService.deposit(bob, "SETTLEBASE", BigDecimal("10"))
        walletService.reserve(bob, "SETTLEBASE", BigDecimal("2"))
        walletService.deposit(alice, "SETTLEQUOTE", BigDecimal("1000"))
        walletService.reserve(alice, "SETTLEQUOTE", BigDecimal("240"))

        val aliceQuoteBalanceBeforeSettle = walletRepository.findByAccountIdAndAsset(alice, "SETTLEQUOTE")!!.balance
        val bobQuoteBefore = walletRepository.findByAccountIdAndAsset(bob, "SETTLEQUOTE")?.balance ?: BigDecimal.ZERO

        val referenceId = UUID.randomUUID()
        walletService.settleTrade(
            buyerId = alice,
            sellerId = bob,
            baseAsset = "SETTLEBASE",
            quoteAsset = "SETTLEQUOTE",
            baseQuantity = BigDecimal("2"),
            tradePrice = BigDecimal("100"),
            buyerLimitPrice = BigDecimal("120"),
            referenceId = referenceId
        )

        val aliceQuote = walletRepository.findByAccountIdAndAsset(alice, "SETTLEQUOTE")!!
        val bobQuote = walletRepository.findByAccountIdAndAsset(bob, "SETTLEQUOTE")!!

        assertEquals(0, aliceQuoteBalanceBeforeSettle.add(BigDecimal("40")).compareTo(aliceQuote.balance))
        assertEquals(0, BigDecimal.ZERO.compareTo(aliceQuote.reserved))
        assertEquals(0, bobQuoteBefore.add(BigDecimal("200")).compareTo(bobQuote.balance))
    }
}