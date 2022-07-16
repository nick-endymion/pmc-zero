package org.endy.pmczero.mapper

import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.modern.*
import org.endy.pmczero.to.*


fun Mset.toTO(): MsetTO {
    return MsetTO(
        id = id,
        name = name,
        created_at = created_at,
        updated_at = updated_at
    )
}

fun Mset.toTOwithMedia(): MsetTO {
    return MsetTO(
        id = id,
        name = name,
        created_at = created_at,
        updated_at = updated_at,
        media = media.map { it.toTO() })
}

fun MsetTO.toEntity(): Mset {
    return Mset().also {
        it.id = id
        it.name = name
    }
}


fun Medium.toTO(): MediumTO {
    return MediumTO(
        id = id,
        name = name,
        created_at = created_at,
        updated_at = updated_at,
        bessources = arrayListOf(),
//        bessources = bessources.map { it.toTO() }
    )
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
        if (mediumId != null)
            it.medium = Medium().also { it.id = mediumId; it.name = name; it.mtype = Mtype.BOOKMARK.i }
        println("mediumId " + mediumId)
        println("name " + name)
        println("url " + url)
        println("id " + id)
    }
}

fun SerializedScannerTO.toEntity(): SerializedScanner {
    return SerializedScanner().also {
        it.id = id
        it.name = name
        it.example = example
        it.regex = regex
        it.serialization = serialization
    }
}
fun SerializedScanner.toTO(): SerializedScannerTO {
    return SerializedScannerTO(
        id = id,
        name = name,
        regex = regex,
        example = example,
        serialization = serialization
    )
}


