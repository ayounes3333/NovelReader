package my.noveldokusha.tooling.quick_setup.transfer

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * Transport-agnostic abstraction for the Quick Setup data transfer channel.
 * Both the HTTP server and USB AOA transports implement this interface
 * so the exporter/importer logic remains transport-agnostic.
 */
interface TransferChannel : Closeable {
    val name: String

    suspend fun open()
    suspend fun readFrame(): Frame?
    suspend fun writeFrame(frame: Frame)
    suspend fun flush()

    data class Frame(
        val type: Byte,
        val payload: ByteArray,
    ) {
        companion object {
            const val TYPE_MANIFEST: Byte = 1
            const val TYPE_BOOK_BATCH: Byte = 2
            const val TYPE_CHAPTER_BATCH: Byte = 3
            const val TYPE_CHAPTER_BODY: Byte = 4
            const val TYPE_IMAGE_MANIFEST: Byte = 5
            const val TYPE_IMAGE_BLOB: Byte = 6
            const val TYPE_PREFERENCES: Byte = 7
            const val TYPE_COMPLETE: Byte = 8
            const val TYPE_ERROR: Byte = 9
            const val TYPE_ACK: Byte = 10
            const val TYPE_BOOK_PAGE_REQUEST: Byte = 11
            const val TYPE_CHAPTER_PAGE_REQUEST: Byte = 12
            const val TYPE_BODY_REQUEST: Byte = 13
            const val TYPE_IMAGE_REQUEST: Byte = 14
            const val TYPE_CANCEL: Byte = 15
            const val TYPE_RESUME: Byte = 16
            const val TYPE_PROGRESS: Byte = 17
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return type == other.type && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = type.toInt()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }
}

/**
 * Length-prefixed framing codec: [type:1][length:4][payload:N]
 */
object FramingCodec {

    fun encode(frame: TransferChannel.Frame, output: OutputStream) {
        output.write(frame.type.toInt())
        val length = frame.payload.size
        output.write((length shr 24) and 0xFF)
        output.write((length shr 16) and 0xFF)
        output.write((length shr 8) and 0xFF)
        output.write(length and 0xFF)
        output.write(frame.payload)
        output.flush()
    }

    fun decode(input: InputStream): TransferChannel.Frame? {
        val typeByte = input.read()
        if (typeByte == -1) return null
        val type = typeByte.toByte()

        val lenBytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(lenBytes, read, 4 - read)
            if (n == -1) return null
            read += n
        }

        val length = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                ((lenBytes[1].toInt() and 0xFF) shl 16) or
                ((lenBytes[2].toInt() and 0xFF) shl 8) or
                (lenBytes[3].toInt() and 0xFF)

        if (length < 0 || length > 50 * 1024 * 1024) return null // 50MB max frame

        val payload = ByteArray(length)
        read = 0
        while (read < length) {
            val n = input.read(payload, read, length - read)
            if (n == -1) return null
            read += n
        }

        return TransferChannel.Frame(type, payload)
    }
}
