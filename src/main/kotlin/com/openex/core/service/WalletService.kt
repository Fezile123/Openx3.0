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
        writeLedgerPair(accountId, asset, amount, EntryType.CREDIT, EntryType.DEBIT, referenceId, "DEPOSIT")
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
            throw InsufficientFundsException("Insufficient $asset balance: have ${wallet.balance}, need $amount")
        }
        val referenceId = UUID.randomUUID()
        writeLedgerPair(accountId, asset, amount, EntryType.DEBIT, EntryType.CREDIT, referenceId, "WITHDRAWAL")
        wallet.balance = wallet.balance.subtract(amount)
        walletRepository.save(wallet)
        return referenceId
    }

    @Transactional
    fun reserve(accountId: UUID, asset: String, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Reserve amount must be positive" }
        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: throw InsufficientFundsException("No $asset wallet found for account $accountId")
        val available = wallet.balance.subtract(wallet.reserved)
        if (available < amount) {
            throw InsufficientFundsException("Insufficient available $asset balance: have $available available, need $amount")
        }
        wallet.balance = wallet.balance.subtract(amount)
        wallet.reserved = wallet.reserved.add(amount)
        walletRepository.save(wallet)
    }

    @Transactional
    fun release(accountId: UUID, asset: String, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Release amount must be positive" }
        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: throw InsufficientFundsException("No $asset wallet found for account $accountId")
        require(wallet.reserved >= amount) { "Cannot release $amount $asset: only ${wallet.reserved} is reserved" }
        wallet.reserved = wallet.reserved.subtract(amount)
        wallet.balance = wallet.balance.add(amount)
        walletRepository.save(wallet)
    }

    @Transactional
    fun consumeReserved(accountId: UUID, asset: String, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Consume amount must be positive" }
        val wallet = walletRepository.findByAccountIdAndAsset(accountId, asset)
            ?: throw InsufficientFundsException("No $asset wallet found for account $accountId")
        if (wallet.reserved < amount) {
            // Another concurrent trade already consumed this reservation first —
            // a real race, not a business-rule violation. Signal it the same way
            // as an optimistic-lock conflict so OrderService's retry loop picks
            // it up and re-evaluates against fresh data instead of failing hard.
            throw org.springframework.orm.ObjectOptimisticLockingFailureException(
                "com.openex.core.domain.Wallet", accountId.toString()
            )
        }
        wallet.reserved = wallet.reserved.subtract(amount)
        walletRepository.save(wallet)
    }

    @Transactional
    fun settleTrade(
        buyerId: UUID,
        sellerId: UUID,
        baseAsset: String,
        quoteAsset: String,
        baseQuantity: BigDecimal,
        tradePrice: BigDecimal,
        buyerLimitPrice: BigDecimal,
        referenceId: UUID
    ) {
        require(baseQuantity > BigDecimal.ZERO) { "Base quantity must be positive" }
        require(tradePrice > BigDecimal.ZERO) { "Trade price must be positive" }

        val quoteOwed = tradePrice.multiply(baseQuantity)
        val quoteReservedForFill = buyerLimitPrice.multiply(baseQuantity)
        val priceImprovement = quoteReservedForFill.subtract(quoteOwed)

        consumeReserved(buyerId, quoteAsset, quoteOwed)
        if (priceImprovement > BigDecimal.ZERO) {
            release(buyerId, quoteAsset, priceImprovement)
        }

        val sellerQuoteWallet = walletRepository.findByAccountIdAndAsset(sellerId, quoteAsset)
            ?: Wallet(accountId = sellerId, asset = quoteAsset)
        sellerQuoteWallet.balance = sellerQuoteWallet.balance.add(quoteOwed)
        walletRepository.save(sellerQuoteWallet)

        ledgerEntryRepository.save(LedgerEntry(accountId = buyerId, asset = quoteAsset, entryType = EntryType.DEBIT, amount = quoteOwed, referenceId = referenceId, referenceType = "TRADE"))
        ledgerEntryRepository.save(LedgerEntry(accountId = sellerId, asset = quoteAsset, entryType = EntryType.CREDIT, amount = quoteOwed, referenceId = referenceId, referenceType = "TRADE"))

        consumeReserved(sellerId, baseAsset, baseQuantity)

        val buyerBaseWallet = walletRepository.findByAccountIdAndAsset(buyerId, baseAsset)
            ?: Wallet(accountId = buyerId, asset = baseAsset)
        buyerBaseWallet.balance = buyerBaseWallet.balance.add(baseQuantity)
        walletRepository.save(buyerBaseWallet)

        ledgerEntryRepository.save(LedgerEntry(accountId = sellerId, asset = baseAsset, entryType = EntryType.DEBIT, amount = baseQuantity, referenceId = referenceId, referenceType = "TRADE"))
        ledgerEntryRepository.save(LedgerEntry(accountId = buyerId, asset = baseAsset, entryType = EntryType.CREDIT, amount = baseQuantity, referenceId = referenceId, referenceType = "TRADE"))
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
        ledgerEntryRepository.save(LedgerEntry(accountId = userAccountId, asset = asset, entryType = userEntryType, amount = amount, referenceId = referenceId, referenceType = referenceType))
        ledgerEntryRepository.save(LedgerEntry(accountId = SYSTEM_ACCOUNT_ID, asset = asset, entryType = systemEntryType, amount = amount, referenceId = referenceId, referenceType = referenceType))
    }
}