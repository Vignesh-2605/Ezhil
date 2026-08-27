package com.ezhil.app.di

import android.content.Context
import androidx.room.Room
import com.ezhil.app.BuildConfig
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.remote.EzhilApiService
import com.ezhil.app.data.remote.interceptor.AuthInterceptor
import com.ezhil.app.data.remote.interceptor.BaseUrlInterceptor
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): EzhilDatabase =
        Room.databaseBuilder(ctx, EzhilDatabase::class.java, EzhilDatabase.DATABASE_NAME)
            .addMigrations(EzhilDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()
    // All DTOs use @JsonClass(generateAdapter = true) — KSP generates the adapters at
    // compile time, so KotlinJsonAdapterFactory (reflection) is not needed at runtime.

    @Provides
    @Singleton
    fun provideOkHttp(
        baseUrlInterceptor: BaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            // Must run first: it decides which server the request goes to.
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttp: OkHttpClient, moshi: Moshi): EzhilApiService =
        Retrofit.Builder()
            // Placeholder only — BaseUrlInterceptor rewrites this per request
            // from the address stored in the session.
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(EzhilApiService::class.java)
}
