package org.endy.pmczero.mapper

import org.endy.pmczero.model.FolderTO
import org.endy.pmczero.model.FoldersEntity
import org.endy.pmczero.model.MfileTO
import org.endy.pmczero.model.MfilesEntity

//fun FoldersEntity.map(f: FolderTO) = FoldersEntity(id, storageId,mpath,lfolder,null,title)
fun FolderTO.map(): FoldersEntity {return FoldersEntity(id, storageId,mpath,lfolder,mfiles.map{mfile -> mfile.map()},title)}
fun MfileTO.map(): MfilesEntity {return MfilesEntity()}