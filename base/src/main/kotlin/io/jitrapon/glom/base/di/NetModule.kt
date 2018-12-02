package io.jitrapon.glom.base.di

import android.app.Application
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.BuildConfig
import io.jitrapon.glom.R
import io.jitrapon.glom.base.domain.user.account.AccountDataSource
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
@Module(includes = [BaseModule::class, BaseDomainModule::class])
class NetModule {

    @Provides
    @Named("apiBaseUrl")
    fun provideBaseUrl(application: Application): String {
        // should end with a backslash
        val url = application.getString(R.string.server_url)
        return if (url.endsWith('/')) url else "$url/"
    }

    @Provides
    @Named("headerInterceptor")
    internal fun provideHttpClientHeaderInterceptor(@Named("accountLocalDataSource") dataSource: AccountDataSource): Interceptor = Interceptor { chain ->
        chain.request().newBuilder().let {
            it.addHeader("Content-Type", "application/json")
            val token = dataSource.getAccount()?.idToken
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
    @Singleton
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