package mx.cargap.supplier.data

import mx.cargap.supplier.domain.PlatformConfiguration
import mx.cargap.supplier.domain.PlatformConfigurationRepository

/** Identidad CARGAP compilada en esta app autónoma, independiente de los menús del servidor. */
class LocalPlatformConfigurationRepository : PlatformConfigurationRepository {
    override suspend fun load() = PlatformConfiguration(
        title = "CARGAP",
        primary = mapOf(
            "50" to "#fff1f3", "100" to "#ffe2e7", "200" to "#ffc5d0", "300" to "#ff97a9",
            "400" to "#f95e79", "500" to "#e5213d", "600" to "#c91934", "700" to "#a9132c",
            "800" to "#8d162b", "900" to "#78192b", "950" to "#420915",
        ),
        surface = mapOf(
            "0" to "#ffffff", "50" to "#fafafb", "100" to "#f3f4f6", "200" to "#e3e5e9",
            "300" to "#cbd0d8", "400" to "#a3a8b2", "500" to "#727784", "600" to "#535965",
            "700" to "#3b404a", "800" to "#272a31", "900" to "#191c22", "950" to "#101216",
        ),
    )
}
