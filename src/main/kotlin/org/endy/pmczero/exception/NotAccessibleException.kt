package org.endy.pmczero.exception

/**
 * Thrown when something exists but cannot be used, e.g. a location whose file system path is
 * missing or not accessible. Mapped to 409 by [ExceptionAdvice], so it stays distinguishable from
 * a [NotFoundException], which maps to 404.
 */
class NotAccessibleException(message: String) : Throwable(message) {

}