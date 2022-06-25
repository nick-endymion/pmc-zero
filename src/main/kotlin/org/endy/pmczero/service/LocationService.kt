package org.endy.pmczero.service

import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.legacy.MfilesEntity
import org.endy.pmczero.repository.MfilesRepository
import org.springframework.stereotype.Service

@Service
class LocationService(private val mfilesRepository: MfilesRepository) {

    fun url(mfile: MfilesEntity, location: Location) : String {

        val folder = mfile.folder ?: throw Exception()
        return location.uri + "/" + folder.lfolder+ "/"+folder.mpath+"/"+mfile.filename

    }

}
