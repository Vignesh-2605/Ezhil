package com.ezhil.app.data.remote.interceptor

import com.ezhil.app.data.local.SecurePrefs
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val prefs: SecurePrefs
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = prefs.authToken
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
