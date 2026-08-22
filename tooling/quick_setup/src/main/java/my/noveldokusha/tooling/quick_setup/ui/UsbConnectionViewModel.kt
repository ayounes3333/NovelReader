package my.noveldokusha.tooling.quick_setup.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.quick_setup.usb.UsbConnectionManager
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupExporter
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupImporter
import my.noveldokusha.tooling.quick_setup.transfer.ImageManifestEntry
import my.noveldokusha.tooling.quick_setup.transfer.TransferChannel
import my.noveldokusha.tooling.quick_setup.transfer.TransferProgress
import my.noveldokusha.tooling.quick_setup.transfer.dto.BookDto
import my.noveldokusha.tooling.quick_setup.transfer.dto.ChapterBodyDto
import my.noveldokusha.tooling.quick_setup.transfer.dto.ChapterDto
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class UsbConnectionViewModel @Inject constructor(
    application: Application,
    private val exporter: QuickSetupExporter,
    private val importer: QuickSetupImporter,
) : AndroidViewModel(application) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val usbManager = UsbConnectionManager(application)

    private val _connectionState = MutableStateFlow<UsbConnectionManager.ConnectionState>(
        UsbConnectionManager.ConnectionState.Disconnected
    )
    val connectionState: StateFlow<UsbConnectionManager.ConnectionState> = _connectionState.asStateFlow()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    sealed class TransferState {
        data object Idle : TransferState()
        data class Sending(val progress: TransferProgress) : TransferState()
        data class Receiving(val progress: TransferProgress) : TransferState()
        data object Completed : TransferState()
        data class Error(val message: String) : TransferState()
    }

    fun startConnection() {
        usbManager.detectAndConnect { state ->
            _connectionState.value = state
            if (state is UsbConnectionManager.ConnectionState.Connected) {
                Timber.i("USB connected as ${state.role}")
            }
        }
    }

    fun disconnect() {
        usbManager.disconnect()
        _connectionState.value = UsbConnectionManager.ConnectionState.Disconnected
        _transferState.value = TransferState.Idle
    }

    fun startSendViaUsb() {
        val state = _connectionState.value
        if (state !is UsbConnectionManager.ConnectionState.Connected) {
            _transferState.value = TransferState.Error("Not connected via USB")
            return
        }

        _transferState.value = TransferState.Sending(
            TransferProgress(
                phase = TransferProgress.Phase.CONNECTING,
                itemsTransferred = 0,
                totalItems = 0,
                bytesTransferred = 0,
                totalBytes = 0,
            )
        )

        val channel = state.channel
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sendViaChannel(channel)
                _transferState.value = TransferState.Completed
            } catch (e: Exception) {
                Timber.e(e, "USB send failed")
                _transferState.value = TransferState.Error(e.message ?: "Send failed")
            } finally {
                channel.close()
            }
        }
    }

    private suspend fun sendViaChannel(channel: TransferChannel) {
        val manifest = exporter.createManifest()
        val manifestJson = json.encodeToString(manifest)
        channel.open()
        channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_MANIFEST, manifestJson.toByteArray()))
        channel.flush()

        val ack = channel.readFrame()
        if (ack?.type != TransferChannel.Frame.TYPE_ACK) {
            throw RuntimeException("Expected ACK after manifest")
        }

        var page = 0
        while (true) {
            val books = exporter.getBooksPage(page)
            if (books.isEmpty()) break
            val bookJson = json.encodeToString(books)
            channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_BOOK_BATCH, bookJson.toByteArray()))
            channel.flush()
            page++
        }

        page = 0
        while (true) {
            val chapters = exporter.getChaptersPage(page)
            if (chapters.isEmpty()) break
            val chapterJson = json.encodeToString(chapters)
            channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_CHAPTER_BATCH, chapterJson.toByteArray()))
            channel.flush()
            page++
        }

        // Chapter bodies (downloaded/offline content)
        page = 0
        while (true) {
            val bodies = exporter.getChapterBodiesPage(page)
            if (bodies.isEmpty()) break
            val bodiesJson = json.encodeToString(bodies)
            channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_CHAPTER_BODY, bodiesJson.toByteArray()))
            channel.flush()
            page++
        }

        // Images: blob frame payload = [4-byte header length][header JSON][raw bytes]
        val imageManifest = exporter.getImageManifest()
        for (entry in imageManifest) {
            val data = exporter.getImageBlob(entry.relativePath) ?: continue
            val header = json.encodeToString(entry).toByteArray()
            val payload = ByteArray(4 + header.size + data.size)
            payload[0] = (header.size shr 24 and 0xFF).toByte()
            payload[1] = (header.size shr 16 and 0xFF).toByte()
            payload[2] = (header.size shr 8 and 0xFF).toByte()
            payload[3] = (header.size and 0xFF).toByte()
            System.arraycopy(header, 0, payload, 4, header.size)
            System.arraycopy(data, 0, payload, 4 + header.size, data.size)
            channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_IMAGE_BLOB, payload))
            channel.flush()
        }

        val prefsJson = exporter.getPreferencesJson()
        channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_PREFERENCES, prefsJson.toByteArray()))
        channel.flush()

        channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_COMPLETE, ByteArray(0)))
        channel.flush()
        channel.close()
    }

    fun startReceiveViaUsb() {
        val state = _connectionState.value
        if (state !is UsbConnectionManager.ConnectionState.Connected) {
            _transferState.value = TransferState.Error("Not connected via USB")
            return
        }

        _transferState.value = TransferState.Receiving(
            TransferProgress(
                phase = TransferProgress.Phase.CONNECTING,
                itemsTransferred = 0,
                totalItems = 0,
                bytesTransferred = 0,
                totalBytes = 0,
            )
        )

        val channel = state.channel
        viewModelScope.launch(Dispatchers.IO) {
            try {
                receiveViaChannel(channel)
                _transferState.value = TransferState.Completed
            } catch (e: Exception) {
                Timber.e(e, "USB receive failed")
                _transferState.value = TransferState.Error(e.message ?: "Receive failed")
            } finally {
                channel.close()
            }
        }
    }

    private suspend fun receiveViaChannel(channel: TransferChannel) {
        channel.open()

        val manifestFrame = channel.readFrame()
            ?: throw RuntimeException("No manifest received")
        val manifestJson = String(manifestFrame.payload)
        val manifest = json.decodeFromString<my.noveldokusha.tooling.quick_setup.transfer.TransferManifest>(manifestJson)

        _transferState.value = TransferState.Receiving(
            TransferProgress(
                phase = TransferProgress.Phase.TRANSFERRING_DATABASE,
                itemsTransferred = 0,
                totalItems = manifest.booksCount + manifest.chaptersCount,
                bytesTransferred = 0,
                totalBytes = 0,
            )
        )

        channel.writeFrame(TransferChannel.Frame(TransferChannel.Frame.TYPE_ACK, ByteArray(0)))
        channel.flush()

        var transferred = 0
        var sawComplete = false

        while (true) {
            val frame = channel.readFrame() ?: break
            when (frame.type) {
                TransferChannel.Frame.TYPE_BOOK_BATCH -> {
                    val books = json.decodeFromString<List<BookDto>>(String(frame.payload))
                    importer.importBooks(books.map { it.toBook() })
                    transferred += books.size
                    _transferState.value = TransferState.Receiving(
                        TransferProgress(
                            phase = TransferProgress.Phase.TRANSFERRING_DATABASE,
                            itemsTransferred = transferred,
                            totalItems = manifest.booksCount + manifest.chaptersCount,
                            bytesTransferred = 0,
                            totalBytes = 0,
                        )
                    )
                }
                TransferChannel.Frame.TYPE_CHAPTER_BATCH -> {
                    val chapters = json.decodeFromString<List<ChapterDto>>(String(frame.payload))
                    importer.importChapters(chapters.map { it.toChapter() })
                    transferred += chapters.size
                }
                TransferChannel.Frame.TYPE_CHAPTER_BODY -> {
                    val bodies = json.decodeFromString<List<ChapterBodyDto>>(String(frame.payload))
                    importer.importChapterBodies(bodies.map { it.toChapterBody() })
                }
                TransferChannel.Frame.TYPE_IMAGE_BLOB -> {
                    // Payload = [4-byte header length][header JSON][raw bytes]
                    val p = frame.payload
                    if (p.size < 4) {
                        Timber.w("Malformed image blob frame")
                    } else {
                        val headerLen = ((p[0].toInt() and 0xFF) shl 24) or
                                ((p[1].toInt() and 0xFF) shl 16) or
                                ((p[2].toInt() and 0xFF) shl 8) or
                                (p[3].toInt() and 0xFF)
                        if (headerLen < 0 || 4 + headerLen > p.size) {
                            Timber.w("Malformed image blob header length: $headerLen")
                        } else {
                            val entry = json.decodeFromString<ImageManifestEntry>(
                                String(p, 4, headerLen)
                            )
                            val data = p.copyOfRange(4 + headerLen, p.size)
                            importer.importImage(entry.relativePath, data, entry.sha256)
                            _transferState.value = TransferState.Receiving(
                                TransferProgress(
                                    phase = TransferProgress.Phase.TRANSFERRING_IMAGES,
                                    itemsTransferred = 0,
                                    totalItems = manifest.imagesCount,
                                    bytesTransferred = data.size.toLong(),
                                    totalBytes = manifest.totalImageBytes,
                                )
                            )
                        }
                    }
                }
                TransferChannel.Frame.TYPE_PREFERENCES -> {
                    _transferState.value = TransferState.Receiving(
                        TransferProgress(
                            phase = TransferProgress.Phase.TRANSFERRING_PREFERENCES,
                            itemsTransferred = 0,
                            totalItems = 1,
                            bytesTransferred = 0,
                            totalBytes = 0,
                        )
                    )
                    importer.importPreferences(String(frame.payload))
                }
                TransferChannel.Frame.TYPE_COMPLETE -> {
                    sawComplete = true
                    break
                }
                else -> Timber.w("Unexpected frame type: ${frame.type}")
            }
        }

        // A null frame (EOF/corruption) must not masquerade as success
        if (!sawComplete) {
            throw RuntimeException(
                "USB connection lost before transfer completed " +
                        "($transferred of ${manifest.booksCount + manifest.chaptersCount} items received)"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        usbManager.disconnect()
    }
}
