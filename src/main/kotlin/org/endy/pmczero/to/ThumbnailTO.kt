package org.endy.pmczero.to

/**
 * What [org.endy.pmczero.service.ThumbnailService.createThumbnail] did for one medium.
 *
 * [created] rather than a bare success, because the call is a no-op when a thumbnail file is already
 * there. A caller that asked for a thumbnail and got one back cannot tell whether it was generated
 * by this call or found on disk, and the two cases read very differently to someone trying to work
 * out why a thumbnail looks stale.
 *
 * [name] is the bessource name, relative to the TN_FS location, and [url] the same path as an
 * absolute url, so a caller can show the thumbnail without having to resolve the location itself.
 */
data class ThumbnailTO(
    val mediumId: Int,
    val name: String,
    val url: String,
    val width: Int,
    val height: Int,
    val created: Boolean
)