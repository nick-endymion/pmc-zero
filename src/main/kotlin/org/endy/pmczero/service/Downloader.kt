package org.endy.pmczero.service

import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
class Downloader {

    val map: MutableMap<String, String> = HashMap()

    companion object Default : Downloader()

    fun getAsString(urlString: String, withProxy: Boolean = true): String {

        if (map[urlString] != null) {
            return map[urlString]!!
        }

        val url = URL(urlString)
        println(url)
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
        val inputStream = con.inputStream
        val content = inputStream.bufferedReader().use(BufferedReader::readText)
        map [urlString] = content
        return content
    }

    fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean = false) {

        Files.createDirectories(Paths.get(directory))
        val filename = getFilename(urlString)
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
        val inputStream = con.inputStream
        inputStream.use { input ->
            Files.copy(input, Paths.get(directory + "/" + filename), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun getFilename(url: String): String {
        val f = url.split("/")
        val fn = f[f.size - 1].split("?")[0]
        return fn
    }

}
