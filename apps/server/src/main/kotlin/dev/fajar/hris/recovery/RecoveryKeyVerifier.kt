package dev.fajar.hris.recovery

import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.sync.data.crypto.parseSyncCursorKeyring
import java.io.Reader
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.system.exitProcess

/** Offline recovery entry point. It starts no Spring context, server, worker or provider. */
object RecoveryKeyVerifier {
    @JvmStatic
    fun main(arguments: Array<String>) {
        try {
            require(arguments.size == 2)
            val configuration = privateInput(Path.of(arguments[0]), 16_384)
            val records = privateInput(Path.of(arguments[1]), 512L * 1024 * 1024)
            val lines = Files.readAllLines(configuration)
            require(lines.size == 4)
            val identity = parseIdentityKeyring(lines[0], lines[1])
            require(identity.keys.isNotEmpty())
            parseSyncCursorKeyring(lines[2], lines[3])
            val count = Files.newBufferedReader(records).use { verify(identity, it) }
            println("{\"identityEnvelopesVerified\":$count}")
        } catch (_: Exception) {
            System.err.println(
                "Recovery key verification failed; no decrypted values were retained."
            )
            exitProcess(1)
        }
    }

    fun verify(keyring: IdentityKeyring, input: Reader): Int {
        var count = 0
        while (true) {
            val line = readBoundedLine(input) ?: return count
            require(count < 100_000)
            val fields = line.split('\t', limit = 3)
            require(fields.size == 2 && fields[0].length <= 256)
            // Reuse the application's actual AES-GCM envelope/AAD contract. Only the
            // authentication result is needed; plaintext never enters a report/log.
            decryptIdentityEnvelope(keyring, fields[0], fields[1])
            count++
        }
    }

    private fun readBoundedLine(input: Reader): String? {
        val line = StringBuilder()
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val character = input.read()
            if (character == -1) return if (line.isEmpty()) null else line.toString()
            if (character == '\n'.code) return line.toString()
            require(line.length < 4_354)
            line.append(character.toChar())
        }
    }

    private fun privateInput(path: Path, limit: Long): Path {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) <= limit)
        val permissions = Files.getPosixFilePermissions(path, NOFOLLOW_LINKS)
        require(permissions.none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") })
        return path
    }
}
