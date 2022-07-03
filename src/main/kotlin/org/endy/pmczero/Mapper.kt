package org.endy.pmczero.mapper

import org.endy.pmczero.model.*
import org.endy.pmczero.model.legacy.MfilesEntity
import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.to.*
import java.sql.Date


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
        btype = btype,
        mediumId = medium?.id,
        storageId = storage.id,
        encrypted = encrypted,
        created_at = created_at,
        updated_at = updated_at,
    )
}
