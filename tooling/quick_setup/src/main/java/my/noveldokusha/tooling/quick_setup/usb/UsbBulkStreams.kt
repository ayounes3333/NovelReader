package my.noveldokusha.tooling.quick_setup.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val BUFFER_SIZE = 16 * 1024
private const val WRITE_TIMEOUT_MS = 10_000

/**
 * InputStream over a USB bulk IN endpoint. Used by the host side of an AOA
 * connection, which must talk to the re-enumerated accessory device through
 * bulk transfers (openAccessory() is only available on the accessory side).
 *
 * Reads block indefinitely (timeout 0); closing the UsbDeviceConnection makes
 * the pending bulkTransfer return -1, which is surfaced as end-of-stream.
 */
class UsbBulkInputStream(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
) : InputStream() {

    private val buffer = ByteArray(BUFFER_SIZE)
    private var pos = 0
    private var count = 0

    override fun read(): Int {
        val single = ByteArray(1)
        val n = read(single, 0, 1)
        return if (n <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (pos >= count) {
            val n = connection.bulkTransfer(endpoint, buffer, buffer.size, 0)
            if (n < 0) return -1
            count = n
            pos = 0
        }
        val toCopy = minOf(len, count - pos)
        System.arraycopy(buffer, pos, b, off, toCopy)
        pos += toCopy
        return toCopy
    }
}

/**
 * OutputStream over a USB bulk OUT endpoint (host side of an AOA connection).
 */
class UsbBulkOutputStream(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
) : OutputStream() {

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            val chunkSize = minOf(remaining, BUFFER_SIZE)
            val chunk = if (offset == 0 && chunkSize == b.size) b
            else b.copyOfRange(offset, offset + chunkSize)
            val n = connection.bulkTransfer(endpoint, chunk, chunkSize, WRITE_TIMEOUT_MS)
            if (n < 0) throw IOException("USB bulk write failed")
            offset += n
            remaining -= n
        }
    }
}
