package org.endy.pmczero.service

import org.endy.pmczero.model.modern.Location
import org.springframework.stereotype.Service

@Service
class MediaCreator(val fileSystemScanner: FileSystemScanner) {

    // Big todo

    fun av(location: Location, folder: String) {
        val path = location.withFolderPath(folder)
//        fileSytemScanner.ls(path)
    }


    fun createMedia(files: List<String>) {

    }
}
