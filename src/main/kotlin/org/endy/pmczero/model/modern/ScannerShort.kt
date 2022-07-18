package org.endy.pmczero.model.modern

import java.sql.Date
import javax.persistence.*

interface ScannerShort {

    fun getId(): Int
    fun getName(): String
    fun getRegex(): String
}

