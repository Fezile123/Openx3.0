package com.openex.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class EntryType { DEBIT, CREDIT }

@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val accountId: UUID,

    @Column(nullable = false)
    val asset: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val entryType: EntryType,

    @Column(nullable = false, precision = 20, scale = 8)
    val amount: BigDecimal,

    @Column(nullable = false)
    val referenceId: UUID,

    @Column(nullable = false)
    val referenceType: String,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
)
