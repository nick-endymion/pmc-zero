package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.StorageRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class StorageService(private val storageRepository: StorageRepository) {

    fun findById(id: Int): Storage {
        return storageRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun save(storage: Storage): Storage {
        return storageRepository.save(storage)
    }

    fun delete(id: Int) {
        storageRepository.delete(findById(id))
    }


}
