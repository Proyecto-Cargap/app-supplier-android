package mx.cargap.supplier.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlatformConfigurationTest {
    @Test fun bundledPaletteContainsValidColorsForLightAndDarkAppearance() = runTest {
        val config = LocalPlatformConfigurationRepository().load()
        assertEquals("CARGAP", config.title)
        for ((palette, shades) in listOf(
            config.primary to listOf("400", "500", "600", "700"),
            config.surface to listOf("0", "50", "100", "200", "800", "900", "950"),
        )) {
            for (shade in shades) {
                val color = palette[shade]
                assertNotNull("Falta la tonalidad $shade", color)
                assertTrue("Color inválido para $shade", color!!.matches(Regex("#[0-9a-fA-F]{6}")))
            }
        }
    }
}
