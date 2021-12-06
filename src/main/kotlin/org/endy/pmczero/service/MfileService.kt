package org.endy.pmczero.service

import org.endy.pmczero.repository.MfilesRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class MfileService(private val mfilesRepository: MfilesRepository) {

    fun findById(id: Int) {
        mfilesRepository.findByIdOrNull(id)
    }

}