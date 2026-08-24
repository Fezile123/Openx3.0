package com.openex.core.service

import com.openex.core.domain.Order
import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderStatus
import com.openex.core.domain.OrderType
import com.openex.core.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val walletService: WalletService,
    private val matchingEngine: MatchingEngine,
    private val broadcastService: BroadcastService,
    @Lazy private val self: OrderService
) {

    private val log =
        LoggerFactory.getLogger(OrderService::class.java)

    companion object {
        private const val MAX_RETRIES = 100
        private const val RETRY_BACKOFF_MS_MIN = 5L
        private const val RETRY_BACKOFF_MS_MAX = 60L
    }

    private fun jitteredBackoff(): Long =
        (RETRY_BACKOFF_MS_MIN..RETRY_BACKOFF_MS_MAX).random()

    fun placeOrder(
        accountId: UUID,
        symbol: String,
        side: OrderSide,
        type: OrderType,
        price: BigDecimal?,
        quantity: BigDecimal,
        idempotencyKey: String
    ): Order {

        var attempt = 0

        while (true) {
            try {
                return self.placeOrderInternal(
                    accountId = accountId,
                    symbol = symbol,
                    side = side,
                    type = type,
                    price = price,
                    quantity = quantity,
                    idempotencyKey = idempotencyKey
                )
            } catch (e: Exception) {

                if (
                    e !is ObjectOptimisticLockingFailureException &&
                    e !is DataIntegrityViolationException
                ) {
                    throw e
                }

                attempt++

                if (attempt >= MAX_RETRIES) {
                    throw e
                }

                Thread.sleep(jitteredBackoff())
            }
        }
    }

    @Transactional
fun placeOrderInternal(
    accountId: UUID,
    symbol: String,
    side: OrderSide,
    type: OrderType,
    price: BigDecimal?,
    quantity: BigDecimal,
    idempotencyKey: String
): Order {

    // 1. Idempotency check MUST happen before reserving funds.
    orderRepository
        .findByIdempotencyKey(idempotencyKey)
        ?.let { existingOrder ->
            return existingOrder
        }

    // 2. Validate quantity.
    require(quantity > BigDecimal.ZERO) {
        "Quantity must be positive"
    }

    // 3. Validate LIMIT price.
    if (type == OrderType.LIMIT) {
        require(
            price != null && price > BigDecimal.ZERO
        ) {
            "Limit orders require a positive price"
        }
    }

    // 4. Parse BASE-QUOTE symbol.
    val (baseAsset, quoteAsset) = parseSymbol(symbol)

    /*
     * 5. Reserve funds before creating the order.
     *
     * BUY:
     *   reserve price × quantity of quote asset.
     *
     * SELL:
     *   reserve quantity of base asset.
     */
    if (type == OrderType.LIMIT) {

        when (side) {

            OrderSide.BUY -> {
                val requiredQuote =
                    price!!
                        .multiply(quantity)

                walletService.reserve(
                    accountId = accountId,
                    asset = quoteAsset,
                    amount = requiredQuote
                )
            }

            OrderSide.SELL -> {
                walletService.reserve(
                    accountId = accountId,
                    asset = baseAsset,
                    amount = quantity
                )
            }
        }
    }

    // 6. Create the order.
    val order = Order(
        accountId = accountId,
        symbol = symbol,
        side = side,
        type = type,
        price = price,
        quantity = quantity,
        remainingQuantity = quantity,
        idempotencyKey = idempotencyKey
    )

    // 7. Persist the order.
    val saved = orderRepository.save(order)

    log.info(
        "Order placed: id=${saved.id} " +
            "account=$accountId " +
            "$side $quantity $symbol @ $price"
    )

    /*
     * 8. Run the matching engine.
     *
     * This is intentionally done after saving the order so
     * the matching engine can find it by ID.
     */
    matchingEngine.match(saved.id)

    // 9. Reload the order because matching may have changed it.
    val result =
        orderRepository
            .findById(saved.id)
            .orElseThrow {
                IllegalStateException(
                    "Order ${saved.id} vanished immediately after matching"
                )
            }

    if (
        result.status == OrderStatus.FILLED ||
        result.status == OrderStatus.PARTIALLY_FILLED
    ) {
        log.info(
            "Order matched: id=${result.id} " +
                "status=${result.status} " +
                "remaining=${result.remainingQuantity}"
        )
    }

    return result
}

    fun cancelOrder(
        orderId: UUID,
        accountId: UUID
    ): Order {

        var attempt = 0

        while (true) {
            try {
                return self.cancelOrderInternal(
                    orderId = orderId,
                    accountId = accountId
                )
            } catch (e: Exception) {

                if (
                    e !is ObjectOptimisticLockingFailureException &&
                    e !is DataIntegrityViolationException
                ) {
                    throw e
                }

                attempt++

                if (attempt >= MAX_RETRIES) {
                    throw e
                }

                Thread.sleep(jitteredBackoff())
            }
        }
    }

    @Transactional
    fun cancelOrderInternal(
        orderId: UUID,
        accountId: UUID
    ): Order {

        val order =
            orderRepository
                .findById(orderId)
                .orElseThrow {
                    IllegalArgumentException(
                        "Order $orderId not found"
                    )
                }

        require(order.accountId == accountId) {
            "Order $orderId does not belong to account $accountId"
        }

        /*
         * Cancelling an already completed/cancelled order
         * is a no-op.
         */
        if (
            order.status == OrderStatus.CANCELLED ||
            order.status == OrderStatus.FILLED
        ) {
            return order
        }

        /*
         * No remaining quantity means the order is filled.
         */
        if (order.remainingQuantity <= BigDecimal.ZERO) {

            order.remainingQuantity = BigDecimal.ZERO
            order.status = OrderStatus.FILLED

            return orderRepository.save(order)
        }

        val (baseAsset, quoteAsset) =
            parseSymbol(order.symbol)

        /*
         * Release ONLY the funds corresponding to
         * the remaining quantity.
         */
        if (order.type == OrderType.LIMIT) {

            when (order.side) {

                OrderSide.BUY -> {

                    val remainingQuote =
                        order.price!!
                            .multiply(order.remainingQuantity)

                    walletService.release(
                        accountId = accountId,
                        asset = quoteAsset,
                        amount = remainingQuote
                    )
                }

                OrderSide.SELL -> {

                    walletService.release(
                        accountId = accountId,
                        asset = baseAsset,
                        amount = order.remainingQuantity
                    )
                }
            }
        }

        order.status =
            OrderStatus.CANCELLED

        val cancelled =
            orderRepository.save(order)

        log.info(
            "Order cancelled: id=${cancelled.id} " +
                "account=$accountId " +
                "remaining=${cancelled.remainingQuantity}"
        )

        broadcastService.broadcastOrderBook(
            cancelled.symbol
        )

        return cancelled
    }

    private fun parseSymbol(
        symbol: String
    ): Pair<String, String> {

        val parts =
            symbol.split("-")

        require(parts.size == 2) {
            "Symbol must be in BASE-QUOTE form, e.g. BTC-USD"
        }

        require(
            parts[0].isNotBlank() &&
                parts[1].isNotBlank()
        ) {
            "Symbol must contain both BASE and QUOTE assets"
        }

        return parts[0] to parts[1]
    }
}