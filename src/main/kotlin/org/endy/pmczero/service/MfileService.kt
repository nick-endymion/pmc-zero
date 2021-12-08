package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RType
import org.endy.pmczero.repository.MfilesRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class MfileService(private val mfilesRepository: MfilesRepository) {

    fun findById(id: Int) {
        mfilesRepository.findByIdOrNull(id)
    }

    fun url(id: Int, type: RType) {

        var mfile = mfilesRepository.findByIdOrNull(id) ?: throw NotFoundException()

//        mfile.folder.storage
    }

    fun file(id: Int, type: RType) {

    }
}
