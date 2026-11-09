package mx.cargap.supplier.data

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

/** Cámara frontal exclusiva, sin galería ni carga automática. El resultado se confirma en el asistente. */
class EnrollmentCameraActivity : ComponentActivity() {
    private var capture: ImageCapture? = null
    private var cameraPreview: Preview? = null
    private var provider: ProcessCameraProvider? = null
    private lateinit var preview: PreviewView
    private lateinit var shutter: Button
    private lateinit var status: TextView
    private var output: Uri? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera() else showError("Permite el uso de la cámara para tomar tu selfie.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        output = intent.getStringExtra("output")?.let(Uri::parse)?.takeIf { it.scheme == "content" && it.authority == packageName + ".evidence" }
        if (output == null) { finish(); return }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 32); gravity = Gravity.CENTER }
        status = TextView(this).apply { text = "Mira directamente a la cámara. Mantén tu rostro visible y busca buena iluminación."; textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 16, 0, 24) }
        preview = PreviewView(this).apply { contentDescription = "Vista previa de la cámara frontal" }
        shutter = Button(this).apply { text = "Tomar selfie"; isEnabled = false; setOnClickListener { takePhoto() } }
        layout.addView(status)
        layout.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(shutter, LinearLayout.LayoutParams(-1, -2))
        layout.addView(Button(this).apply { text = "Cancelar"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }
    private fun showError(message: String) { status.text = message; shutter.isEnabled = false }
    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                val cameraProvider = future.get(); provider = cameraProvider
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) { showError("Este dispositivo no tiene una cámara frontal disponible."); return@addListener }
                val stream = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }; cameraPreview = stream
                val image = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, stream, image)
                capture = image; shutter.isEnabled = true
            } catch (_: Exception) { showError("No pudimos abrir la cámara. Cierra esta vista y vuelve a intentarlo.") }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun takePhoto() {
        val image = capture ?: return
        val uri = output ?: return
        val stream = try { contentResolver.openOutputStream(uri, "w") } catch (_: Exception) { null }
        if (stream == null) { showError("No pudimos guardar la foto temporal."); return }
        shutter.isEnabled = false
        image.targetRotation = preview.display?.rotation ?: 0
        image.takePicture(ImageCapture.OutputFileOptions.Builder(stream).build(), ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                stream.close()
                setResult(Activity.RESULT_OK); finish()
            }
            override fun onError(error: ImageCaptureException) {
                stream.close(); showError("No pudimos tomar la foto. Cierra esta vista y vuelve a intentarlo.")
            }
        })
    }
    override fun onDestroy() { provider?.unbind(*listOfNotNull(cameraPreview, capture).toTypedArray()); capture = null; cameraPreview = null; super.onDestroy() }
}
