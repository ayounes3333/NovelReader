package my.noveldokusha.ui.browse.extractor.utils.zip

import my.noveldokusha.utils.encode1251
import net.lingala.zip4j.model.AbstractFileHeader

class ArchiveEntry(header: AbstractFileHeader) {
    private val header: AbstractFileHeader

    init {
        this.header = header
    }

    val size: Long
        get() = header.uncompressedSize
    val compressedSize: Long
        get() = header.compressedSize
    val isDirectory: Boolean
        get() = header.isDirectory
    val name: String
        get() = if (header.isFileNameUTF8Encoded) {
            header.fileName
        } else {
            header.fileName.encode1251() ?: ""
        }
}