package org.endy.pmczero.mapper

import org.endy.pmczero.model.FolderTO
import org.endy.pmczero.model.FoldersEntity
import org.endy.pmczero.model.MfileTO
import org.endy.pmczero.model.MfilesEntity

//fun FoldersEntity.map(f: FolderTO) = FoldersEntity(id, storageId,mpath,lfolder,null,title)
fun FolderTO.map(): FoldersEntity {
    return FoldersEntity(id, storage , mpath, lfolder, mfiles.map { mfile -> mfile.map() }, title)
}

fun FoldersEntity.toTO(includeMfiles: Boolean = false): FolderTO {
    return FolderTO(
        id = id,
        storage = storage,
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
    return MfileTO(filename = filename, id = id, folder =  folder, modDate = modDate, mtype =  mtype)
}
