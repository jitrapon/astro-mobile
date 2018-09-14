package io.jitrapon.glom.base.di

import android.app.Application
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.BuildConfig
import io.jitrapon.glom.R
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Retrofit net module
 *
 * Created by Jitrapon
 */
@Module(includes = [BaseModule::class])
class NetModule {

    @Provides
    @Named("apiBaseUrl")
    fun provideBaseUrl(application: Application): String {
        // should end with a backslash
        val url = application.getString(R.string.server_url)
        return if (url.endsWith('/')) url else "$url/"
    }

    @Provides
    @Named("apiToken")
    fun provideApiToken(): String? = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjBmNTVkZWZlOWU5YzU2ZmRhZTRkOGY0MDFjZjQ5Njc4YzE2N2MzYWEifQ.eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20vaml0cmFwb24tZ2xvbSIsImF1ZCI6ImppdHJhcG9uLWdsb20iLCJhdXRoX3RpbWUiOjE1MDg0Njk0MzEsInVzZXJfaWQiOiJva3BrY0xzaDJ4VVJJVmZSWjFhV1RLQ21JY0kzIiwic3ViIjoib2twa2NMc2gyeFVSSVZmUloxYVdUS0NtSWNJMyIsImlhdCI6MTUzNjkxMTEyNywiZXhwIjoxNTM2OTE0NzI3LCJlbWFpbCI6Inlvc2hpMzAwM0BnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsImZpcmViYXNlIjp7ImlkZW50aXRpZXMiOnsiZW1haWwiOlsieW9zaGkzMDAzQGdtYWlsLmNvbSJdfSwic2lnbl9pbl9wcm92aWRlciI6InBhc3N3b3JkIn19.n14ZddApJ_xo-dfsCdaONbOYxkNKwXALtLrYzligEfzJVVGlXz5aEPQzrwvCw5tcVySuPtuPO0BSGMig_1xQim6CKVBWGlWlAFOO4T6a1pZbo46vdWrtmaos3sQmRfk-qi9_cLimjgXS1pAtL_ArB6l5OfUw4Rp3L4Y0Sg-fL1-d972pilOL4mKxIbnTlNHvP3KSjLWxRnF02B4Pse0_3n2bn_u8Mz2pGgrHdHWZgQYlcBHqrq-V2Tg0vZTd3kPk-fxwzY6gfNB3e_BwfgWB9qpYXJ0qqtAySYTafoj-_jLnyqU9UW_smEmcv3gW2k4DiG4RLJhUmNWwoeHXvErgfA"

    @Provides
    @Named("headerInterceptor")
    internal fun provideHttpClientHeaderInterceptor(@Named("apiToken") token: String?): Interceptor = Interceptor { chain ->
        chain.request().newBuilder().let {
            it.addHeader("Content-Type", "application/json")
            if (!token.isNullOrEmpty()) it.addHeader("Authorization", "Bearer $token")
            chain.proceed(it.build())
        }
    }

    @Provides
    @Named("loggingInterceptor")
    internal fun provideHttpClientLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
    }

    @Provides
    fun provideHttpClient(@Named("headerInterceptor") httpHeaderIntercepter: Interceptor,
                          @Named("loggingInterceptor") loggingInterceptor: Interceptor): OkHttpClient {
        return OkHttpClient().newBuilder()
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(httpHeaderIntercepter)
                .addInterceptor(loggingInterceptor)
                .build()
    }

    @Provides
    fun provideMoshi(): Moshi {
        // add all JsonAdapter factory here
        return Moshi.Builder().build()
    }

    @Provides
    fun provideConverterFactory(moshi: Moshi): Converter.Factory = MoshiConverterFactory.create(moshi).withNullSerialization()

    @Provides
    fun provideCallAdapterFactory(): CallAdapter.Factory = RxJava2CallAdapterFactory.create()

    @Provides
    @Singleton
    fun provideRetrofit(@Named("apiBaseUrl") baseUrl: String, httpClient: OkHttpClient, converterFactory: Converter.Factory,
                        adapterFactory: CallAdapter.Factory): Retrofit {
        return Retrofit.Builder()
                .client(httpClient)
                .baseUrl(baseUrl)
                .addConverterFactory(converterFactory)
                .addCallAdapterFactory(adapterFactory)
                .build()
    }
}