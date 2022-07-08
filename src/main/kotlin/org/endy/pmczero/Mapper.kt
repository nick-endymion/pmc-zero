package org.endy.pmczero.mapper

import org.endy.pmczero.model.modern.*
import org.endy.pmczero.to.*


fun BSet.toTO(): BsetTO {
    return BsetTO(
        id = id,
        name = name,
        created_at = created_at,
        updated_at = updated_at,
        media = media.map { it.toTO() })
}

fun Medium.toTO(): MediumTO {
    return MediumTO(
        id = id,
        name = name,
        created_at = created_at,
        updated_at = updated_at,
        bessources = bessources.map { it.toTO() })
}

fun Bessource.toTO(): BessourceTO {
    return BessourceTO(
        id = id,
        name = name,
        btype = ressType,
        mediumId = medium?.id,
        storageId = storage.id,
        encrypted = encrypted,
        created_at = created_at,
        updated_at = updated_at,
    )
}

fun Bookmark.toTO(): BookmarkTO {
    return BookmarkTO(
        id = id,
        name = name,
        url = url,
        mediumId = medium?.id,
        created_at = created_at,
        updated_at = updated_at,
    )
}

fun BookmarkTO.toEntity(): Bookmark {
    return Bookmark().also {
        it.id = id
        it.name = name
        it.url = url
    }
}
