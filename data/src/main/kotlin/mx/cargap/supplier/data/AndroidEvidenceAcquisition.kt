package mx.cargap.supplier.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.cargap.supplier.domain.*
import java.io.File
import java.util.UUID

/** Lee documentos con permisos temporales; no conserva copias fuera de la caché privada. */
class AndroidEvidenceAcquisition(private val context: Context) : EvidenceAcquisition {
    private val directory = File(context.cacheDir, "enrollment-camera")
    init { clear() }
    override fun cameraReference(): String {
        directory.mkdirs()
        val file = File.createTempFile("selfie-", ".jpg", directory)
        return FileProvider.getUriForFile(context, context.packageName + ".evidence", file).toString()
    }
    override suspend fun read(reference: String): Evidence = withContext(Dispatchers.IO) {
        val uri = Uri.parse(reference)
        require(uri.scheme == "content")
        val resolver = context.contentResolver
        var name = "documento"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index) && cursor.getLong(index) > 10485760) throw AccessException(Failure.TOO_LARGE)
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > 10485760) throw AccessException(Failure.TOO_LARGE)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw AccessException(Failure.INVALID_INPUT)
        val mime = when {
            bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray()) -> "application/pdf"
            bytes.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
            bytes.size >= 3 && bytes[0] == 255.toByte() && bytes[1] == 216.toByte() && bytes[2] == 255.toByte() -> "image/jpeg"
            else -> throw AccessException(Failure.INVALID_INPUT)
        }
        if (uri.authority == context.packageName + ".evidence") clear()
        Evidence(UUID.randomUUID().toString(), name, mime, bytes)
    }
    override fun clear() { directory.listFiles()?.forEach { it.delete() } }
}
