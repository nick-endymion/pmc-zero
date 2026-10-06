package org.endy.pmczero.model

/**
 * One element a scan ran into, and the level of the worker that found it.
 *
 * @param element the element as the parser handed it over, i.e. the whole value it read rather than
 * anything derived from it
 * @param level the level of the [org.endy.pmczero.model.scraper.FoundElementsWorker] that found it,
 * which is the depth the caller asked for. Carried per element rather than read off the list, so a
 * worker of level 1 and one of level 3 can run over the same page and the results can still be told
 * apart afterwards
 */
data class FoundElement(
    val level: Int,
    val element: String
)
