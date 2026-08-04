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

    /**
     * Real double-spend test: two threads race to withdraw the wallet's
     * FULL balance at the same time. Exactly one must win; the other must
     * fail cleanly (insufficient funds OR an optimistic-lock conflict from
     * the Wallet.version field) — never both succeeding and pushing the
     * balance negative.
     *
     * This test does NOT participate in the class-level @Transactional
     * rollback: worker threads use separate DB connections, so the setup
     * deposit must actually commit before they can see it. We clean up
     * manually at the end instead.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `concurrent withdrawals cannot double-spend`() {
        val asset = "TESTCOIN"

        // Clean slate for this asset, committed for real.
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
        startSignal.countDown() // release both at once
        doneSignal.await()

        val successCount = results.count { it.isSuccess }
        assertEquals(1, successCount, "Exactly one concurrent withdrawal should succeed, not zero or two")

        val finalBalance = walletRepository.findByAccountIdAndAsset(alice, asset)!!.balance
        assertEquals(0, BigDecimal.ZERO.compareTo(finalBalance), "Balance must land at exactly zero, never negative")

        // Manual cleanup since this test committed real data outside the rollback.
        ledgerEntryRepository.findAll()
            .filter { it.asset == asset }
            .forEach { ledgerEntryRepository.delete(it) }
        walletRepository.findByAccountIdAndAsset(alice, asset)?.let {
            walletRepository.delete(it)
        }
    }
}
