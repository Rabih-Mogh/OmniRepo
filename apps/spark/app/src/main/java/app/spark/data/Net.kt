package app.spark.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The few requests the app makes for its reels: a page fetched, or a question posted. */
internal object Net {

    /** What came back: the HTTP status and the body, read whatever the status. */
    class Reply(val status: Int, val body: String)

    /** The text at [url]. Anything but a plain success is an [IOException], and so is a body over [limit] bytes. */
    fun get(url: String, limit: Int, headers: Map<String, String> = emptyMap()): String {
        val reply = send(url, null, headers, limit, 30_000)
        if (reply.status != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${reply.status}")
        return reply.body
    }

    /** Whatever is answered at [url], errors included. */
    fun fetch(url: String, headers: Map<String, String>, limit: Int): Reply = send(url, null, headers, limit, 30_000)

    /** Posts [json] to [url] and gives back whatever was answered, errors included. Waits up to [patience] milliseconds for the answer. */
    fun post(url: String, json: String, headers: Map<String, String>, limit: Int, patience: Int): Reply = send(url, json, headers, limit, patience)

    private fun send(url: String, json: String?, headers: Map<String, String>, limit: Int, patience: Int): Reply {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = patience
            conn.useCaches = false
            for ((name, value) in headers) conn.setRequestProperty(name, value)
            if (json != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream: InputStream? = if (status in 200..299) conn.inputStream else conn.errorStream
            return Reply(status, stream?.use { read(it, limit) }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    private fun read(input: InputStream, limit: Int): String {
        val bytes = ByteArrayOutputStream()
        val buf = ByteArray(1 shl 14)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            bytes.write(buf, 0, n)
            if (bytes.size() > limit) throw IOException("Too large.")
        }
        return bytes.toString("UTF-8")
    }
}
