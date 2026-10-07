package org.endy.pmczero.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * Unit tests for [ExceptionAdvice]. The advice is a plain class, so it is called directly instead of
 * going through a spring context.
 */
class ExceptionAdviceTest {

    private val advice = ExceptionAdvice()

    @Test
    fun `not found answers 404`() {
        val response = advice.handleNotFound(NotFoundException())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `not found answers a json error body`() {
        val response = advice.handleNotFound(NotFoundException())

        assertEquals("not found", response.body!!.error)
    }

    @Test
    fun `not accessible answers 409`() {
        val response = advice.handleNotAccessible(NotAccessibleException("location 1 is not accessible"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("location 1 is not accessible", response.body!!.error)
    }

    @Test
    fun `bad request answers 400`() {
        val response = advice.handleBadRequest(BadRequestException("waitForSelectorState=x is not a wait state"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("waitForSelectorState=x is not a wait state", response.body!!.error)
    }
}