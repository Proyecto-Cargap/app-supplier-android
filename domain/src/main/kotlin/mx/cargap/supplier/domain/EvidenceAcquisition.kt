package mx.cargap.supplier.domain

/** Referencias opacas de selección; el adaptador conserva permisos, lectura y temporales. */
interface EvidenceAcquisition {
    fun cameraReference(): String
    suspend fun read(reference: String): Evidence
    fun clear()
}
