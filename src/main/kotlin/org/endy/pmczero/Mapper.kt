package org.endy.pmczero.mapper

import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.modern.*
import org.endy.pmczero.to.*


fun Mset.toTO(): MsetTO {
    return MsetTO(
        id = id,
        name = name,
        locationId = locationId,
        subpath = subpath,
        url = url,
        created_at = created_at,
        updated_at = updated_at
    )
}

fun Mset.toTOwithMedia(withBessources: Boolean = false): MsetTO {
    return MsetTO(
        id = id,
        name = name,
        locationId = locationId,
        subpath = subpath,
        url = url,
        created_at = created_at,
        updated_at = updated_at,
        media = media.map { it.toTO(withBessources) })
}

fun MsetTO.toEntity(): Mset {
    return Mset().also { mset ->
        mset.id = id
        mset.name = name
        mset.locationId = locationId
        mset.subpath = subpath
        mset.url = url
        if (media != null)
            mset.media = media.map { it.toEntity(mset) }.toMutableList()
    }
}


fun Medium.toTO(withBessources: Boolean = true, withMset: Boolean = false): MediumTO {
    return MediumTO(
        id = id,
        name = name,
        created_at = created_at,
        updated_at = updated_at,
        mtype = mtype,
        deleted = deleted,
        bessources = bessources.map { it.toTO() },
        mset = if (withMset) {
            mset?.toTO()
        } else {
            null
        }
    )
}

fun MediumTO.toEntity(mset: Mset? = null): Medium {
    return Medium().also {
        it.id = id
        it.name = name
        it.mtype = mtype
        it.mset = mset
    }
}

fun Bessource.toTO(withImage: Boolean = false): BessourceTO {
    return BessourceTO(
        id = id,
        name = name,
        ressType = ressType,
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
        updated_at = updated_at
    )
}

fun Bookmark.toFatTO(): BookmarkTO {
    return this.toTO().also { it.medium = medium?.toTO(withMset = true) }
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

fun ScannerTO.toEntity(): Scanner {
    return Scanner().also {
        it.id = id
        it.name = name
        it.example = example
        it.regex = regex
        it.serialization = serialization
        it.valid = valid
    }
}

fun Scanner.toTO(): ScannerTO {
    return ScannerTO(
        id = id,
        name = name,
        regex = regex,
        example = example,
        serialization = serialization,
        valid = valid
    )
}

fun ScannerShort.toTO(): ScannerShortTO {
    return ScannerShortTO(
        id = getId(),
        name = getName(),
        regex = getRegex()
    )
}

fun Storage.toTO(withLocations: Boolean = false): StorageTO {
    return StorageTO(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        locations = if (withLocations) locations.map { it.toTO() } else listOf()
    )
}

fun StorageTO.toEntity(): Storage {
    return Storage().also {
        it.id = id
        it.name = name
        it.createdAt = createdAt
        it.updatedAt = updatedAt
    }
}

fun Location.toTO(withStorage: Boolean = true, withStorageLocations: Boolean = false): LocationTO {
    return LocationTO(
        id = id,
        name = name,
        uri = uri,
        description = description,
        locationType = locationType,
        storageTO = if (withStorage) storageOrNull()?.toTO(withStorageLocations) ?: StorageTO() else StorageTO(),
        inuse = inuse,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mfileId = mfileId,
        origin = origin,
        extension = extension
    )
}


fun LocationTO.toEntity(): Location {
    return Location().also {
        it.id = id
        it.name = name
        it.uri = uri
        it.description = description
        it.locationType = locationType
        it.inuse = inuse
        it.mfileId = mfileId
        it.origin = origin
        it.extension = extension
        it.storage = storageTO.toEntity()
    }
}
//fun Medium.toTO(): MediumTO {
//    return MediumTO()
//}
