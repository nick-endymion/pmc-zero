package org.endy.pmczero.model.scraper

import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.service.SetService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest


//Attention:
//Run only with H2
@SpringBootTest
class persistenceTests {


    @Autowired
    lateinit var setService: SetService

    @Test
    fun test1() {

        val set = BSet().also {it.name = "test name"}
        setService.save(bset = set)
        println(set.id)
        println(set.created_at)

    }

    @Test
    fun test2() {

        val set = BSet().also {it.name = "test name"}
        val medium = Medium().also {it.name = "test medium"}
        set.media = mutableListOf(medium)
        val bessource1 = Bessource().also {it.name = "test bessource 1"}
        val bessource2 = Bessource().also {it.name = "test bessource 2"}
        medium.bessources = mutableListOf(bessource1, bessource2)
        setService.save(bset = set)
        println(set.id)
        println(set.name)
        println(medium.id)
        println(medium.name)
        println(bessource1.id)
        println(bessource1.name)
        println(bessource2.id)
        println(bessource2.name)

    }

}
