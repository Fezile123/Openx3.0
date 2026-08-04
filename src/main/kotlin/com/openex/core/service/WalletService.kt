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
        val SYSTEM_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    }

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

    @Transactional
    fun withdraw(accountId: UUID, asset: String, amount: BigDecimal): UUID {
        require(amount > BigDecimal.ZERO) { "Withdrawal amount must be positive" }

        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: throw InsufficientFundsException("No $asset wallet found for account $accountId")

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

    /**
     * Moves [amount] from spendable balance into `reserved`, without
     * changing the wallet's total (balance + reserved). Used when placing
     * an order, so the funds it needs can't also be spent by another order.
     * Available balance = balance - reserved; must cover [amount] or this
     * throws InsufficientFundsException.
     */
    @Transactional
    fun reserve(accountId: UUID, asset: String, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Reserve amount must be positive" }

        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: throw InsufficientFundsException("No $asset wallet found for account $accountId")

        val available = wallet.balance.subtract(wallet.reserved)
        if (available < amount) {
            throw InsufficientFundsException(
                "Insufficient available $asset balance: have $available available, need $amount"
            )
        }

        wallet.balance = wallet.balance.subtract(amount)
        wallet.reserved = wallet.reserved.add(amount)
        walletRepository.save(wallet)
    }

    /**
     * Moves [amount] back from `reserved` to spendable balance. Used when
     * an order is cancelled, or after a fill has consumed only part of
     * the original reservation.
     */
    @Transactional
    fun release(accountId: UUID, asset: String, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Release amount must be positive" }

        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: throw InsufficientFundsException("No $asset wallet found for account $accountId")

        require(wallet.reserved >= amount) {
            "Cannot release $amount $asset: only ${wallet.reserved} is reserved"
        }

        wallet.reserved = wallet.reserved.subtract(amount)
        wallet.balance = wallet.balance.add(amount)
        walletRepository.save(wallet)
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
