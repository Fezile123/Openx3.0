package com.openex.core.service

import com.openex.core.domain.EntryType
import com.openex.core.domain.LedgerEntry
import com.openex.core.domain.Wallet
import com.openex.core.repository.LedgerEntryRepository
import com.openex.core.repository.WalletRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
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
        val SYSTEM_ACCOUNT_ID: UUID =
            UUID.fromString(
                "00000000-0000-0000-0000-000000000000"
            )
    }

    @Transactional
    fun deposit(
        accountId: UUID,
        asset: String,
        amount: BigDecimal
    ): UUID {

        require(amount > BigDecimal.ZERO) {
            "Deposit amount must be positive"
        }

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

        val wallet =
            walletRepository.findByAccountIdAndAssetForUpdate(
                accountId,
                asset
            ) ?: Wallet(
                accountId = accountId,
                asset = asset
            )

        wallet.balance =
            wallet.balance.add(amount)

        walletRepository.save(wallet)

        return referenceId
    }

    @Transactional
    fun withdraw(
        accountId: UUID,
        asset: String,
        amount: BigDecimal
    ): UUID {

        require(amount > BigDecimal.ZERO) {
            "Withdrawal amount must be positive"
        }

        val wallet =
            walletRepository.findByAccountIdAndAssetForUpdate(
                accountId,
                asset
            ) ?: throw InsufficientFundsException(
                "No $asset wallet found for account $accountId"
            )

        /*
         * Accounting model:
         *
         * balance  = total funds
         * reserved = funds locked by open orders
         * available = balance - reserved
         */
        val available =
            wallet.balance.subtract(wallet.reserved)

        if (available < amount) {
            throw InsufficientFundsException(
                "Insufficient available $asset balance: " +
                    "have $available available, need $amount"
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

        wallet.balance =
            wallet.balance.subtract(amount)

        walletRepository.save(wallet)

        return referenceId
    }

    /**
     * Locks funds for an open order.
     *
     * Accounting model:
     *
     * balance  = total funds
     * reserved = locked funds
     * available = balance - reserved
     *
     * Reserving funds therefore ONLY increases reserved.
     * It does NOT decrease balance.
     *
     * A pessimistic database lock is used here because
     * multiple orders can attempt to reserve the same
     * wallet concurrently.
     */
    @Transactional
    fun reserve(
        accountId: UUID,
        asset: String,
        amount: BigDecimal
    ) {

        require(amount > BigDecimal.ZERO) {
            "Reserve amount must be positive"
        }

        /*
         * IMPORTANT:
         *
         * Use the FOR UPDATE query here.
         *
         * Without the lock, two concurrent requests can
         * read the same wallet balance before either one
         * commits, causing over-reservation.
         */
        val wallet =
            walletRepository.findByAccountIdAndAssetForUpdate(
                accountId,
                asset
            ) ?: throw InsufficientFundsException(
                "No $asset wallet found for account $accountId"
            )

        val available =
            wallet.balance.subtract(wallet.reserved)

        if (available < amount) {
            throw InsufficientFundsException(
                "Insufficient available $asset balance: " +
                    "have $available available, need $amount"
            )
        }

        /*
         * Keep balance unchanged.
         *
         * Only move funds into the reserved portion.
         */
        wallet.reserved =
            wallet.reserved.add(amount)

        walletRepository.save(wallet)
    }

    /**
     * Releases previously reserved funds.
     *
     * Releasing only decreases reserved.
     * It does NOT increase balance because balance
     * already contains the reserved funds.
     */
    @Transactional
    fun release(
        accountId: UUID,
        asset: String,
        amount: BigDecimal
    ) {

        require(amount > BigDecimal.ZERO) {
            "Release amount must be positive"
        }

        val wallet =
            walletRepository.findByAccountIdAndAssetForUpdate(
                accountId,
                asset
            ) ?: throw InsufficientFundsException(
                "No $asset wallet found for account $accountId"
            )

        require(wallet.reserved >= amount) {
            "Cannot release $amount $asset: " +
                "only ${wallet.reserved} is reserved"
        }

        wallet.reserved =
            wallet.reserved.subtract(amount)

        walletRepository.save(wallet)
    }

    /**
     * Consumes funds that were previously reserved.
     *
     * For a completed trade, the consumed amount is removed
     * from both:
     *
     * - reserved
     * - total balance
     *
     * because those funds have now left the wallet.
     */
    @Transactional
    fun consumeReserved(
        accountId: UUID,
        asset: String,
        amount: BigDecimal
    ) {

        require(amount > BigDecimal.ZERO) {
            "Consume amount must be positive"
        }

        val wallet =
            walletRepository.findByAccountIdAndAssetForUpdate(
                accountId,
                asset
            ) ?: throw InsufficientFundsException(
                "No $asset wallet found for account $accountId"
            )

        /*
         * If the reservation is no longer available,
         * treat it as a concurrency conflict.
         */
        if (wallet.reserved < amount) {
            throw ObjectOptimisticLockingFailureException(
                Wallet::class.java,
                accountId
            )
        }

        wallet.reserved =
            wallet.reserved.subtract(amount)

        wallet.balance =
            wallet.balance.subtract(amount)

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

        require(baseQuantity > BigDecimal.ZERO) {
            "Base quantity must be positive"
        }

        require(tradePrice > BigDecimal.ZERO) {
            "Trade price must be positive"
        }

        val quoteOwed =
            tradePrice.multiply(baseQuantity)

        val quoteReservedForFill =
            buyerLimitPrice.multiply(baseQuantity)

        val priceImprovement =
            quoteReservedForFill.subtract(quoteOwed)

        /*
         * BUYER
         *
         * Consume the actual quote currency used
         * for this trade.
         */
        consumeReserved(
            accountId = buyerId,
            asset = quoteAsset,
            amount = quoteOwed
        )

        /*
         * If the execution price was better than the
         * buyer's limit price, return the difference
         * from reserved funds.
         */
        if (priceImprovement > BigDecimal.ZERO) {

            release(
                accountId = buyerId,
                asset = quoteAsset,
                amount = priceImprovement
            )
        }

        /*
         * SELLER receives quote currency.
         */
        val sellerQuoteWallet =
            walletRepository.findByAccountIdAndAssetForUpdate(
                sellerId,
                quoteAsset
            ) ?: Wallet(
                accountId = sellerId,
                asset = quoteAsset
            )

        sellerQuoteWallet.balance =
            sellerQuoteWallet.balance.add(quoteOwed)

        walletRepository.save(sellerQuoteWallet)

        ledgerEntryRepository.save(
            LedgerEntry(
                accountId = buyerId,
                asset = quoteAsset,
                entryType = EntryType.DEBIT,
                amount = quoteOwed,
                referenceId = referenceId,
                referenceType = "TRADE"
            )
        )

        ledgerEntryRepository.save(
            LedgerEntry(
                accountId = sellerId,
                asset = quoteAsset,
                entryType = EntryType.CREDIT,
                amount = quoteOwed,
                referenceId = referenceId,
                referenceType = "TRADE"
            )
        )

        /*
         * SELLER
         *
         * Consume the base asset that was reserved
         * for the sell order.
         */
        consumeReserved(
            accountId = sellerId,
            asset = baseAsset,
            amount = baseQuantity
        )

        /*
         * BUYER receives base asset.
         */
        val buyerBaseWallet =
            walletRepository.findByAccountIdAndAssetForUpdate(
                buyerId,
                baseAsset
            ) ?: Wallet(
                accountId = buyerId,
                asset = baseAsset
            )

        buyerBaseWallet.balance =
            buyerBaseWallet.balance.add(baseQuantity)

        walletRepository.save(buyerBaseWallet)

        ledgerEntryRepository.save(
            LedgerEntry(
                accountId = sellerId,
                asset = baseAsset,
                entryType = EntryType.DEBIT,
                amount = baseQuantity,
                referenceId = referenceId,
                referenceType = "TRADE"
            )
        )

        ledgerEntryRepository.save(
            LedgerEntry(
                accountId = buyerId,
                asset = baseAsset,
                entryType = EntryType.CREDIT,
                amount = baseQuantity,
                referenceId = referenceId,
                referenceType = "TRADE"
            )
        )
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