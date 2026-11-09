package mx.cargap.supplier.presentation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mx.cargap.supplier.R
import mx.cargap.supplier.domain.*

/** Asistente documental. Las lecturas, revisiones y cargas conservan la autoridad del servidor. */
@Composable fun EnrollmentScreen(model: EnrollmentViewModel, acquisition: EvidenceAcquisition?, refreshAccount: () -> Unit = {}, back: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var selected by remember { mutableStateOf<DocumentRequirement?>(null) }
    var reference by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Evidence?>(null) }
    var active by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    LaunchedEffect(state.step) { scroll.scrollTo(0) }
    fun read(uri: Uri?) {
        if (uri == null || acquisition == null) return
        scope.launch {
            try { val result = acquisition.read(uri.toString()); if (active) preview = result }
            catch (e: Exception) { if (e is CancellationException) throw e; if (active) model.acquisitionFailed() }
        }
    }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { read(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) read(reference?.let(Uri::parse)) else acquisition?.clear()
    }
    fun pick(requirement: DocumentRequirement) {
        selected = requirement
        try {
            if (requirement.code == "selfie") {
                reference = acquisition?.cameraReference()
                reference?.let { camera.launch(Intent(context.packageName.substringBeforeLast(".test").substringBeforeLast(".dev") + ".action.ENROLLMENT_SELFIE")
                    .setPackage(context.packageName).putExtra("output", it)) }
            } else document.launch(requirement.mimeTypes.toTypedArray())
        } catch (_: Exception) { acquisition?.clear(); model.acquisitionFailed() }
    }
    DisposableEffect(Unit) { onDispose { active = false; preview = null; acquisition?.clear() } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) model.load() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model) { model.load() }
    AccessSystemBars(dark)
    val foreground = if (dark) Color.White else Color.Black
    CompositionLocalProvider(LocalContentColor provides foreground) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (dark) Color(0xff101216) else Color.White).safeDrawingPadding().imePadding()) {
            val height = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = height).padding(horizontal = 24.dp).testTag("enrollment-screen"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    TextButton(onClick = back, modifier = Modifier.align(Alignment.Start)) { EText("‹ Volver", color = AccessRed) }
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Paso ${state.step} de 4" }, verticalAlignment = Alignment.CenterVertically) {
                        (1..4).forEach { number ->
                            if (number > 1) HorizontalDivider(Modifier.weight(1f), color = foreground.copy(alpha = .55f))
                            Surface(shape = CircleShape, color = if (number <= state.step) AccessRed else AccessRed.copy(alpha = .15f), border = BorderStroke(1.dp, AccessRed.copy(alpha = .5f))) {
                                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { EText("$number", 30, color = if (number <= state.step) Color.White else if (dark) Color(0xffff8a9b) else AccessRed) }
                            }
                        }
                    }
                    val status = state.showingStatus || state.enrollment?.state in listOf(EnrollmentStatus.IN_REVIEW, EnrollmentStatus.APPROVED)
                    if (status) {
                        EnrollmentSummary(state.enrollment)
                        if (state.enrollment?.state == EnrollmentStatus.CORRECTION_REQUIRED) EButton("Corregir documentos", !state.busy) { model.status(false) }
                    } else {
                        EText("Adjunta los documentos requeridos para completar tu registro.")
                        if (state.step == 1) {
                            EText("¿Qué tipo de contribuyente eres?", modifier = Modifier.padding(vertical = 12.dp))
                            PersonType.entries.forEach { type ->
                                Surface(shape = CircleShape, color = foreground.copy(alpha = .045f), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().selectableWithoutIndication(state.type == type,
                                        !state.busy && state.enrollment?.personType == null) { model.select(type) }.padding(16.dp).testTag("person-${type.code}"), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(state.type == type, null); Spacer(Modifier.width(12.dp)); EText(type.label)
                                    }
                                }
                            }
                            if (state.type == PersonType.LEGAL) OutlinedTextField(state.tradeName, model::tradeName, label = { EText("Nombre comercial") }, singleLine = true,
                                enabled = state.enrollment?.personType == null, modifier = Modifier.fillMaxWidth().testTag("enrollment-trade-name"))
                            Spacer(Modifier.height(80.dp))
                        } else {
                            if (state.step == 4) Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 20.dp)) {
                                EText("Consejos rápidos", modifier = Modifier.fillMaxWidth())
                                EText("Mira directamente a la cámara.")
                                EText("Mantén tu rostro dentro del recuadro.")
                                EText("Buena iluminación, sin sombras.")
                            }
                            state.enrollment?.requirements?.filter { it.step == state.step }?.forEach { requirement ->
                                val uploaded = state.enrollment?.documents?.firstOrNull { it.requirementCode == requirement.code }
                                val editable = !state.busy && acquisition != null && state.enrollment?.pendingOperations.isNullOrEmpty() &&
                                    (uploaded == null || uploaded.state in listOf(DocumentStatus.UPLOADED, DocumentStatus.REJECTED))
                                if (requirement.code == "selfie") {
                                    Spacer(Modifier.height(60.dp))
                                    EButton(if (uploaded == null) "Tomar selfie" else if (editable) "Repetir la selfie" else "Selfie confirmada", editable, Modifier.testTag("upload-selfie")) { pick(requirement) }
                                } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    EText(requirement.label)
                                    if (requirement.instruction.isNotEmpty()) EText(requirement.instruction, 13)
                                    Surface(shape = RoundedCornerShape(24.dp), color = foreground.copy(alpha = .045f), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().clickable(enabled = editable, onClickLabel = "Adjuntar ${requirement.label}") { pick(requirement) }.padding(24.dp).heightIn(min = 150.dp).testTag("upload-${requirement.code}"),
                                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Image(painterResource(if (requirement.code == "selfie") R.drawable.enrollment_camera else R.drawable.enrollment_document), null, Modifier.size(64.dp))
                                            uploaded?.let { EText(it.name, 14); EText(it.state.label, 14); if (it.reason.isNotEmpty()) EText(it.reason, 14, color = MaterialTheme.colorScheme.error) }
                                            EText(if (requirement.code == "selfie") (if (uploaded == null) "Tomar selfie" else "Repetir la selfie") else if (uploaded == null) "Toca para adjuntar archivo" else if (editable) "Sustituir archivo" else "Documento confirmado", 14)
                                            EText(if (requirement.mimeTypes.size == 1) "PDF · Máx. 10 MiB" else if ("application/pdf" in requirement.mimeTypes) "JPG, PNG o PDF · Máx. 10 MiB" else "JPG o PNG · Máx. 10 MiB", 12)
                                        }
                                    }
                                }
                            }
                        }
                        EButton("Continuar", if (state.step == 1) state.canSave else state.canContinue,
                            Modifier.testTag(if (state.step == 1) "enrollment-save" else if (state.step == 4) "enrollment-submit" else "enrollment-next")) {
                            if (state.step == 1) model.save() else if (state.step < 4) model.step(state.step + 1) else model.submit()
                        }
                        if (state.step > 1) TextButton(onClick = { model.step(state.step - 1) }, enabled = !state.busy) { EText("Anterior", color = AccessRed) }
                    }
                    if (state.busy) CircularProgressIndicator(Modifier.semantics { contentDescription = "Confirmando" })
                    state.message?.let { EText(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("enrollment-error").semantics { liveRegion = LiveRegionMode.Polite }) }
                    if (!state.enrollment?.pendingOperations.isNullOrEmpty()) EText("Hay una carga pendiente de confirmar. Actualiza el estado antes de continuar.", 14)
                    if (state.canRetryUpload) TextButton(onClick = model::retryUpload, enabled = !state.busy) { EText("Comprobar carga y reintentar", color = AccessRed) }
                    TextButton(onClick = { model.load(); refreshAccount() }, enabled = !state.busy) { EText("Actualizar estado", color = AccessRed) }
                    Box(Modifier.fillMaxWidth().height(125.dp), contentAlignment = Alignment.BottomCenter) {
                        AccessFlame(Modifier.fillMaxSize().alpha(if (dark) .13f else .055f))
                        Image(painterResource(if (dark) R.drawable.wordmark_dark else R.drawable.wordmark_light), null, Modifier.padding(bottom = 24.dp).width(68.dp))
                    }
                }
            }
        }
    }
    preview?.let { evidence ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = if (dark) Color(0xff101216) else Color.White) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        EText(if (selected?.code == "selfie") "Revisa tu foto" else selected?.label ?: "Revisa tu documento", 24, modifier = Modifier.semantics { heading() })
                        if (evidence.mimeType.startsWith("image/")) {
                            val bitmap = remember(evidence) { android.graphics.BitmapFactory.decodeByteArray(evidence.bytes, 0, evidence.bytes.size,
                                android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }
                            bitmap?.let { Image(it.asImageBitmap(), "Vista previa", modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).border(3.dp, Color(0xff31d95b), RoundedCornerShape(20.dp))) }
                        } else EText(evidence.name)
                        EText("Revisa que el documento sea completo y legible antes de continuar.")
                        EButton(if (selected?.code == "selfie") "Confirmar selfie" else "Adjuntar archivo", true) { selected?.let { model.upload(evidence, it) }; preview = null }
                        TextButton(onClick = { preview = null; selected?.let(::pick) }) { EText(if (selected?.code == "selfie") "Repetir la selfie" else "Repetir selección", color = AccessRed) }
                    }
                }
            }
        }
    }
    if (state.submitted) Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = if (dark) Color(0xff101216) else Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.size(90.dp).background(Color(0xff31d95b), CircleShape), contentAlignment = Alignment.Center) { EText("✓", 64, color = Color.White) }
                    EText("Documentos enviados con éxito", 26, modifier = Modifier.semantics { heading() })
                    EText("Completaste el formulario de registro de documentos. La revisión tiene un tiempo estimado de 24 a 72 horas.")
                    EButton("Cerrar", true) { model.status(false); back() }
                    HorizontalDivider()
                    TextButton(onClick = { model.status(true) }) { EText("Ver estatus de validación de documentos", color = AccessRed) }
                }
            }
        }
    }
}
@Composable private fun EText(text: String, size: Int = 16, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Text(text, modifier, fontFamily = AccessFont, fontSize = size.sp, lineHeight = (size * 1.3).sp, textAlign = TextAlign.Center, color = color)
}
@Composable private fun EButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = AccessRed, contentColor = Color.White), modifier = modifier.widthIn(max = 330.dp).fillMaxWidth().heightIn(min = 48.dp)) { EText(text) }
}
/** Selección semántica, con un único objetivo accesible por fila. */
private fun Modifier.selectableWithoutIndication(selected: Boolean, enabled: Boolean, action: () -> Unit): Modifier =
    this.semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton }.clickable(enabled = enabled, onClick = action)
@Composable fun EnrollmentSummary(enrollment: Enrollment?) {
    EText(enrollment?.state?.label ?: "Tu expediente", 24)
    if (enrollment?.state == EnrollmentStatus.IN_REVIEW) EText("Revisión estimada de 24 a 72 horas. Aquí podrás consultar el resultado.")
    enrollment?.documents?.forEach { document ->
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(enrollment.requirements.firstOrNull { it.code == document.requirementCode }?.label ?: document.name, style = MaterialTheme.typography.titleSmall)
            Text("${document.state.label} · Versión ${document.version}")
            if (document.reason.isNotEmpty()) Text(document.reason, color = MaterialTheme.colorScheme.error)
        }
    }
}
