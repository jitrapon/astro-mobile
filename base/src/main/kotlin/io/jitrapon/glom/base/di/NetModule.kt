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
    fun provideApiToken(): String? = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImEzMjJiNjhiY2U0MzExZTg2OTYzOTUzM2QzYTFhMjU1MWQ1ZTc0YzYiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20vaml0cmFwb24tZ2xvbSIsImF1ZCI6ImppdHJhcG9uLWdsb20iLCJhdXRoX3RpbWUiOjE1MDg0Njk0MzEsInVzZXJfaWQiOiJva3BrY0xzaDJ4VVJJVmZSWjFhV1RLQ21JY0kzIiwic3ViIjoib2twa2NMc2gyeFVSSVZmUloxYVdUS0NtSWNJMyIsImlhdCI6MTUzODIyMzYyOSwiZXhwIjoxNTM4MjI3MjI5LCJlbWFpbCI6Inlvc2hpMzAwM0BnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsImZpcmViYXNlIjp7ImlkZW50aXRpZXMiOnsiZW1haWwiOlsieW9zaGkzMDAzQGdtYWlsLmNvbSJdfSwic2lnbl9pbl9wcm92aWRlciI6InBhc3N3b3JkIn19.jmbRwmdGiclNHZZlYnJ7mRSru_mrJ67V7ZN2efOjrOT0PLNi1Vys8IJoOoQmfgiXxb_k-B32ck8o1wnRFs0nrFo4GUWqxatCq5JNVJmtBzud7hu_0cWEHf4OX0KOSTZp85WhaLtCazfY6eyD8dFILT8BvPz2cQcEvtiX-ow2H0jctrzjNZvbNzXKGQZdxhZe2VnLaIMk2Igol7Bx9giv1ubxMCKWzuUdXplkzBhyQTht_hJFszu09moJyocVES50Z5j-ehZ5G3y6z5hHxD36qPz5uEbZqHavmYwvdDUywmY_BlYBmtlHyMKrNvJCdr76tkHfMOZPeqjlBjafRykZyQ"

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