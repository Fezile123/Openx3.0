package com.openex.core.service

import com.openex.core.domain.EntryType
import com.openex.core.domain.LedgerEntry
import com.openex.core.domain.Wallet
import com.openex.core.repository.LedgerEntryRepository
import com.openex.core.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

class InsufficientFundsException(message: String) : RuntimeException(message)

@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {
    companion object {
        // Counterparty for all deposits/withdrawals — see V4 migration.
        val SYSTEM_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    }

    /**
     * Credits [accountId]'s wallet and debits the system account by the
     * same amount, as one atomic operation. Returns the referenceId
     * shared by both ledger entries.
     */
    @Transactional
    fun deposit(accountId: UUID, asset: String, amount: BigDecimal): UUID {
        require(amount > BigDecimal.ZERO) { "Deposit amount must be positive" }

        val referenceId = UUID.randomUUID()
        writeLedgerPair(
            userAccountId = accountId,
            asset = asset,
            amount = amount,
            userEntryType = EntryType.CREDIT,
            systemEntryType = EntryType.DEBIT,
            referenceId = referenceId,
            referenceType = "DEPOSIT"
        )

        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: Wallet(accountId = accountId, asset = asset)
        wallet.balance = wallet.balance.add(amount)
        walletRepository.save(wallet)

        return referenceId
    }

    /**
     * Debits [accountId]'s wallet and credits the system account by the
     * same amount. Throws [InsufficientFundsException] if the wallet
     * doesn't have enough balance — no partial writes happen in that case
     * because the whole method runs in one transaction that rolls back
     * on exception.
     */
    @Transactional
    fun withdraw(accountId: UUID, asset: String, amount: BigDecimal): UUID {
        require(amount > BigDecimal.ZERO) { "Withdrawal amount must be positive" }

        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: throw InsufficientFundsException(
                "No $asset wallet found for account $accountId"
            )

        if (wallet.balance < amount) {
            throw InsufficientFundsException(
                "Insufficient $asset balance: have ${wallet.balance}, need $amount"
            )
        }

        val referenceId = UUID.randomUUID()
        writeLedgerPair(
            userAccountId = accountId,
            asset = asset,
            amount = amount,
            userEntryType = EntryType.DEBIT,
            systemEntryType = EntryType.CREDIT,
            referenceId = referenceId,
            referenceType = "WITHDRAWAL"
        )

        wallet.balance = wallet.balance.subtract(amount)
        walletRepository.save(wallet)

        return referenceId
    }

    private fun writeLedgerPair(
        userAccountId: UUID,
        asset: String,
        amount: BigDecimal,
        userEntryType: EntryType,
        systemEntryType: EntryType,
        referenceId: UUID,
        referenceType: String
    ) {
        ledgerEntryRepository.save(
            LedgerEntry(
                accountId = userAccountId,
                asset = asset,
                entryType = userEntryType,
                amount = amount,
                referenceId = referenceId,
                referenceType = referenceType
            )
        )
        ledgerEntryRepository.save(
            LedgerEntry(
                accountId = SYSTEM_ACCOUNT_ID,
                asset = asset,
                entryType = systemEntryType,
                amount = amount,
                referenceId = referenceId,
                referenceType = referenceType
            )
        )
    }
}
