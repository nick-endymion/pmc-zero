package org.endy.pmczero.mapper

import org.endy.pmczero.model.*
import org.endy.pmczero.to.FolderTO
import org.endy.pmczero.to.MfileTO
import org.endy.pmczero.to.StorageTO

//fun FoldersEntity.map(f: FolderTO) = FoldersEntity(id, storageId,mpath,lfolder,null,title)
fun FolderTO.map(): FoldersEntity {
    return FoldersEntity(id, null , mpath, lfolder, mfiles.map { mfile -> mfile.map() }, title)
}

fun FoldersEntity.toTO(includeMfiles: Boolean = false): FolderTO {
    return FolderTO(
        id = id,
        storage = storage?.toTO(),
        mpath = mpath,
        lfolder = lfolder,
        title = title,
        mfiles = if (includeMfiles) mfiles.map { m -> m.toTO() } else listOf()
    )

}

fun MfileTO.map(): MfilesEntity {
    return MfilesEntity()
}

fun MfilesEntity.toTO(): MfileTO {
    return MfileTO(filename = filename, id = id, folder =  FoldersEntity(), modDate = modDate, mtype =  mtype)
}

fun StoragesEntity.toTO() : StorageTO {
    return StorageTO(id = id, name = name)
}
