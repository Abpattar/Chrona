package com.chrona.app.platform.cloud

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one seam through which the cloud provider performs HTTP.
 *
 * A tiny interface (rather than a networking library) keeps the app free of a new dependency and
 * lets the provider be tested without a server: tests supply a fake transport and never touch the
 * network.
 */
data class HttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val timeoutMillis: Long,
)

data class HttpResponse(
    val status: Int,
    val body: String,
)

interface HttpTransport {
    suspend fun post(request: HttpRequest): HttpResponse
}

/** Production transport. `HttpURLConnection` is part of the platform, so nothing is added. */
class UrlConnectionHttpTransport(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HttpTransport {

    override suspend fun post(request: HttpRequest): HttpResponse = withContext(io) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = request.timeoutMillis.toInt()
            readTimeout = request.timeoutMillis.toInt()
            doOutput = true
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
