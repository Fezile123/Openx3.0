package com.openex.core.repository

import com.openex.core.domain.LedgerEntry
import com.openex.core.domain.Order
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.Trade
import com.openex.core.domain.Wallet
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): Order?
    fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<Order>
    fun findBySymbolAndStatusIn(symbol: String, statuses: List<OrderStatus>): List<Order>
}

interface TradeRepository : JpaRepository<Trade, UUID>

interface WalletRepository : JpaRepository<Wallet, UUID> {

    fun findByAccountId(accountId: UUID): List<Wallet>

    fun findByAccountIdAndAsset(
        accountId: UUID,
        asset: String
    ): Wallet?
}

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {
    fun findByReferenceId(referenceId: UUID): List<LedgerEntry>
}