package org.endy.pmczero.to

/**
 * Error payload written by the global exception advice, so a failed call gets a json body instead
 * of the container's error page.
 */
data class ErrorTO(val error: String)