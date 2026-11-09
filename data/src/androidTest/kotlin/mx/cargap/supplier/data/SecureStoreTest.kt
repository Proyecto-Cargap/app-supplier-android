package mx.cargap.supplier.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SecureStoreTest {
    @Test fun deviceKeystoreEncryptsAndDetectsTampering() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AndroidSecureSessionStore(context, "fixture.secure.store")
        store.remove()
        try {
            store.write("fixture-private-data")
            assertEquals("fixture-private-data", AndroidSecureSessionStore(context, "fixture.secure.store").read())
            val file = File(context.noBackupFilesDir, "session.bin")
            assertFalse(file.readText().contains("fixture-private-data"))
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertTrue(runCatching { store.read() }.isFailure)
        } finally { store.remove() }
        assertNull(store.read())
    }
}
