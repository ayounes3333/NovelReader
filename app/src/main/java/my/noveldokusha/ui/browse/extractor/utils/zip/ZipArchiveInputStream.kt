package my.noveldokusha.ui.browse.extractor.utils.zip

import android.util.Log
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*

class ZipArchiveInputStream(file: String?) : InputStream() {

    // public static final Lock lock = new ReentrantLock();
    private var zis: ZipInputStream? = null
    private var iterator: Iterator<FileHeader>? = null
    private var current: FileHeader? = null
    private var zp: ZipFile? = null
    private var inputStream: ZipInputStream? = null
    private val tempFile: File? = null

    init {
        // CacheZipUtils.cacheLock.lock();
        try {
            zp = ZipFile(file)

            val fileHeaders = zp!!.fileHeaders
            Collections.sort(fileHeaders, Comparator { o1, o2 ->
                try {
                    return@Comparator o1.fileName.compareTo(o2.fileName)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@Comparator 0
                }
            })
            iterator = fileHeaders.iterator()
            Log.d("ZipArchiveInputStream", "ZipArchiveInputStream$file")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        // CacheZipUtils.cacheLock.unlock();
        closeStream()
    }

    fun release() {
        tempFile?.delete()
        closeStream()
        if (zp != null) {
            zp = null
        }
    }

    private fun closeStream() {
        if (zis != null) {
            try {
                zis!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            zis = null
        }
        if (inputStream != null) {
            try {
                inputStream!!.close()
                inputStream = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val nextEntry: ArchiveEntry?
        get() {
            if (zis != null) {
                return try {
                    val nextEntry = zis!!.nextEntry
                    nextEntry?.let { ArchiveEntry(it) }
                } catch (e: IOException) {
                    null
                }
            }
            if (iterator == null || (iterator?.hasNext() == false)) {
                return null
            }
            closeStream()
            current = iterator?.next()
            return if (current != null) ArchiveEntry(current!!) else null
        }

    @Throws(IOException::class)
    private fun openStream() {
        if (zis != null) {
            return
        }
        if (inputStream == null) {
            Log.d("ZipArchiveInputStream", "openStream$zp")
            inputStream = try {
                zp!!.getInputStream(current)
            } catch (e: ZipException) {
                throw IOException()
            }
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (zis != null) {
            return zis!!.read()
        }
        openStream()
        return inputStream!!.read()
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        if (zis != null) {
            return zis!!.read(b)
        }
        openStream()
        return inputStream!!.read(b)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (zis != null) {
            return zis!!.read(b, off, len)
        }
        openStream()
        return inputStream!!.read(b, off, len)
    }
}