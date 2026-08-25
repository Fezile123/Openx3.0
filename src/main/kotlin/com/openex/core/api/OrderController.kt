package com.openex.core.api

import com.openex.core.domain.Order
import com.openex.core.domain.OrderSide
import com.openex.core.domain.OrderType
import com.openex.core.repository.OrderRepository
import com.openex.core.service.OrderService
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class PlaceOrderRequest(
    @field:NotNull val symbol: String,
    @field:NotNull val side: OrderSide,
    @field:NotNull val type: OrderType,
    val price: BigDecimal? = null,
    @field:NotNull @field:Positive val quantity: BigDecimal
)

data class OrderResponse(
    val id: UUID,
    val accountId: UUID,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val price: BigDecimal?,
    val quantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val status: String
) {
    companion object {
        fun from(order: Order) = OrderResponse(
            id = order.id,
            accountId = order.accountId,
            symbol = order.symbol,
            side = order.side,
            type = order.type,
            price = order.price,
            quantity = order.quantity,
            remainingQuantity = order.remainingQuantity,
            status = order.status.name
        )
    }
}

@RestController
@RequestMapping("/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository
) {

    @PostMapping
    fun placeOrder(
        @RequestBody request: PlaceOrderRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        authentication: Authentication
    ): ResponseEntity<OrderResponse> {

        val accountId = authentication.principal as UUID

        val order = orderService.placeOrder(
            accountId = accountId,
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            price = request.price,
            quantity = request.quantity,
            idempotencyKey = idempotencyKey
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(OrderResponse.from(order))
    }

    @DeleteMapping("/{orderId}")
    fun cancelOrder(
        @PathVariable orderId: UUID,
        authentication: Authentication
    ): ResponseEntity<OrderResponse> {

        val accountId = authentication.principal as UUID

        val order = orderService.cancelOrder(
            orderId,
            accountId
        )

        return ResponseEntity.ok(
            OrderResponse.from(order)
        )
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @PathVariable orderId: UUID,
        authentication: Authentication
    ): ResponseEntity<OrderResponse> {

        val accountId = authentication.principal as UUID

        val order = orderRepository
            .findById(orderId)
            .orElseThrow {
                NoSuchElementException(
                    "Order $orderId not found"
                )
            }

        if (order.accountId != accountId) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(
            OrderResponse.from(order)
        )
    }

    @GetMapping
    fun listOrders(
        authentication: Authentication
    ): ResponseEntity<List<OrderResponse>> {

        val accountId = authentication.principal as UUID

        val orders =
            orderRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId)

        return ResponseEntity.ok(
            orders.map { OrderResponse.from(it) }
        )
    }
}
