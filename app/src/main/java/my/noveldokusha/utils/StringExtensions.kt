package my.noveldokusha.utils

import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

fun String.encode1251(): String? {
    return if (Charset.forName("8859_1").newEncoder().canEncode(this)) {
        this.encode("8859_1", "Windows-1251")
    } else {
        this
    }
}

fun String.removeMimeTypeFromBase64(): String {
    val ar = this.split(',')
    return if (ar.size > 1)
        ar[ar.lastIndex]
    else this
}

fun String.encode(from: String, to: String): String? {
    return try {
        String(this.toByteArray(charset(from)), Charset.forName(to))
    } catch (e: UnsupportedEncodingException) {
        this
    }
}