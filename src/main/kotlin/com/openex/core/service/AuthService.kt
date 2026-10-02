package com.openex.core.service

import com.openex.core.repository.AccountRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun authenticate(
        email: String,
        password: String
    ): Boolean {

        val account = accountRepository.findByEmail(email)
            ?: return false

        return passwordEncoder.matches(
            password,
            account.passwordHash
        )
    }
}
