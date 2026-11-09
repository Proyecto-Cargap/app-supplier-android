package mx.cargap.supplier.composition

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import mx.cargap.supplier.BuildConfig
import mx.cargap.supplier.domain.*
import mx.cargap.supplier.application.RegistrationUseCases
import mx.cargap.supplier.application.PublicAccessUseCases
import mx.cargap.supplier.application.EnrollmentUseCases
import mx.cargap.supplier.application.AccessUseCases
import mx.cargap.supplier.data.*

/** Único punto de unión entre contratos y adaptadores de esta aplicación. */
@HiltAndroidApp class CargapApplication : Application()
@Module @InstallIn(SingletonComponent::class)
object AccessModule {
    @Provides @Singleton fun registrationRepository(gateway: GatewayTransport): RegistrationRepository = APIRegistrationRepository(gateway)
    @Provides fun registrationCases(repository: RegistrationRepository) = RegistrationUseCases(repository)
    @Provides @Singleton fun publicRepository(gateway: GatewayTransport): PublicAccessRepository = APIPublicAccessRepository(gateway)
    @Provides fun publicCases(repository: PublicAccessRepository) = PublicAccessUseCases(repository)
    @Provides fun actor(): Actor = Actor.SUPPLIER
    @Provides @Singleton fun gateway(): GatewayTransport = OkHttpGateway(BuildConfig.API_BASE_URL, BuildConfig.ALLOW_LOCAL_HTTP)
    @Provides @Singleton fun store(@ApplicationContext context: Context): SecureSessionStore =
        AndroidSecureSessionStore(context, BuildConfig.APPLICATION_ID + "." + BuildConfig.API_BASE_URL)
    @Provides @Singleton fun sessions(gateway: GatewayTransport, store: SecureSessionStore, actor: Actor): APISessionRepository =
        APISessionRepository(gateway, store, actor)
    @Provides fun sessionPort(sessions: APISessionRepository): SessionRepository = sessions
    @Provides @Singleton fun enrollment(sessions: APISessionRepository): EnrollmentRepository = APIEnrollmentRepository(AuthenticatedSessionGateway(sessions))
    @Provides fun enrollmentCases(repository: EnrollmentRepository) = EnrollmentUseCases(repository)
    @Provides @Singleton fun acquisition(@ApplicationContext context: Context): EvidenceAcquisition = AndroidEvidenceAcquisition(context)
    @Provides @Singleton fun configuration(): PlatformConfigurationRepository = LocalPlatformConfigurationRepository()
    @Provides fun access(sessions: SessionRepository, configuration: PlatformConfigurationRepository) = AccessUseCases(sessions, configuration)
}
