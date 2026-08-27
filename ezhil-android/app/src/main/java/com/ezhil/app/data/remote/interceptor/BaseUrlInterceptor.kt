package com.ezhil.app.data.remote.interceptor

import android.util.Log
import com.ezhil.app.data.local.SecurePrefs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites every request onto the server address stored in the session.
 *
 * Retrofit fixes its baseUrl when the singleton is built, so a compile-time
 * BuildConfig value means the APK only ever talks to whichever machine was on
 * the developer's network at build time — a new Wi-Fi, a tunnel, or a hosted
 * backend all required a rebuild. Rewriting here keeps one APK usable on any
 * network: set the address once on the login screen and it persists.
 *
 * The path Retrofit built is preserved; only scheme, host and port change.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val prefs: SecurePrefs,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val override = prefs.serverUrl?.trim().orEmpty()
        if (override.isEmpty()) return chain.proceed(request)

        val base = normalise(override)
        if (base == null) {
            Log.w(TAG, "stored server address is not a valid URL: $override")
            return chain.proceed(request)
        }

        val rewritten = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()

        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    private fun normalise(raw: String): HttpUrl? {
        // Accept "example.com", "example.com:8080" and full URLs alike —
        // a teacher typing an address should not have to remember a scheme.
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw
                         else "https://$raw"
        return withScheme.toHttpUrlOrNull()
    }

    private companion object {
        const val TAG = "EzhilBaseUrl"
    }
}
