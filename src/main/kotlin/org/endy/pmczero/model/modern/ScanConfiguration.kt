package org.endy.pmczero.model.modern

import java.util.regex.Pattern

class ScanConfiguration(
    val pattern: Pattern = Pattern.compile(".*\\.pdf"),
    val minDepth: Int = 32,
    val maxDepth: Int = 32,
    val includeDir: Boolean = false,
    val includeFile: Boolean = true
) {

}
