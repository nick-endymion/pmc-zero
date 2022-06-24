package org.endy.pmczero.service

import org.endy.pmczero.model.ScanConfiguration
import org.springframework.stereotype.Service
import java.io.File

@Service
class FileSystemScanner {

    fun scan(folder: String, scanconfig: ScanConfiguration) {
        ls(folder, scanconfig)
    }

    fun ls(folder: String, scanconfig: ScanConfiguration): List<String> {
        val length = folder.length
        val fileTreeWalk = File(folder).walk()
        val x = fileTreeWalk.filter {
            (it.isDirectory == scanconfig.includeDir ||
                    it.isFile == scanconfig.includeFile) &&
                    scanconfig.pattern.matcher(it.name).matches()
        }
            .map { it.absolutePath.drop(length) }
            .associateWith { it.toCharArray().filter { it.equals('\\') || it.equals('/') }.size }
            .filter {
                it.value <= scanconfig.maxDepth && it.value >= scanconfig.minDepth
            }
            .map { it.key }
            .toList()
        return x
    }


    //    fun group(fileList: List<String>): List<Pair<String, String>> {
//      return fileList.associateWith { determineFolder(it) }.toList()
//    }
    fun group(fileList: List<String>, level: Int): Map<String, List<String>> {
        return fileList.associateWith { determineFolder(it, level) }.toList().groupBy { it.second }
            .mapValues { it.value.map {it.first} }
    }

    fun determineFolder(fileName: String, level: Int): String {
        if (level == 0)
            return ""
        val separator = "\\" + File.separator
        val folder = separator + "[^" + separator + "]+"
        var path = ""
        for (i in 1..level)
            path += folder
        val regex = Regex(path + separator)
        val x = regex.find(fileName)
        if (x == null)
            return determineFolder(fileName)
        return x.value.toString().dropLast(1)
    }

    fun determineFolder(fileName: String): String {
        val separator = "\\" + File.separator
        val name = separator + "[^" + separator + "]+$"
        val regex = Regex(name)
        val x = regex.find(fileName)
        if (x == null)
            throw Exception()
        return fileName.dropLast(x.value.toString().length)
    }

}
