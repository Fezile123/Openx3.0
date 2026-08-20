package com.openex.core.api

import com.openex.core.repository.WalletRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

data class WalletResponse(
    val id: UUID,
    val accountId: UUID,
    val asset: String,
    val balance: BigDecimal,
    val reserved: BigDecimal,
    val available: BigDecimal
)

@RestController
@RequestMapping("/wallets")
class WalletController(
    private val walletRepository: WalletRepository
) {

    @GetMapping
    fun getWallets(
        @RequestParam accountId: UUID
    ): ResponseEntity<List<WalletResponse>> {

        val wallets = walletRepository
            .findByAccountId(accountId)
            .map { wallet ->

                WalletResponse(
                    id = wallet.id,
                    accountId = wallet.accountId,
                    asset = wallet.asset,
                    balance = wallet.balance,
                    reserved = wallet.reserved,
                    available = wallet.balance.subtract(wallet.reserved)
                )
            }

        return ResponseEntity.ok(wallets)
    }
}