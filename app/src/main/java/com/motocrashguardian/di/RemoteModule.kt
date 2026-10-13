package com.motocrashguardian.di

import android.content.Context
import android.content.SharedPreferences
import com.motocrashguardian.BuildConfig
import com.motocrashguardian.data.remote.ApiJson
import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.remote.KeystoreSecretCipher
import com.motocrashguardian.data.remote.McgApi
import com.motocrashguardian.data.remote.SecretCipher
import com.motocrashguardian.data.remote.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {

    @Provides
    @Singleton
    fun provideSecretCipher(): SecretCipher = KeystoreSecretCipher()

    @Provides
    @Singleton
    fun provideAuthSession(
        @ApplicationContext context: Context,
        cipher: SecretCipher
    ): AuthSession = TokenStore(authPreferences(context), cipher)

    @Provides
    @Singleton
    fun provideJson(): Json = ApiJson

    @Provides
    @Singleton
    fun provideOkHttpClient(auth: AuthSession): OkHttpClient = OkHttpClient.Builder()
        // El servicio gratuito de Render tarda 30-60 s en despertar; esto corre en segundo plano.
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = auth.token()
            val request = if (token == null) {
                chain.request()
            } else {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            }
            chain.proceed(request)
        }
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC: solo metodo, URL y codigo. Nunca cuerpos (telefonos, coordenadas) ni token.
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    }
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideMcgApi(retrofit: Retrofit): McgApi = retrofit.create(McgApi::class.java)

    private fun authPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences("mcg_auth", Context.MODE_PRIVATE)
}
