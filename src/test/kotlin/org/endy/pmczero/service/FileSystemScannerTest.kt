package org.endy.pmczero.service

import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.ScanConfiguration
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.MsetRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.util.regex.Pattern

class FileSystemScannerTest {

    private val mediaRepository: MediaRepository = mock()
    private val msetRepository: MsetRepository = mock()
    private val scanner = FileSystemScanner(mediaRepository, msetRepository)

    @Test
    fun `ls returns files matching pattern within depth range`() {
        val root = Files.createTempDirectory("fss-ls-files")
        val rootFile = root.resolve("root.pdf")
        val nestedDir = root.resolve("nested")
        val nestedFile = nestedDir.resolve("doc.pdf")
        val txtFile = nestedDir.resolve("skip.txt")

        Files.createDirectories(nestedDir)
        Files.createFile(rootFile)
        Files.createFile(nestedFile)
        Files.createFile(txtFile)

        val config = ScanConfiguration(
            pattern = Pattern.compile(".*\\.pdf"),
            minDepth = 1,
            maxDepth = 2,
            includeDir = false,
            includeFile = true
        )

        val result = scanner.ls(root.toString(), config).toSet()

        assertEquals(setOf("/root.pdf", "/nested/doc.pdf"), normalize(result))
    }

    @Test
    fun `ls returns only directories when includeDir is true and includeFile is false`() {
        val root = Files.createTempDirectory("fss-ls-dirs")
        val nestedDir = root.resolve("nested")
        val childDir = nestedDir.resolve("child")
        val file = nestedDir.resolve("doc.pdf")

        Files.createDirectories(childDir)
        Files.createFile(file)

        val config = ScanConfiguration(
            pattern = Pattern.compile(".*"),
            minDepth = 1,
            maxDepth = 2,
            includeDir = true,
            includeFile = false
        )

        val result = scanner.ls(root.toString(), config).toSet()

        assertEquals(setOf("/nested", "/nested/child"), normalize(result))
    }

    @Test
    fun `ls respects maxDepth and excludes deeper entries`() {
        val root = Files.createTempDirectory("fss-ls-depth")
        val level1 = root.resolve("a")
        val level2 = level1.resolve("b")
        val level3 = level2.resolve("deep.pdf")

        Files.createDirectories(level2)
        Files.createFile(level3)

        val config = ScanConfiguration(
            pattern = Pattern.compile(".*\\.pdf"),
            minDepth = 1,
            maxDepth = 2,
            includeDir = false,
            includeFile = true
        )

        val result = scanner.ls(root.toString(), config)

        assertEquals(emptySet<String>(), normalize(result.toSet()))
    }

    @Test
    fun `scanAndCreateMedia creates media and reuses existing mset`() {
        val root = Files.createTempDirectory("fss-scan-media-existing")
        Files.createFile(root.resolve("a.pdf"))
        Files.createFile(root.resolve("b.pdf"))

        val config = ScanConfiguration(
            pattern = Pattern.compile(".*\\.pdf"),
            minDepth = 1,
            maxDepth = 1,
            includeDir = false,
            includeFile = true
        )

        val existingMset = Mset().apply { name = "MySet" }
        whenever(msetRepository.findAllByNameContaining("MySet")).thenReturn(listOf(existingMset))
        whenever(mediaRepository.save(any())).doAnswer { it.arguments[0] as Medium }

        val result = scanner.scanAndCreateMedia(root.toString(), config)

        assertEquals(2, result.size)
        verify(msetRepository, never()).save(any())
        verify(mediaRepository, times(2)).save(any())
        result.forEach { assertEquals("MySet", it.mset?.name) }
    }

    @Test
    fun `scanAndCreateMedia creates mset when not existing`() {
        val root = Files.createTempDirectory("fss-scan-media-new")
        Files.createFile(root.resolve("a.pdf"))

        val config = ScanConfiguration(
            pattern = Pattern.compile(".*\\.pdf"),
            minDepth = 1,
            maxDepth = 1,
            includeDir = false,
            includeFile = true
        )

        whenever(msetRepository.findAllByNameContaining("NewSet")).thenReturn(emptyList())
        whenever(msetRepository.save(any())).thenAnswer { it.arguments[0] as Mset }
        whenever(mediaRepository.save(any())).thenAnswer { it.arguments[0] as Medium }

        val result = scanner.scanAndCreateMedia(root.toString(), config)

        assertEquals(1, result.size)
        verify(msetRepository, times(1)).save(any())
        assertEquals("NewSet", result.first().mset?.name)
    }

    @Test
    fun `scanAndCreateMedia without msetName creates media without mset`() {
        val root = Files.createTempDirectory("fss-scan-media-nomset")
        Files.createFile(root.resolve("single.pdf"))

        val config = ScanConfiguration(
            pattern = Pattern.compile(".*\\.pdf"),
            minDepth = 1,
            maxDepth = 1,
            includeDir = false,
            includeFile = true
        )

        whenever(mediaRepository.save(any())).thenAnswer { it.arguments[0] as Medium }

        val result = scanner.scanAndCreateMedia(root.toString(), config)

        assertEquals(1, result.size)
        assertEquals(null, result.first().mset)
        verify(msetRepository, never()).findAllByNameContaining(any())
        verify(msetRepository, never()).save(any())
    }

    private fun normalize(paths: Set<String>): Set<String> =
        paths.map { it.replace('\\', '/') }.toSet()
}
