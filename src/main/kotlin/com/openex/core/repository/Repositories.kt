package com.openex.core.repository

import com.openex.core.domain.LedgerEntry
import com.openex.core.domain.Order
import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.domain.Trade
import com.openex.core.domain.Wallet
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {

    fun findByIdempotencyKey(
        idempotencyKey: String
    ): Order?

    fun findByAccountIdOrderByCreatedAtDesc(
        accountId: UUID
    ): List<Order>

    fun findBySymbolAndStatusIn(
        symbol: String,
        statuses: List<OrderStatus>
    ): List<Order>

    /**
     * Find active opposite-side orders that can potentially
     * participate in matching.
     *
     * Actual price crossing is still checked by MatchingEngine.
     *
     * Orders are returned in price-time priority:
     *
     * BUY incoming  -> lowest SELL first
     * SELL incoming -> highest BUY first
     */
    @Query(
        """
        SELECT o
        FROM Order o
        WHERE o.symbol = :symbol
          AND o.side = :side
          AND o.type = :type
          AND o.status IN :statuses
          AND o.remainingQuantity > 0
        ORDER BY o.createdAt ASC
        """
    )
    fun findActiveOrdersForMatching(
        @Param("symbol") symbol: String,
        @Param("side") side: OrderSide,
        @Param("type") type: OrderType,
        @Param("statuses") statuses: List<OrderStatus>
    ): List<Order>
}

interface TradeRepository : JpaRepository<Trade, UUID> {

    fun findTop50BySymbolOrderByExecutedAtDesc(
        symbol: String
    ): List<Trade>
}

interface WalletRepository : JpaRepository<Wallet, UUID> {

    fun findByAccountId(
        accountId: UUID
    ): List<Wallet>

    fun findByAccountIdAndAsset(
        accountId: UUID,
        asset: String
    ): Wallet?

    /**
     * Locks the wallet row while the current transaction
     * performs a balance/reservation update.
     *
     * This prevents concurrent operations from reading
     * and modifying the same wallet simultaneously.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT w
        FROM Wallet w
        WHERE w.accountId = :accountId
          AND w.asset = :asset
        """
    )
    fun findByAccountIdAndAssetForUpdate(
        @Param("accountId") accountId: UUID,
        @Param("asset") asset: String
    ): Wallet?
}

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {

    fun findByReferenceId(
        referenceId: UUID
    ): List<LedgerEntry>
}