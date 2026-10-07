package org.endy.pmczero.exception

/**
 * Thrown when a request cannot be made sense of, e.g. a parameter value that is not one of the
 * values the ressource knows. Mapped to 400 by [ExceptionAdvice].
 *
 * The one place this is thrown is a [waitForSelectorState][org.endy.pmczero.ressource.ScraperRessource]
 * that is not a playwright wait state. Answering that with the 409 of a [NotAccessibleException]
 * would be the wrong status twice over: the request is not in conflict with anything, and telling a
 * caller that something on the server cannot be used would send them off to look at the server for
 * a mistake that is in the request they just sent.
 */
class BadRequestException(message: String) : Throwable(message) {

}
