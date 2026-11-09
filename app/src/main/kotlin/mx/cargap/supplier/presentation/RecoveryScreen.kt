package mx.cargap.supplier.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.cargap.supplier.R

/** Recuperación nativa; el repositorio existente conserva el contrato público por correo. */
@Composable internal fun RecoveryScreen(model: PublicAccessViewModel, back: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val dark = isSystemInDarkTheme()
    val upper = if (dark) Color(0xff101216) else Color(0xfffffafb)
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    AccessSystemBars(dark)
    DisposableEffect(model) { model.reset(); onDispose { model.reset() } }
    fun submit() {
        if (state.busy) return
        focus.clearFocus()
        model.recover(email)
    }
    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(Color.White, Color.White.copy(alpha = 0.3f))) {
        BoxWithConstraints(Modifier.fillMaxSize().background(upper).imePadding()) {
            val width = minOf(maxWidth, 520.dp)
            val unit = width.value / 556f
            val bottom = maxOf(bottomInset, (24 * unit).dp)
            val header = maxOf((150 * unit).dp, maxHeight - ((374 * unit + 288).dp + bottom))
            Column(Modifier.fillMaxSize().background(AccessRed).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(header).background(upper), contentAlignment = Alignment.Center) {
                    Image(painterResource(if (dark) R.drawable.wordmark_dark else R.drawable.wordmark_light),
                        contentDescription = "CARGAP", modifier = Modifier.width(width * 0.78f).offset(y = header * 0.15f))
                }
                AccessFlame(Modifier.fillMaxWidth().height((158 * unit).dp).background(upper))
                Box(Modifier.fillMaxWidth().background(AccessRed), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.width(width).padding(horizontal = width * 0.05f)
                        .padding(top = (40 * unit).dp, bottom = bottom), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("¿Has olvidado tu contraseña?", color = Color.White, fontFamily = AccessFont,
                            fontWeight = FontWeight.Bold, fontSize = maxOf(26f, 34 * unit).sp,
                            lineHeight = maxOf(32f, 40 * unit).sp, textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("recovery-title").semantics { heading() })
                        Spacer(Modifier.height((24 * unit).dp))
                        AccessTextField(email, { email = it }, "Correo", "Correo electrónico", "recovery-email", !state.busy, unit,
                            KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                            KeyboardActions(onSend = { submit() }))
                        state.message?.let {
                            Text(it, color = Color.White, fontFamily = AccessFont, fontSize = 16.sp, lineHeight = 22.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp).testTag("public-message")
                                    .semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        Spacer(Modifier.height((32 * unit).dp))
                        Button(onClick = { submit() }, enabled = !state.busy,
                            modifier = Modifier.width(width * 0.52f).heightIn(min = maxOf(48f, 58 * unit).dp).testTag("recovery-send"),
                            shape = CircleShape, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AccessRed,
                                disabledContainerColor = Color.White, disabledContentColor = AccessRed)) {
                            if (state.busy) { CircularProgressIndicator(Modifier.size(20.dp), color = AccessRed, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                            Text(if (state.busy) "Enviando…" else "Enviar", fontFamily = AccessFont,
                                fontSize = maxOf(18f, 24 * unit).sp, lineHeight = maxOf(24f, 30 * unit).sp, textAlign = TextAlign.Center)
                        }
                        TextButton(onClick = { focus.clearFocus(); back() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).testTag("recovery-back"),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Text("Volver al inicio de sesión", fontFamily = AccessFont, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height((24 * unit).dp))
                        Image(painterResource(R.drawable.login_mark), contentDescription = null, modifier = Modifier.height((96 * unit).dp))
                    }
                }
            }
        }
    }
}
