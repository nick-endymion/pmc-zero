package org.endy.pmczero.ressource

import org.endy.pmczero.model.FoldersEntity
import org.endy.pmczero.model.ScanConfiguration
import org.endy.pmczero.service.FileSystemScanner
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.regex.Pattern

@RestController
class FilesystemScannerRessource(val fss: FileSystemScanner) {

    @GetMapping("/scan-fs")
    fun getFolders(@RequestParam folder: String, @RequestParam maxDepth: Int): List<String> {
        var scanconfig = ScanConfiguration(Pattern.compile(".*"), 0, maxDepth, false, true)
        var result2 = fss.ls(folder, scanconfig)
        return result2
    }

    @GetMapping("/scan-fs2")
    fun getFolders2(@RequestParam folder: String, @RequestParam maxDepth: Int, @RequestParam level: Int): Map<String, List<String>> {
        var scanconfig = ScanConfiguration(Pattern.compile(".*"), 1, maxDepth, false, true)
        var result2 = fss.ls(folder, scanconfig)
        return fss.group(result2,level)
    }

}
