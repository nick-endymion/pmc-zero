package org.endy.pmczero.service

import org.endy.pmczero.model.LocationsEntity
import org.springframework.stereotype.Service

@Service
class MediaCreator(val fileSytemScanner: FileSytemScanner) {

    fun av(location: LocationsEntity, folder: String) {
        val path = location.withFolderPath(folder)
//        fileSytemScanner.ls(path)
    }


    fun createMedia(files: List<String>) {

    }
}
