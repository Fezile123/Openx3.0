package com.openex.core.api

import com.openex.core.repository.AccountRepository
import com.openex.core.service.AuthService
import com.openex.core.service.JwtService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val authenticated: Boolean,
    val token: String
)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val accountRepository: AccountRepository,
    private val jwtService: JwtService
) {

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest
    ): LoginResponse {

        val authenticated = authService.authenticate(
            request.email,
            request.password
        )

        if (!authenticated) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
            )
        }

        val account = accountRepository.findByEmail(request.email)
            ?: throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
            )

        val token = jwtService.generateToken(
            account.id,
            account.email
        )

        return LoginResponse(
            authenticated = true,
            token = token
        )
    }
}