package com.openex.core.service

import com.openex.core.repository.LedgerEntryRepository
import com.openex.core.repository.WalletRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

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

        // balance must be unchanged after the rejected withdrawal
        val after = walletRepository.findByAccountIdAndAsset(alice, "USD")!!.balance
        assertEquals(balance, after)
    }
}
