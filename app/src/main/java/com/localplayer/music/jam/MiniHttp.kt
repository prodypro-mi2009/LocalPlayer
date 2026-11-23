package com.localplayer.music.jam

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One HTTP request: the first line and the headers are read, the body is NOT read yet ([body] is the stream, positioned
 * at its first byte), so a big upload can go straight to a file.
 */
class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    /** Header names in lower case. */
    val headers: Map<String, String>,
    val body: InputStream,
    /** The address the request came from (empty when unknown, e.g. in tests). */
    val remote: String = "",
) {
    val contentLength: Long get() = headers["content-length"]?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    /** The whole body as bytes; null when it is bigger than [max] or ends early. */
    fun readBody(max: Int): ByteArray? {
        val length = contentLength
        if (length > max) return null
        val out = ByteArray(length.toInt())
        var read = 0
        while (read < out.size) {
            val n = body.read(out, read, out.size - read)
            if (n < 0) return null
            read += n
        }
        return out
    }
}

/**
 * An answer. Either a small [body], or (for a song) [stream]: opened when the answer is sent, exactly [streamLength]
 * bytes of it are copied. A stream that cannot be opened (null) is answered with 404.
 */
class HttpResponse(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val contentType: String = "application/json; charset=utf-8",
    val stream: (() -> InputStream?)? = null,
    val streamLength: Long = 0L,
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    companion object {
        fun json(status: Int, text: String) = HttpResponse(status, text.toByteArray(Charsets.UTF_8))
    }
}

/** The "Range: bytes=..." header of a request for (part of) a song; a player needs it to seek. Pure, unit-tested. */
sealed interface HttpRange {
    /** No usable Range header: send everything. */
    data object Full : HttpRange

    /** Bytes [start]..[end], both included. */
    data class Partial(val start: Long, val end: Long) : HttpRange

    data object Unsatisfiable : HttpRange

    companion object {
        fun parse(header: String?, length: Long): HttpRange {
            val text = header?.trim() ?: return Full
            if (!text.startsWith("bytes=") || length <= 0) return Full
            val spec = text.removePrefix("bytes=").trim()
            if (',' in spec) return Full // several ranges at once: not needed, answer with everything
            val dash = spec.indexOf('-')
            if (dash < 0) return Full
            val first = spec.substring(0, dash).trim()
            val last = spec.substring(dash + 1).trim()
            if (first.isEmpty()) {
                val suffix = last.toLongOrNull() ?: return Full
                if (suffix <= 0) return Unsatisfiable
                return Partial((length - suffix).coerceAtLeast(0), length - 1)
            }
            val start = first.toLongOrNull() ?: return Full
            val end = if (last.isEmpty()) length - 1 else (last.toLongOrNull() ?: return Full)
            if (start >= length || end < start) return Unsatisfiable
            return Partial(start, minOf(end, length - 1))
        }
    }
}

/** Reads the head of a request. Pure (works on any stream), so it is unit-tested. */
object HttpParser {
    private const val MAX_HEAD = 16 * 1024

    /** Null when the stream does not hold a well-formed request head. [input] must be buffered (one byte at a time is read). */
    fun parse(input: InputStream, remote: String = ""): HttpRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val first = lines.firstOrNull()?.split(" ") ?: return null
        if (first.size < 3) return null
        val method = first[0].uppercase()
        val target = first[1]
        val path = target.substringBefore('?')
        if (!path.startsWith("/")) return null
        val query = HashMap<String, String>()
        if ('?' in target) {
            target.substringAfter('?').split('&').filter { it.isNotEmpty() }.forEach { pair ->
                val key = pair.substringBefore('=')
                val value = if ('=' in pair) pair.substringAfter('=') else ""
                try {
                    query[URLDecoder.decode(key, "UTF-8")] = URLDecoder.decode(value, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    return null
                }
            }
        }
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        return HttpRequest(method, path, query, headers, input, remote)
    }

    /** Everything up to the empty line, without it. Null at the end of the stream or when the head is too long. */
    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var matched = 0 // how much of "\r\n\r\n" has been seen
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            matched = when {
                b == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                b == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) return String(out.toByteArray(), 0, out.size() - 4, Charsets.ISO_8859_1)
        }
        return null
    }
}

/** Which addresses belong to the local network (a Jam is never meant for anybody else). Pure, unit-tested. */
object PeerCheck {
    /** Private IPv4 (10/8, 172.16/12, 192.168/16), link-local, loopback, and the IPv6 equivalents (fe80::/10, fc00::/7, ::1). */
    fun isLocal(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
        is Inet6Address -> address.isLinkLocalAddress || address.isLoopbackAddress || (address.address[0].toInt() and 0xFE) == 0xFC
        else -> false
    }

    /** The same rule for the text of an IPv4 address (the invite of a Jam). False for anything that is not IPv4. */
    fun isLocalIpv4(text: String): Boolean {
        val parts = text.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
    }
}

/** A very small HTTP/1.1 server: one request per connection, one thread per connection. Enough for a handful of phones. */
class MiniHttpServer(private val handler: (HttpRequest) -> HttpResponse) {
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    private val perAddress = ConcurrentHashMap<String, AtomicInteger>()

    @Volatile
    var port: Int = 0
        private set

    /** Binds the first free port from [first] ([tries] ports). False when none is free. */
    fun start(first: Int, tries: Int): Boolean {
        for (candidate in first until first + tries) {
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(candidate))
            } catch (e: IOException) {
                socket.close()
                continue
            }
            server = socket
            port = candidate
            // A bounded number of threads: when they are all busy, a new connection is simply closed.
            val executor = ThreadPoolExecutor(0, MAX_THREADS, 30, TimeUnit.SECONDS, SynchronousQueue())
            pool = executor
            Thread({ acceptLoop(socket, executor) }, "jam-accept").apply { isDaemon = true }.start()
            return true
        }
        return false
    }

    fun stop() {
        try {
            server?.close()
        } catch (e: IOException) {
            // Closing a socket that is already closed.
        }
        server = null
        pool?.shutdownNow()
        pool = null
    }

    private fun acceptLoop(socket: ServerSocket, executor: ExecutorService) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: SocketException) {
                return
            } catch (e: IOException) {
                return
            }
            val address = client.inetAddress
            val key = address?.hostAddress.orEmpty()
            // Only the local network (Wi-Fi, hotspot), and only a few connections at a time from one device.
            if (address == null || !PeerCheck.isLocal(address)) {
                client.close()
                continue
            }
            val count = perAddress.getOrPut(key) { AtomicInteger() }
            if (count.incrementAndGet() > MAX_PER_ADDRESS) {
                count.decrementAndGet()
                client.close()
                continue
            }
            try {
                executor.execute {
                    try {
                        serve(client, key)
                    } finally {
                        if (count.decrementAndGet() <= 0) perAddress.remove(key, count)
                    }
                }
            } catch (e: RejectedExecutionException) {
                count.decrementAndGet()
                client.close()
            }
        }
    }

    private fun serve(client: Socket, remote: String) {
        client.use { socket ->
            try {
                // The head of a request must arrive quickly; a body (a song) may take longer.
                socket.soTimeout = HEAD_TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val request = HttpParser.parse(input, remote) ?: return
                socket.soTimeout = READ_TIMEOUT_MS
                val response = try {
                    handler(request)
                } catch (e: Exception) {
                    HttpResponse.json(500, JamJson.error("server"))
                }
                val opener = response.stream
                val source = opener?.invoke()
                val answer = if (opener != null && source == null) HttpResponse.json(404, JamJson.error("not_found")) else response
                val length = if (source != null) answer.streamLength else answer.body.size.toLong()
                val head = StringBuilder("HTTP/1.1 ${answer.status} ${reason(answer.status)}\r\n")
                    .append("Content-Type: ${answer.contentType}\r\n")
                    .append("Content-Length: $length\r\n")
                    .append("Cache-Control: no-store\r\n")
                answer.extraHeaders.forEach { (name, value) -> head.append("$name: $value\r\n") }
                head.append("Connection: close\r\n\r\n")
                val output = socket.getOutputStream()
                output.write(head.toString().toByteArray(Charsets.ISO_8859_1))
                if (source != null) {
                    source.use { copyExactly(it, output, answer.streamLength) }
                } else {
                    output.write(answer.body)
                }
                output.flush()
            } catch (e: IOException) {
                // The phone went away in the middle of a request: nothing to do.
            }
        }
    }

    /** Copies up to [count] bytes (less when the source ends early; the client then notices the short body). */
    private fun copyExactly(input: InputStream, output: java.io.OutputStream, count: Long) {
        val buffer = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) return
            output.write(buffer, 0, n)
            left -= n
        }
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        416 -> "Range Not Satisfiable"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        413 -> "Payload Too Large"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        507 -> "Insufficient Storage"
        else -> "OK"
    }

    private companion object {
        const val READ_TIMEOUT_MS = 60_000
        const val HEAD_TIMEOUT_MS = 10_000
        const val MAX_THREADS = 64
        const val MAX_PER_ADDRESS = 16
    }
}
