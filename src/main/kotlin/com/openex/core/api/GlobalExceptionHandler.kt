package com.openex.core.api

import com.openex.core.service.InsufficientFundsException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class ErrorResponse(
    val error: String,
    val details: List<String> = emptyList()
)

/**
 * Centralized error handling for every REST controller in the app.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(
        ex: InsufficientFundsException
    ): ResponseEntity<ErrorResponse> {
        log.info("Rejected request: insufficient funds — ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(
                ErrorResponse(
                    error = ex.message ?: "Insufficient funds"
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(
        ex: IllegalArgumentException
    ): ResponseEntity<ErrorResponse> {
        log.info("Rejected request: bad input — ${ex.message}")

        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    error = ex.message ?: "Invalid request"
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException
    ): ResponseEntity<ErrorResponse> {

        val details = ex.bindingResult.fieldErrors.map {
            "${it.field}: ${it.defaultMessage}"
        }

        log.info("Rejected request: validation failed — $details")

        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    error = "Validation failed",
                    details = details
                )
            )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(
        ex: NoSuchElementException
    ): ResponseEntity<ErrorResponse> {

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    error = ex.message ?: "Resource not found"
                )
            )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException
    ): ResponseEntity<ErrorResponse> {

        return ResponseEntity
            .status(ex.statusCode)
            .body(
                ErrorResponse(
                    error = ex.reason ?: "Request failed"
                )
            )
    }

    /**
     * Catch-all for unexpected exceptions.
     * Full details are logged server-side but not exposed to clients.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception
    ): ResponseEntity<ErrorResponse> {

        log.error("Unexpected error handling request", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    error = "An unexpected error occurred"
                )
            )
    }
}
