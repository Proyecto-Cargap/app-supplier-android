package mx.cargap.supplier.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Almacén técnico sustituible. Nunca se inyecta a vistas ni ViewModels. */
interface SecureSessionStore {
    suspend fun read(): String?
    suspend fun write(data: String)
    suspend fun remove()
}

/** AES-GCM autenticado con clave no exportable de Android Keystore; archivo excluido de respaldos. */
class AndroidSecureSessionStore(context: Context, namespace: String) : SecureSessionStore {
    private val file = File(context.noBackupFilesDir, "session.bin")
    private val preferences = context.getSharedPreferences("session_barrier", Context.MODE_PRIVATE)
    private val alias = namespace + ".session.v1"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private fun barrier(invalidated: Boolean) {
        check(preferences.edit().putBoolean("invalidated", invalidated).commit())
    }
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (preferences.getBoolean("invalidated", false) || !file.exists()) return@withContext null
        val bytes = file.readBytes(); check(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(alias.toByteArray())
        cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    override suspend fun write(data: String): Unit = withContext(Dispatchers.IO) {
        barrier(true)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(alias.toByteArray())
        val bytes = cipher.iv + cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val temporary = File(file.parentFile, "session.tmp")
        try {
            temporary.outputStream().use { it.write(bytes); it.fd.sync() }
            check(temporary.renameTo(file)); barrier(false)
        } finally { temporary.delete() }
    }
    override suspend fun remove(): Unit = withContext(Dispatchers.IO) {
        barrier(true)
        if (file.exists()) check(file.delete())
    }
}
