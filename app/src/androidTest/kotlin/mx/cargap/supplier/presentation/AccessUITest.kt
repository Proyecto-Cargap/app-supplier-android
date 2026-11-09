package mx.cargap.supplier.presentation

import mx.cargap.supplier.composition.AccessModule
import mx.cargap.supplier.data.AndroidEvidenceAcquisition
import mx.cargap.supplier.application.EnrollmentUseCases
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import mx.cargap.supplier.application.RegistrationUseCases
import mx.cargap.supplier.application.PublicAccessUseCases
import mx.cargap.supplier.application.AccessUseCases
import mx.cargap.supplier.domain.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Fixtures exclusivas del APK de pruebas; no se incluyen en la aplicación. */
private class FixtureSessions(private val pending: Boolean = false) : SessionRepository {
    var session: SessionAccess? = null
    override suspend fun signIn(user: String, password: String): SessionAccess {
        if (password != "fixture-password") throw AccessException(Failure.CREDENTIALS)
        return SessionAccess(Session("S", "U", "Ana Prueba", user, "WS", "Pruebas", if (pending) "" else "ACTOR", if (pending) "" else "Empresa de prueba", 4_000_000_000, requiresAccountCompletion = pending, greetingName = "Ana"), true).also { session = it }
    }
    override suspend fun restore() = session
    override suspend fun clear() { session = null }
    override suspend fun signOut(): Boolean { session = null; return true }
}
private class FixtureEnrollment : EnrollmentRepository {
    private var current = Enrollment("", 0, null, EnrollmentStatus.NOT_STARTED, "", emptyList(), emptyList())
    override suspend fun load() = current
    override suspend fun save(type: PersonType, tradeName: String, revision: Int): Enrollment {
        if (current.personType != null) return current
        val codes = listOf("fiscal", "compliance", "address", "identityFront", "identityBack") + (if (type == PersonType.LEGAL) listOf("incorporation") else emptyList()) + "selfie"
        val labels = mapOf("identityFront" to "Identificación oficial: frente", "identityBack" to "Identificación oficial: reverso", "fiscal" to "Constancia de situación fiscal", "compliance" to "Opinión de cumplimiento", "address" to "Comprobante de domicilio", "incorporation" to "Acta constitutiva", "selfie" to "Selfie")
        current = Enrollment("E", revision + 1, type, EnrollmentStatus.DRAFT, "",
            codes.map { DocumentRequirement(it, labels.getValue(it), if (it == "selfie") 4 else if (it in listOf("fiscal", "compliance")) 2 else 3,
                if (it == "selfie") listOf("image/jpeg", "image/png") else if (it.startsWith("identity")) listOf("application/pdf", "image/jpeg", "image/png") else listOf("application/pdf"), 10485760, "Documento vigente y legible.") }, emptyList(), tradeName = tradeName)
        return current
    }
    fun completeStep(step: Int) {
        val documents = current.requirements.filter { it.step == step }.map { EnrollmentDocument(it.code, java.util.UUID.randomUUID().toString(), 1, "documento-de-prueba.pdf", DocumentStatus.UPLOADED, "") }
        current = current.copy(revision = current.revision + 1, documents = current.documents.filter { old -> documents.none { it.requirementCode == old.requirementCode } } + documents)
    }
    override suspend fun upload(evidence: Evidence, requirement: String, revision: Int): Enrollment {
        current = current.copy(revision = current.revision + 1, documents = current.documents.filter { it.requirementCode != requirement } + EnrollmentDocument(requirement, evidence.operationCode, 1, evidence.name, DocumentStatus.UPLOADED, ""))
        return current
    }
    override suspend fun submit(revision: Int): Enrollment {
        check(current.canSubmit)
        current = current.copy(revision = current.revision + 1, state = EnrollmentStatus.IN_REVIEW, documents = current.documents.map { it.copy(state = DocumentStatus.IN_REVIEW) })
        return current
    }
}
private class FixturePublic : PublicAccessRepository {
    override suspend fun recoverPassword(email: String) {}
}
private class FixtureRegistration : RegistrationRepository {
    var calls = 0
    override suspend fun register(draft: RegistrationDraft, actor: Actor) {
        calls++
        kotlinx.coroutines.delay(400)
        if (draft.email == "failure@example.invalid") throw AccessException(Failure.UNAVAILABLE)
    }
}
class AccessUITest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixtureEnrollment: FixtureEnrollment
    private fun start(pending: Boolean = false) {
        fixtureEnrollment = FixtureEnrollment()
        val model = AccessViewModel(AccessUseCases(FixtureSessions(pending), AccessModule.configuration()), Actor.SUPPLIER)
        val publicAccess = PublicAccessViewModel(PublicAccessUseCases(FixturePublic()))
        val registration = RegistrationViewModel(RegistrationUseCases(FixtureRegistration()), Actor.SUPPLIER)
        val enrollment = EnrollmentViewModel(EnrollmentUseCases(fixtureEnrollment))
        compose.setContent { AccessRoot(model, publicAccess, registration, enrollment, AndroidEvidenceAcquisition(InstrumentationRegistry.getInstrumentation().targetContext)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("email").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun login(password: String = "fixture-password") {
        compose.onNodeWithTag("email").performTextInput("persona@example.invalid")
        compose.onNodeWithTag("password").performTextInput(password)
        compose.onNodeWithTag("password").performImeAction()
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it) } ?: context.getExternalFilesDir(null)!!
        directory.mkdirs()
        val file = File(directory, "$name.png")
        compose.waitForIdle()
        Thread.sleep(400) // Espera a que termine la animación nativa del teclado.
        compose.waitForIdle()
        // La captura del dispositivo incluye las barras del sistema y el teclado IME.
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun activatedAccountWithoutAssociationShowsWelcomeAndLogout() {
        start(pending = true); login()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("onboarding").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("registration-welcome").assertExists()
        compose.onNodeWithTag("registration-complete").assertIsEnabled()
        compose.onNodeWithTag("home").assertDoesNotExist()
        compose.onNodeWithTag("account").assertDoesNotExist()
        compose.onNodeWithTag("enrollment").assertDoesNotExist()
        compose.onNodeWithText("Revisa tu correo para activar tu cuenta y crear tu contraseña.").assertDoesNotExist()
        capture("onboarding")
        compose.onNodeWithTag("logout").performScrollTo().assertIsDisplayed()
        capture("onboarding-bottom")
        compose.onNodeWithTag("logout").performClick()
        compose.onNodeWithTag("login").assertExists()
        compose.onNodeWithTag("registration-welcome").assertDoesNotExist()
        compose.onNodeWithText("Volver").assertDoesNotExist()
    }
    @Test fun loginAccountAndLogoutClearPrivateNavigation() {
        start(); capture("login"); login()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
        capture("home")
        compose.onNodeWithTag("account").performScrollTo().performClick()
        compose.onNodeWithText("persona@example.invalid").assertExists()
        compose.onNodeWithTag("logout").performScrollTo().performClick()
        compose.onNodeWithTag("login").assertExists()
        compose.onNodeWithText("persona@example.invalid").assertDoesNotExist()
        compose.onNodeWithText("Volver").assertDoesNotExist()
    }
    /** Recorre la cámara frontal real del emulador, cancelación, repetición y confirmación antes de subir. */
    @Test fun selfieRequiresNativeCaptureAndExplicitConfirmation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, android.Manifest.permission.CAMERA)
        start(pending = true); login()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("onboarding").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("registration-complete").performScrollTo().performClick()
        compose.onNodeWithTag("person-natural_person").performClick()
        compose.onNodeWithTag("enrollment-save").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("upload-fiscal").fetchSemanticsNodes().isNotEmpty() }
        fixtureEnrollment.completeStep(2); fixtureEnrollment.completeStep(3)
        compose.onNodeWithText("Actualizar estado").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("upload-selfie").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("upload-selfie").performScrollTo().performClick()
        nativeCameraAction("Cancelar")
        compose.onNodeWithTag("enrollment-submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("upload-selfie").performScrollTo().performClick()
        nativeCameraAction("Tomar selfie")
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText("Revisa tu foto").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        org.junit.Assert.assertFalse(kotlinx.coroutines.runBlocking { fixtureEnrollment.load() }.documents.any { it.requirementCode == "selfie" })
        capture("enrollment-selfie-preview")
        compose.onNodeWithText("Repetir la selfie").performScrollTo().performClick()
        nativeCameraAction("Tomar selfie")
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText("Confirmar selfie").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.onNodeWithText("Confirmar selfie").performScrollTo().performClick()
        compose.waitUntil(10_000) { kotlinx.coroutines.runBlocking { fixtureEnrollment.load() }.documents.any { it.requirementCode == "selfie" } }
        compose.onNodeWithTag("enrollment-submit").performScrollTo().assertIsEnabled()
        capture("enrollment-selfie-confirmed")
    }
    /** Usa accesibilidad nativa porque la actividad de cámara no pertenece al árbol de Compose. */
    private fun nativeCameraAction(label: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var target: android.view.accessibility.AccessibilityNodeInfo? = null
        compose.waitUntil(20_000) {
            target = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString()?.equals(label, ignoreCase = true) == true && it.isEnabled && it.isClickable }
            target != null
        }
        org.junit.Assert.assertTrue(target!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
    }
    @Test fun enrollmentPersistsRequiredStepsAndConfirmsBeforeSubmission() {
        start(pending = true); login()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("registration-complete").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("registration-complete").performScrollTo().performClick()
        compose.onNodeWithTag("enrollment-save").performScrollTo().assertIsNotEnabled()
        capture("enrollment-step1")
        compose.onNodeWithText("Persona moral").performScrollTo().performClick()
        compose.onNodeWithTag("enrollment-save").assertIsNotEnabled()
        compose.onNodeWithTag("enrollment-trade-name").performScrollTo().performTextInput("Empresa de prueba")
        capture("enrollment-keyboard")
        compose.onNodeWithTag("enrollment-save").performScrollTo().performClick()
        compose.onNodeWithTag("upload-fiscal").assertExists()
        capture("enrollment-step2")
        compose.onNodeWithTag("enrollment-next").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { fixtureEnrollment.completeStep(2) }
        compose.onNodeWithText("Actualizar estado").performScrollTo().performClick()
        compose.onNodeWithTag("upload-identityFront").assertExists()
        capture("enrollment-step3")
        compose.onNodeWithTag("upload-incorporation").performScrollTo().assertExists()
        compose.runOnIdle { fixtureEnrollment.completeStep(3) }
        compose.onNodeWithText("Actualizar estado").performScrollTo().performClick()
        compose.onNodeWithTag("upload-selfie").assertExists()
        compose.onNodeWithTag("enrollment-submit").performScrollTo().assertIsNotEnabled()
        capture("enrollment-step4")
        // La cámara nativa tiene pruebas separadas; esta fixture acredita el envío y sus estados.
        compose.runOnIdle { fixtureEnrollment.completeStep(4) }
        compose.onNodeWithText("Actualizar estado").performScrollTo().performClick()
        compose.onNodeWithTag("enrollment-submit").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Documentos enviados con éxito").assertExists()
        capture("enrollment-success")
        compose.onNodeWithText("Ver estatus de validación de documentos").performScrollTo().assertIsDisplayed()
        capture("enrollment-success-actions")
        compose.onNodeWithText("Ver estatus de validación de documentos").performClick()
        compose.onNodeWithText("Documentos en revisión").assertExists()
        capture("enrollment-status")
    }
    @Test fun invalidCredentialsRemainOnLogin() {
        start(); login("invalid")
        compose.onNodeWithTag("error").assertExists()
        compose.onNodeWithTag("home").assertDoesNotExist()
    }
    @Test fun loginControlsRemainReachableWithKeyboard() {
        start()
        compose.onNodeWithTag("registration").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("recovery").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("email").performScrollTo().performClick()
        compose.onNodeWithTag("email").performTextInput("persona@example.invalid")
        compose.onNodeWithTag("email").performImeAction()
        compose.onNodeWithTag("password").assertIsFocused()
        capture("login-keyboard")
        compose.onNodeWithTag("password").performTextInput("invalid")
        compose.onNodeWithTag("password").performImeAction()
        compose.onNodeWithTag("error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("password").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("registration").performScrollTo().assertIsDisplayed()
    }

    @Test fun recoveryValidationFeedbackAndReturn() {
        start()
        compose.onNodeWithTag("recovery").performScrollTo().performClick()
        compose.onNodeWithTag("recovery-email").assertExists()
        capture("recovery")
        compose.onNodeWithTag("recovery-send").performScrollTo().performClick()
        compose.onNodeWithTag("public-message").assertTextEquals("Escribe un correo válido.")
        compose.onNodeWithTag("recovery-email").performScrollTo().performTextInput("persona@example.invalid")
        compose.onNodeWithTag("recovery-email").performImeAction()
        compose.onNodeWithTag("public-message").performScrollTo().assertTextEquals("Si el correo corresponde a una cuenta, recibirás un enlace para restablecer tu contraseña.")
        capture("recovery-confirmation")
        compose.onNodeWithTag("recovery-back").performScrollTo().performClick()
        compose.onNodeWithTag("email").assertExists()
        compose.onNodeWithTag("recovery").performScrollTo().performClick()
        compose.onNodeWithTag("public-message").assertDoesNotExist()
    }

    private fun fillRegistration(email: String) {
        compose.onNodeWithTag("registration").performScrollTo().performClick()
        compose.onNodeWithTag("registration-next").assertIsNotEnabled()
        capture("registration-names")
        for ((id, value) in listOf("first-name" to "Ana", "paternal" to "Pérez", "maternal" to "López", "preferred" to "Anita"))
            compose.onNodeWithTag("registration-$id").performScrollTo().performClick().performTextInput(value)
        capture("registration-names-keyboard")
        compose.onNodeWithTag("registration-next").performScrollTo().performClick()
        capture("registration-email")
        compose.onNodeWithTag("registration-email").performScrollTo().performTextInput(email)
        compose.onNodeWithTag("registration-email").performImeAction()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("registration-phone").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("registration-phone").performScrollTo().performClick().performTextInput("5512345678")
        compose.onNodeWithTag("registration-code").performScrollTo().performClick().performTextInput("1234")
        compose.onNodeWithTag("registration-next").assertIsNotEnabled()
        compose.onNodeWithTag("registration-code").performTextInput("5")
        compose.onNodeWithTag("registration-resend").performScrollTo().performClick()
        compose.onNodeWithTag("registration-code").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("registration-code").performScrollTo().performTextInput("54321")
        capture("registration-phone-keyboard")
        compose.onNodeWithTag("registration-next").performScrollTo().performClick()
    }
    @Test fun registrationFlowPreservesFieldsAndReturnsToLogin() {
        start(); fillRegistration("registro@example.invalid")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("registration-success").fetchSemanticsNodes().isNotEmpty() }
        capture("registration-confirmed")
        compose.onNodeWithTag("registration-continue").performScrollTo().performClick()
        compose.onNodeWithTag("registration-welcome").assertExists()
        compose.onNodeWithTag("registration-complete").assertIsNotEnabled()
        capture("registration-welcome")
        compose.onNodeWithTag("registration-exit").performScrollTo().performClick()
        compose.onNodeWithTag("email").assertExists()
        compose.onNodeWithTag("registration").performScrollTo().performClick()
        compose.onNodeWithTag("registration-first-name").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }
    @Test fun registrationFailureNeverShowsConfirmation() {
        start(); fillRegistration("failure@example.invalid")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("registration-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("registration-success").assertDoesNotExist()
        capture("registration-error")
        compose.onNodeWithTag("registration-exit").performScrollTo().performClick()
        compose.onNodeWithTag("email").assertExists()
    }

}
