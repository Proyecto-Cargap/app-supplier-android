package mx.cargap.supplier.presentation

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import mx.cargap.supplier.R

internal val AccessRed = Color(0xffe91335)
internal val AccessFont = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold))

/** Vista de acceso independiente; conserva las acciones y el estado del MVVM anfitrión. */
@Composable internal fun LoginScreen(
    state: AccessState,
    signIn: (String, String) -> Unit,
    recovery: (() -> Unit)? = null,
    registration: (() -> Unit)? = null,
) {
    var email by remember { mutableStateOf("") }
    // La contraseña nunca se escribe en estado guardado ni en almacenamiento.
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val dark = isSystemInDarkTheme()
    val upper = if (dark) Color(0xff101216) else Color(0xfffffafb)
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    AccessSystemBars(dark)
    fun submit() {
        if (state.busy) return
        val supplied = password
        password = ""
        focus.clearFocus()
        signIn(email, supplied)
    }
    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(Color.White, Color.White.copy(alpha = 0.3f))) {
        BoxWithConstraints(Modifier.fillMaxSize().background(upper).imePadding()) {
            val contentWidth = minOf(maxWidth, 520.dp)
            val unit = contentWidth.value / 556f
            val bottom = maxOf(bottomInset, (24 * unit).dp)
            val panel = (158 * unit + 64 * unit + 96 + 54 * unit + 48 * unit + 96 + 14 * unit + maxOf(48f, 58 * unit) + 28 * unit + 96 * unit).dp + bottom
            val header = maxOf((180 * unit).dp, maxHeight - panel)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(header), contentAlignment = Alignment.Center) {
                    Image(painterResource(if (dark) R.drawable.wordmark_dark else R.drawable.wordmark_light),
                        contentDescription = "CARGAP", modifier = Modifier.width(contentWidth * 0.78f).offset(y = header * 0.15f))
                }
                AccessFlame(Modifier.fillMaxWidth().height((158 * unit).dp))
                Box(Modifier.fillMaxWidth().background(AccessRed), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.width(contentWidth).padding(horizontal = contentWidth * 0.05f)
                        .padding(top = (64 * unit).dp, bottom = bottom), horizontalAlignment = Alignment.CenterHorizontally) {
                        AccessTextField(email, { email = it }, "Correo", "Correo electrónico", "email", !state.busy, unit,
                            KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            KeyboardActions(onNext = { passwordFocus.requestFocus() }))
                        Spacer(Modifier.height((54 * unit).dp))
                        AccessTextField(password, { password = it }, "Contraseña", "Contraseña", "password", !state.busy, unit,
                            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                            KeyboardActions(onGo = { submit() }), Modifier.focusRequester(passwordFocus), PasswordVisualTransformation())
                        state.message?.let {
                            Text(it, color = Color.White, fontFamily = AccessFont, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                .testTag("error").semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        state.notice?.let {
                            Text(it, color = Color.White, fontFamily = AccessFont, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("notice"))
                        }
                        Spacer(Modifier.height((48 * unit).dp))
                        TextButton(onClick = { submit() }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("login"),
                            contentPadding = PaddingValues(0.dp), colors = loginTextColors()) {
                            if (state.busy) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (state.busy) "Ingresando…" else "Iniciar sesión", fontFamily = AccessFont,
                                fontWeight = FontWeight.Bold, fontSize = maxOf(28f, 42 * unit).sp, textAlign = TextAlign.Center,
                                lineHeight = maxOf(34f, 50 * unit).sp)
                        }
                        TextButton(onClick = { focus.clearFocus(); recovery?.invoke() }, enabled = !state.busy && recovery != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("recovery")
                                .semantics { if (recovery == null) stateDescription = "Disponible en una próxima etapa" },
                            contentPadding = PaddingValues(0.dp), colors = loginTextColors()) {
                            Text("¿Has olvidado tu contraseña?", fontFamily = AccessFont, fontSize = maxOf(16f, 22 * unit).sp,
                                lineHeight = maxOf(22f, 28 * unit).sp, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height((14 * unit).dp))
                        Button(onClick = { focus.clearFocus(); registration?.invoke() }, enabled = !state.busy && registration != null,
                            modifier = Modifier.width(contentWidth * 0.52f).heightIn(min = maxOf(48f, 58 * unit).dp)
                                .testTag("registration").semantics { if (registration == null) stateDescription = "Disponible en una próxima etapa" },
                            shape = CircleShape, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AccessRed,
                                disabledContainerColor = Color.White, disabledContentColor = AccessRed)) {
                            Text("Crear cuenta", fontFamily = AccessFont, fontSize = maxOf(18f, 24 * unit).sp,
                                lineHeight = maxOf(24f, 30 * unit).sp, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height((28 * unit).dp))
                        Image(painterResource(R.drawable.login_mark), contentDescription = null, modifier = Modifier.height((96 * unit).dp))
                    }
                }
            }
        }
    }
}

@Composable private fun loginTextColors() = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White)

@Composable internal fun AccessTextField(
    value: String, change: (String) -> Unit, placeholder: String, label: String, tag: String,
    enabled: Boolean, unit: Float, keyboard: KeyboardOptions, actions: KeyboardActions,
    modifier: Modifier = Modifier, transformation: VisualTransformation = VisualTransformation.None,
) {
    BasicTextField(value, change, modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .semantics { contentDescription = label }, enabled = enabled, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontFamily = AccessFont, fontSize = maxOf(18f, 24 * unit).sp),
        keyboardOptions = keyboard, keyboardActions = actions, visualTransformation = transformation,
        cursorBrush = SolidColor(Color.White), decorationBox = { field ->
            Column {
                Box(Modifier.fillMaxWidth().heightIn(min = 47.dp).padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.BottomStart) {
                    if (value.isEmpty()) Text(placeholder, color = Color.White, fontFamily = AccessFont, fontSize = maxOf(18f, 24 * unit).sp)
                    field()
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.7f), thickness = 1.dp)
            }
        })
}

/** Contorno vectorial del diseño, con coordenadas de referencia de 556 × 158. */
@Composable internal fun AccessFlame(modifier: Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(0f, 116f)
            cubicTo(12f, 76f, 34f, 44f, 67f, 27f)
            cubicTo(75f, 23f, 84f, 26f, 88f, 39f)
            cubicTo(106f, 94f, 139f, 145f, 189f, 151f)
            cubicTo(223f, 157f, 248f, 140f, 252f, 117f)
            cubicTo(260f, 87f, 228f, 52f, 191f, 20f)
            cubicTo(178f, 8f, 183f, 0f, 199f, 0f)
            cubicTo(270f, -5f, 318f, 8f, 358f, 39f)
            cubicTo(391f, 65f, 403f, 97f, 407f, 129f)
            cubicTo(410f, 147f, 424f, 158f, 439f, 156f)
            cubicTo(462f, 154f, 473f, 132f, 466f, 110f)
            cubicTo(461f, 94f, 454f, 78f, 447f, 65f)
            cubicTo(440f, 51f, 447f, 51f, 456f, 55f)
            cubicTo(504f, 69f, 537f, 103f, 556f, 142f)
            lineTo(556f, 158f); lineTo(0f, 158f); close()
        }
        scale(size.width / 556f, size.height / 158f, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawPath(path, AccessRed) }
    }
}

/** Restaura las barras del sistema al abandonar las vistas públicas. */
@Composable internal fun AccessSystemBars(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, dark) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = bars?.isAppearanceLightStatusBars
        val previousNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = !dark
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            previousStatus?.let { bars?.isAppearanceLightStatusBars = it }
            previousNavigation?.let { bars?.isAppearanceLightNavigationBars = it }
        }
    }
}
