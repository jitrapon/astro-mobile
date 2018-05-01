package io.jitrapon.glom.base.di

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.BuildConfig
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
@Module
class NetModule {

    @Provides
    @Named("apiBaseUrl")
    fun provideBaseUrl(): String {
        // should end with a backslash
        return "https://jsonplaceholder.typicode.com/"
    }

    @Provides
    @Named("headerInterceptor")
    internal fun provideHttpClientHeaderInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer abcd1234")
                .build()
        chain.proceed(request)
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
    fun provideConverterFactory(moshi: Moshi): Converter.Factory = MoshiConverterFactory.create(moshi)

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