package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
class Downloader : Fetcher {

    val map: MutableMap<String, String> = HashMap()

    companion object Default : Downloader()

    override fun getAsString(urlString: String, withProxy: Boolean): String {

        if (map[urlString] != null) {
            return map[urlString]!!
        }

        println(urlString)
        val con = connection(urlString, withProxy)
        val inputStream = con.inputStream
        val content = inputStream.bufferedReader().use(BufferedReader::readText)
        map [urlString] = content
        return content
    }

    override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {

        Files.createDirectories(Paths.get(directory))
        val filename = getFilename(urlString)
        downloadTo(urlString, File(directory, filename), withProxy)
    }

    override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {

        createParentDirectory(target)
        val con = connection(urlString, withProxy)
        con.inputStream.use { input ->
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    /**
     * The directory [target] lives in, created when it is not there yet.
     *
     * `mkdirs` returning false is not on its own a failure: it also returns false when the directory
     * turned out to exist already, so the `isDirectory` check is what tells the two apart.
     */
    private fun createParentDirectory(target: File) {
        val parent = target.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory)
            throw NotAccessibleException("could not create the directory ${parent.path}")
    }

    /**
     * A GET connection to [urlString], over the socks proxy when asked for.
     *
     * One place for both [getAsString] and [downloadTo], so the proxy settings cannot drift apart
     * between reading a page and fetching one of its images.
     */
    private fun connection(urlString: String, withProxy: Boolean): HttpURLConnection {
        val url = URL(urlString)
        val con: HttpURLConnection =
            if (withProxy) {
                val proxyHost = "127.0.0.1"
                val proxyPort = 8090
                val proxyAddr = InetSocketAddress(proxyHost, proxyPort)
                val proxy = Proxy(Proxy.Type.SOCKS, proxyAddr)
                url.openConnection(proxy) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }
        con.setRequestMethod("GET")
        return con
    }

    private fun getFilename(url: String): String {
        val f = url.split("/")
        val fn = f[f.size - 1].split("?")[0]
        return fn
    }

}
