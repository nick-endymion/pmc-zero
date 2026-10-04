package org.endy.pmczero.exception

import org.endy.pmczero.to.ErrorTO
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Maps the exceptions thrown by the services to http status codes, so a failing call answers a
 * meaningful status instead of the generic 500 the container renders for an unhandled exception.
 *
 * Only project exceptions are handled here. Anything else falls through to the default error
 * handling of spring boot, which keeps the statuses of the framework's own exceptions (405 on a
 * wrong method, 415 on a wrong content type, ...) intact.
 */
@RestControllerAdvice
class ExceptionAdvice {

    /**
     * Thrown whenever an entity a service works on does not exist, so it maps to 404.
     */
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ResponseEntity<ErrorTO> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorTO(e.message ?: "not found"))

    /**
     * Thrown when something exists but cannot be used, e.g. a location whose file system path is
     * not accessible, so it maps to 409.
     */
    @ExceptionHandler(NotAccessibleException::class)
    fun handleNotAccessible(e: NotAccessibleException): ResponseEntity<ErrorTO> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorTO(e.message ?: "not accessible"))
}