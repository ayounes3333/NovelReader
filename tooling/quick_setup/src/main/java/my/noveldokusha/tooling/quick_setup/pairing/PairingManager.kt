package my.noveldokusha.tooling.quick_setup.pairing

import android.os.Build
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Manages pairing for Quick Setup sessions.
 * Generates and verifies 6-digit PINs and session tokens.
 * Provides QR code payload data for the source device.
 */
object PairingManager {

    private const val PIN_LENGTH = 6
    private val secureRandom = SecureRandom()

    /**
     * Generate a random 6-digit PIN for pairing.
     * The PIN is displayed on the source device and typed on the target.
     */
    fun generatePin(): String {
        val pin = secureRandom.nextInt(900000) + 100000 // 100000..999999
        return pin.toString()
    }

    /**
     * Generate a cryptographically secure session token.
     * Used for HTTP Authorization header authentication.
     */
    fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Derive a session key from a PIN using PBKDF2.
     * This allows PIN-based authentication without sending the raw PIN.
     */
    fun deriveKeyFromPin(pin: String): String {
        val salt = "NovelDokusha-QuickSetup-v1".toByteArray()
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 100000, 256)
        val secret = factory.generateSecret(spec)
        return secret.encoded.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify a PIN against an expected PIN.
     * Uses constant-time comparison to prevent timing attacks.
     */
    fun verifyPin(provided: String, expected: String): Boolean {
        if (provided.length != expected.length) return false
        var result = 0
        for (i in provided.indices) {
            result = result or (provided[i].code xor expected[i].code)
        }
        return result == 0
    }

    /**
     * Create a QR code payload containing connection information.
     * Format: JSON with ip, port, token, deviceName, fingerprint
     */
    fun createQrPayload(
        ip: String,
        port: Int,
        sessionToken: String,
        deviceName: String,
    ): String {
        // For simplicity, use a colon-separated format
        // In production, use JSON or a more structured format
        return "NDS:$ip:$port:$sessionToken:$deviceName"
    }

    /**
     * Parse a QR code payload into connection details.
     */
    fun parseQrPayload(payload: String): QrConnectionInfo? {
        return try {
            if (payload.startsWith("NDS:")) {
                val parts = payload.removePrefix("NDS:").split(":", limit = 4)
                if (parts.size >= 4) {
                    QrConnectionInfo(
                        ip = parts[0],
                        port = parts[1].toIntOrNull() ?: return null,
                        sessionToken = parts[2],
                        deviceName = parts.getOrElse(3) { "Unknown Device" },
                    )
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    data class QrConnectionInfo(
        val ip: String,
        val port: Int,
        val sessionToken: String,
        val deviceName: String,
    )

    /**
     * Check if a session token is valid (non-empty and reasonable length).
     */
    fun isValidSessionToken(token: String): Boolean {
        return token.length >= 16 && token.all { it.isLetterOrDigit() }
    }
}
