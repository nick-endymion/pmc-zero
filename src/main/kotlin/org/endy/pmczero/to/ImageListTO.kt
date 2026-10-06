package org.endy.pmczero.to

/**
 * What a run of [org.endy.pmczero.model.scraper.FoundElementsWorker] over one page collected.
 *
 * The counterpart of [ImageImportTO] for a run that stores nothing: the same page, looked at with the
 * same parser, answered as the urls it offers rather than as files written and media created.
 *
 * Nothing is written and nothing is stored, so this costs a render of the page and the parsing of its
 * dom, and no downloads at all. That is what makes it the call to make before an import: a gallery of
 * two hundred images whose urls are all that is wanted is answered by one render rather than by two
 * hundred fetches.
 */
data class ImageListTO(
    /** the page the elements were collected from */
    val url: String,

    /** how many elements the collector ran into */
    val found: Int = 0,

    /**
     * The elements in the order the page offered them, each with the
     * [org.endy.pmczero.model.FoundElement.level] of the worker that found it.
     *
     * Absolute, since the parsers of this application read an `abs:` attribute: what is answered is the
     * url a browser would follow rather than the attribute as the page wrote it.
     */
    val elements: List<FoundElementTO> = emptyList(),

    /**
     * The pages that could not be read, with the reason each one did.
     *
     * Only ever filled by a run that follows links, i.e.
     * [org.endy.pmczero.ressource.ScraperRessource.listScraperImagesLevel2], where a link that answers
     * an error or times out would otherwise be indistinguishable from a link to a page that holds no
     * images at all. Both read as a link in [elements] with nothing under it, and only this says which
     * of the two happened.
     *
     * Empty for a listing of a single page, which reads one page and so has nothing that can fail after
     * the render itself.
     */
    val failures: List<ImageImportFailureTO> = emptyList()
)

/**
 * One collected element and the level of the worker that found it.
 *
 * The level is carried per element rather than read off the list, so a collector of level 1 and one of
 * level 3 can run over the same page in one scraper and the two stay tellable apart.
 */
data class FoundElementTO(
    val level: Int,
    val element: String
)
