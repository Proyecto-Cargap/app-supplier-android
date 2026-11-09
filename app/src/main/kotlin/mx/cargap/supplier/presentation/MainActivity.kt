package mx.cargap.supplier.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import mx.cargap.supplier.domain.EvidenceAcquisition

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    @Inject lateinit var acquisition: EvidenceAcquisition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { AccessRoot(hiltViewModel(), hiltViewModel(), registration = hiltViewModel(), enrollment = hiltViewModel(), acquisition = acquisition) }
    }
}
