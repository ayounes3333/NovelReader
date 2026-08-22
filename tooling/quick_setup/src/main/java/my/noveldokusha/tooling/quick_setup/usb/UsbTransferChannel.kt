package my.noveldokusha.tooling.quick_setup.usb

import android.hardware.usb.UsbDeviceConnection
import my.noveldokusha.tooling.quick_setup.transfer.FramingCodec
import my.noveldokusha.tooling.quick_setup.transfer.TransferChannel
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TransferChannel implementation over a USB AOA stream.
 * Wraps the USB ParcelFileDescriptor's input/output streams with
 * length-prefixed framing via FramingCodec.
 */
class UsbTransferChannel(
    override val name: String = "USB",
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val connection: UsbDeviceConnection? = null,
) : TransferChannel {

    private val isOpen = AtomicBoolean(false)
    private val writeLock = Any()

    override suspend fun open() {
        isOpen.set(true)
        Timber.i("USB transfer channel opened")
    }

    override suspend fun readFrame(): TransferChannel.Frame? {
        if (!isOpen.get()) return null
        return try {
            FramingCodec.decode(inputStream)
        } catch (e: Exception) {
            Timber.w(e, "USB read frame failed")
            null
        }
    }

    override suspend fun writeFrame(frame: TransferChannel.Frame) {
        if (!isOpen.get()) return
        synchronized(writeLock) {
            try {
                FramingCodec.encode(frame, outputStream)
            } catch (e: Exception) {
                Timber.w(e, "USB write frame failed")
                throw e
            }
        }
    }

    override suspend fun flush() {
        synchronized(writeLock) {
            outputStream.flush()
        }
    }

    override fun close() {
        isOpen.set(false)
        try {
            outputStream.close()
        } catch (_: Exception) {}
        try {
            inputStream.close()
        } catch (_: Exception) {}
        connection?.close()
        Timber.i("USB transfer channel closed")
    }
}
