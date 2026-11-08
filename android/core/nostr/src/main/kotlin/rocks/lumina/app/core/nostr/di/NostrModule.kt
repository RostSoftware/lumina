package rocks.lumina.app.core.nostr.di

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** App-wide coroutine scope (outlives screens). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
public object NostrModule {
    @Provides
    @Singleton
    @ApplicationScope
    public fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    public fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    public fun provideNostrClient(okHttp: OkHttpClient, @ApplicationScope scope: CoroutineScope): NostrClient =
        NostrClient(
            websocketBuilder = BasicOkHttpWebSocket.Builder { okHttp },
            parentScope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
        )
}
