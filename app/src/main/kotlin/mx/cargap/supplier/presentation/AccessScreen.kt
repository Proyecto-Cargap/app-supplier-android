package mx.cargap.supplier.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import mx.cargap.supplier.R
import mx.cargap.supplier.domain.*

/** Grafo nativo. El cambio de contexto elimina destinos privados y sus formularios. */
@Composable fun AccessRoot(model: AccessViewModel, publicAccess: PublicAccessViewModel? = null, registration: RegistrationViewModel? = null, enrollment: EnrollmentViewModel? = null, acquisition: EvidenceAcquisition? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    enrollment?.let { flow ->
        val value by flow.state.collectAsStateWithLifecycle()
        LaunchedEffect(value.requiresSignIn) { if (value.requiresSignIn) model.invalidateSession() }
    }
    val nav = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(model) { model.start() }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) model.resume() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.phase) {
        if (state.phase !in listOf(Phase.HOME, Phase.ONBOARDING)) { enrollment?.reset(); acquisition?.clear() }
        nav.navigate(state.phase.name) { popUpTo(nav.graph.id) { inclusive = false }; launchSingleTop = true }
    }
    CargapTheme(state.configuration) {
        Surface(Modifier.fillMaxSize()) {
            NavHost(navController = nav, startDestination = Phase.LOADING.name) {
                composable(Phase.LOADING.name) { Shell(model.actor, state.configuration?.title ?: "CARGAP") { CircularProgressIndicator(); Text("Preparando tu acceso…") } }
                composable("enrollment") {
                    if (state.phase in listOf(Phase.HOME, Phase.ONBOARDING) && enrollment != null) EnrollmentScreen(enrollment, acquisition, model::resume) { nav.popBackStack() }
                }
                composable(Phase.LOGIN.name) { LoginScreen(state, model::signIn, if (publicAccess != null) ({ nav.navigate("recovery") }) else null, if (registration != null) ({ nav.navigate("registration") }) else null) }
                composable("registration") { registration?.let { RegistrationScreen(it) { nav.popBackStack() } } }
                composable("recovery") { publicAccess?.let { RecoveryScreen(it) { nav.popBackStack() } } }
                composable(Phase.ONBOARDING.name) {
                    if (state.phase == Phase.ONBOARDING) state.access?.session?.let {
                        PendingAccountScreen(it.greetingName, model.actor, state.notice, model::signOut, complete = { nav.navigate("enrollment") })
                    }
                }
                composable(Phase.BLOCKED.name) {
                    Shell(model.actor, state.configuration?.title ?: "CARGAP") {
                        Text("No pudimos conectar", style = MaterialTheme.typography.headlineMedium)
                        Feedback(state)
                        Button(onClick = model::retry, enabled = !state.busy, modifier = Modifier.testTag("retry")) { Text("Reintentar") }
                        if (state.configuration != null) TextButton(onClick = model::signOut) { Text("Volver al acceso") }
                    }
                }
                composable(Phase.HOME.name) {
                    Shell(model.actor, state.configuration?.title ?: "CARGAP") {
                        state.access?.session?.let { session ->
                            Text("Hola, ${session.name}", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("home"))
                            Text("Tu cuenta está conectada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(session.associationName, style = MaterialTheme.typography.titleLarge)
                                    Detail("Espacio de trabajo", session.workspaceName)
                                }
                            }
                            Feedback(state)
                            OutlinedButton(onClick = { nav.navigate("account") }, modifier = Modifier.fillMaxWidth().testTag("account")) { Text("Mi cuenta") }
                            Logout(state, model::signOut)
                        }
                    }
                }
                composable("account") {
                    Shell(model.actor, state.configuration?.title ?: "CARGAP") {
                        if (state.phase == Phase.HOME) state.access?.session?.let { session ->
                            TextButton(onClick = { nav.popBackStack() }) { Text("Volver") }
                            Text("Mi cuenta", style = MaterialTheme.typography.headlineLarge)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Detail("Nombre", session.name); Detail("Correo electrónico", session.email)
                                    Detail(if (model.actor == Actor.CUSTOMER) "Cliente" else "Transportista", session.associationName)
                                    Detail("Identificador", session.associationCode); Detail("Espacio de trabajo", session.workspaceName)
                                }
                            }
                            Feedback(state); Logout(state, model::signOut)
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun Shell(actor: Actor, title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.height(12.dp))
            Image(painterResource(if (isSystemInDarkTheme()) R.drawable.wordmark_dark else R.drawable.wordmark_light),
                contentDescription = title, modifier = Modifier.width(192.dp).height(48.dp))
            Text(actor.label.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
            content()
            Spacer(Modifier.height(12.dp))
            Text("Tu cuenta, siempre contigo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun Feedback(state: AccessState) {
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
    state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("notice")) }
}
@Composable private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable private fun Logout(state: AccessState, signOut: () -> Unit) {
    TextButton(onClick = signOut, enabled = !state.busy, modifier = Modifier.testTag("logout")) { Text("Cerrar sesión") }
}
@Composable private fun CargapTheme(config: PlatformConfiguration?, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    fun color(value: String?, fallback: Long) = value?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color(fallback)
    val primary = if (dark) color(config?.primary?.get("400"), 0xfff95e79) else color(config?.primary?.get("600"), 0xffc91934)
    val scheme = if (dark) darkColorScheme(primary = primary, onPrimary = Color(0xff101216),
        background = color(config?.surface?.get("950"), 0xff101216), surface = color(config?.surface?.get("950"), 0xff101216),
        surfaceVariant = color(config?.surface?.get("900"), 0xff191c22))
    else lightColorScheme(primary = primary, onPrimary = Color.White,
        background = color(config?.surface?.get("50"), 0xfffafafb), surface = color(config?.surface?.get("50"), 0xfffafafb),
        surfaceVariant = color(config?.surface?.get("0"), 0xffffffff))
    MaterialTheme(colorScheme = scheme, content = content)
}
