package com.sokind.chat.support.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(exception: ResponseStatusException): ResponseEntity<ApiError> {
        val status = HttpStatus.valueOf(exception.statusCode.value())
        return ResponseEntity.status(status)
            .body(ApiError.of(status.value(), status.reasonPhrase, exception.reason))
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        MissingRequestHeaderException::class,
        MethodArgumentTypeMismatchException::class,
        IllegalArgumentException::class,
    )
    fun handleBadRequest(exception: Exception): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(400, "Bad Request", exception.message))

    @ExceptionHandler(Exception::class)
    fun handleAny(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        log.error("unhandled exception on {} {}", request.method, request.requestURI, exception)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of(500, "Internal Server Error", exception.message))
    }
}
