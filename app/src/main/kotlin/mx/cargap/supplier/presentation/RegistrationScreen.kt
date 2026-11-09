package mx.cargap.supplier.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.cargap.supplier.R
import mx.cargap.supplier.domain.Actor

/** Asistente nativo; no comparte composición con el login ni con el expediente. */
@Composable internal fun RegistrationScreen(model: RegistrationViewModel, exit: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val dark = isSystemInDarkTheme()
    val surface = if (dark) Color(0xff101216) else Color.White
    val ink = if (dark) Color.White else Color.Black
    val requests = remember { listOf("first-name", "paternal", "maternal", "preferred", "email", "prefix", "phone", "code").associateWith { FocusRequester() } }
    fun advance() { if (state.canAdvance) { focus.clearFocus(); model.next() } }
    fun back() {
        focus.clearFocus()
        if (state.step == RegistrationStep.EMAIL || state.step == RegistrationStep.PHONE) model.back() else exit()
    }
    DisposableEffect(model) { onDispose { model.reset() } }
    LaunchedEffect(state.step) { model.registerOnce() }
    BackHandler { back() }
    AccessSystemBars(dark || state.step == RegistrationStep.CONFIRMATION)
    CompositionLocalProvider(LocalContentColor provides ink) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (state.step == RegistrationStep.CONFIRMATION) Color(0xff262626) else surface).safeDrawingPadding().imePadding()) {
            val width = minOf(maxWidth, 520.dp)
            val height = maxHeight
            val wordmark = if (dark) R.drawable.wordmark_dark else R.drawable.wordmark_light
            key(state.step) {
                when(state.step) {
                    RegistrationStep.CONFIRMATION -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Column(Modifier.width(width * 0.86f).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(26.dp))
                            .background(surface).padding(horizontal = 22.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
                            if (state.busy) {
                                CircularProgressIndicator(color = AccessRed)
                                RegText("Creando tu cuenta…", modifier = Modifier.testTag("registration-loading"))
                            } else if (state.created) {
                                Canvas(Modifier.size(114.dp)) {
                                    drawCircle(Color(0xff30de59))
                                    val path = Path().apply { moveTo(size.width * 0.25f, size.height * 0.52f); lineTo(size.width * 0.42f, size.height * 0.69f); lineTo(size.width * 0.76f, size.height * 0.35f) }
                                    drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                                }
                                RegText("Cuenta registrada", 30, Modifier.testTag("registration-success").semantics { heading(); liveRegion = LiveRegionMode.Polite })
                                RegButton("Continuar", "registration-continue", modifier = Modifier.width(width * 0.64f), action = model::welcome)
                            } else state.message?.let {
                                RegText(it, modifier = Modifier.testTag("registration-error").semantics { liveRegion = LiveRegionMode.Polite })
                                RegButton("Volver al inicio de sesión", "registration-exit", action = exit)
                            }
                        }
                    }
                    RegistrationStep.WELCOME -> AccountWelcomeContent(state.displayName, model.actor, true, width, height, exit)
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.width(width).heightIn(min = height), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth()) {
                                TextButton(onClick = { back() }, modifier = Modifier.size(48.dp).testTag("registration-back").semantics { contentDescription = "Volver" }, contentPadding = PaddingValues(0.dp)) {
                                    Text("‹", color = AccessRed, fontSize = 38.sp)
                                }
                            }
                            Spacer(Modifier.height(if (height > 550.dp) 46.dp else 12.dp))
                            Image(painterResource(wordmark), "CARGAP", Modifier.width(width * 0.76f))
                            RegText("Crea tu cuenta", 32, Modifier.padding(top = 8.dp).testTag("registration-title").semantics { heading() })
                            Column(Modifier.fillMaxWidth().padding(horizontal = width * 0.08f).padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                RegText(when(state.step) {
                                    RegistrationStep.NAMES -> "Ingresa tu nombre verdadero"
                                    RegistrationStep.EMAIL -> "Ingresa tu correo electrónico de contacto.\nEste no será público."
                                    else -> "Ingresa tu número de celular de contacto.\nEste no será público."
                                }, modifier = Modifier.padding(vertical = 8.dp))
                                val d = state.draft
                                if (state.step == RegistrationStep.NAMES) {
                                    RegField("Nombre", "first-name", d.firstName, { model.edit(d.copy(firstName = it)) }, requests.getValue("first-name")) { requests.getValue("paternal").requestFocus() }
                                    RegField("Apellido paterno", "paternal", d.lastNamePaternal, { model.edit(d.copy(lastNamePaternal = it)) }, requests.getValue("paternal")) { requests.getValue("maternal").requestFocus() }
                                    RegField("Apellido materno (opcional)", "maternal", d.lastNameMaternal, { model.edit(d.copy(lastNameMaternal = it)) }, requests.getValue("maternal")) { requests.getValue("preferred").requestFocus() }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        RegField("Nombre preferido o seudónimo", "preferred", d.preferredName, { model.edit(d.copy(preferredName = it)) }, requests.getValue("preferred")) { advance() }
                                        RegHelper("Solo tú podrás verlo", Modifier.padding(top = 6.dp))
                                    }
                                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { disabled() }.testTag("registration-legal"), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = false, enabled = false, onCheckedChange = null)
                                        RegText("Acepto los Términos y condiciones y la Política de Privacidad", 12,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                                    }
                                } else if (state.step == RegistrationStep.EMAIL) {
                                    RegField("Correo electrónico", "email", d.email, { model.edit(d.copy(email = it)) }, requests.getValue("email"), KeyboardType.Email) { advance() }
                                    RegHelper("Te enviaremos un correo de bienvenida y con este correo nos mantendremos en comunicación. Podrás desactivarlos cuando quieras.")
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RegField("Prefijo", "prefix", d.countryCode, { model.edit(d.copy(countryCode = it)) }, requests.getValue("prefix"), KeyboardType.Phone, Modifier.width(80.dp)) { requests.getValue("phone").requestFocus() }
                                        RegField("Número de celular", "phone", d.phone, { model.edit(d.copy(phone = it)) }, requests.getValue("phone"), KeyboardType.Phone, Modifier.weight(1f)) { requests.getValue("code").requestFocus() }
                                    }
                                    RegHelper("Te enviaremos un código de verificación vía SMS.")
                                    RegField("Confirma tu identidad", "code", state.code, model::code, requests.getValue("code"), KeyboardType.Number) { advance() }
                                    RegHelper("Ingresa el código de 5 dígitos")
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        RegHelper("¿No recibiste el código?")
                                        TextButton(onClick = { model.resend(); requests.getValue("code").requestFocus() }, modifier = Modifier.testTag("registration-resend")) {
                                            RegText("Toca aquí para reenviar un código", 13, color = Color(0xff1675ff))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f).heightIn(min = 28.dp))
                            RegButton("Siguiente", "registration-next", state.canAdvance, Modifier.width(width * 0.64f).padding(bottom = 22.dp)) { advance() }
                        }
                    }
                }
            }
        }
    }
}

/** Cuenta autenticada sin asociación: bienvenida y cierre de sesión, sin destinos operativos. */
@Composable internal fun PendingAccountScreen(name: String, actor: Actor, notice: String?, exit: () -> Unit, complete: (() -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
    AccessSystemBars(dark)
    CompositionLocalProvider(LocalContentColor provides if (dark) Color.White else Color.Black) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (dark) Color(0xff101216) else Color.White).safeDrawingPadding().testTag("onboarding")) {
            AccountWelcomeContent(name, actor, false, minOf(maxWidth, 520.dp), maxHeight, exit, notice, complete)
        }
    }
}
@Composable private fun AccountWelcomeContent(name: String, actor: Actor, awaitingActivation: Boolean,
    width: Dp, height: Dp, exit: () -> Unit, notice: String? = null, complete: (() -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
    val wordmark = if (dark) R.drawable.wordmark_dark else R.drawable.wordmark_light
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.width(width).heightIn(min = height).padding(horizontal = width * 0.05f).padding(top = 32.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
            val mark = if (dark) R.drawable.registration_mark_dark else R.drawable.registration_mark_light
            if (LocalDensity.current.fontScale > 1.3f) {
                Image(painterResource(mark), null, Modifier.size(width * 0.3f))
                Greeting(name)
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(mark), null, Modifier.size(width * 0.32f))
                Greeting(name, Modifier.weight(1f))
            }
            RegistrationMap(Modifier.fillMaxWidth().height(width * 0.62f).clip(RoundedCornerShape(16.dp)))
            RegText(if (actor == Actor.CUSTOMER) "Para solicitar tu primer flete es necesario llenar un formulario para completar tu cuenta."
                else "Para realizar tu primer flete es necesario llenar un formulario para completar tu cuenta.", modifier = Modifier.padding(top = 12.dp))
            RegButton("Completar cuenta", "registration-complete", enabled = !awaitingActivation && complete != null, modifier = Modifier.width(width * 0.64f)) { complete?.invoke() }
            if (awaitingActivation) RegText("Revisa tu correo para activar tu cuenta y crear tu contraseña.", 14, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (notice != null) RegText(notice, 14)
            TextButton(onClick = exit, modifier = Modifier.testTag(if (awaitingActivation) "registration-exit" else "logout")) { RegText(if (awaitingActivation) "Volver al inicio de sesión" else "Cerrar sesión", color = AccessRed) }
            Spacer(Modifier.weight(1f))
            Image(painterResource(wordmark), null, Modifier.width(70.dp))
        }
    }
}
@Composable private fun RegText(text: String, size: Int = 17, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Text(text, modifier, color = color, fontFamily = AccessFont, fontSize = size.sp, lineHeight = (size * 1.25).sp, textAlign = TextAlign.Center)
}
@Composable private fun RegHelper(text: String, modifier: Modifier = Modifier) {
    RegText(text, 12, modifier, MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun RegField(label: String, id: String, value: String, change: (String) -> Unit, focus: FocusRequester,
    keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier, next: () -> Unit) {
    val ink = LocalContentColor.current
    BasicTextField(value, change, modifier.fillMaxWidth().focusRequester(focus).testTag("registration-$id").semantics { contentDescription = label },
        singleLine = true, textStyle = TextStyle(color = ink, fontFamily = AccessFont, fontSize = 17.sp), cursorBrush = SolidColor(AccessRed),
        keyboardOptions = KeyboardOptions(capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false, keyboardType = keyboard, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { next() }),
        decorationBox = { inner ->
            Column {
                Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) RegText(label, color = ink)
                    inner()
                }
                HorizontalDivider(color = ink.copy(alpha = 0.5f), thickness = 0.7.dp)
            }
        })
}
@Composable private fun RegButton(label: String, id: String, enabled: Boolean = true, modifier: Modifier = Modifier, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 48.dp).testTag(id), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = AccessRed, contentColor = Color.White,
            disabledContainerColor = if (isSystemInDarkTheme()) Color(0xff3b3b3b) else Color(0xffdbdbdb),
            disabledContentColor = if (isSystemInDarkTheme()) Color.LightGray else Color(0xff333333)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { RegText(label) }
}
@Composable private fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(modifier.testTag("registration-welcome").semantics(mergeDescendants = true) {}) {
        Text("Hola\n$name", fontFamily = AccessFont, fontSize = 44.sp, lineHeight = 46.sp)
        Text("Bienvenido", fontFamily = AccessFont, fontSize = 27.sp, lineHeight = 32.sp)
    }
}
/** Ilustración vectorial sin ubicación ni conexión a proveedores de mapas. */
@Composable private fun RegistrationMap(modifier: Modifier) {
    Box(modifier.background(Color.Black).semantics(mergeDescendants = true) { contentDescription = "Ilustración de un mapa" }) {
        Canvas(Modifier.matchParentSize()) {
            val lines = listOf(listOf(0.03f to 0f,0.18f to 0.25f,0.03f to 0.52f,0.03f to 1f),
                listOf(0f to 0.2f,0.33f to 0.6f,0.33f to 1f),listOf(0.25f to 0f,0.25f to 1f),
                listOf(0.5f to 0f,0.6f to 0.24f,0.6f to 0.8f),listOf(0.7f to 0f,0.82f to 0.32f,0.82f to 0.65f,0.74f to 1f),
                listOf(0f to 0.52f,1f to 0.52f),listOf(0.33f to 0.36f,1f to 0.36f),
                listOf(0.33f to 0.65f,1f to 0.65f),listOf(0.03f to 0.83f,1f to 0.83f),listOf(0.08f to 0.15f,0.45f to 0f,0.45f to 1f))
            for (line in lines) for ((a,b) in line.zipWithNext()) drawLine(Color(0xff5c5c5c), Offset(a.first*size.width,a.second*size.height),Offset(b.first*size.width,b.second*size.height),strokeWidth = 3.dp.toPx())
        }
        RegText("Transportista", 12, Modifier.align(Alignment.CenterStart).padding(start = 28.dp), Color.White)
        RegText("Tu ubicación", 12, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).offset(y = (-24).dp), Color.White)
    }
}
