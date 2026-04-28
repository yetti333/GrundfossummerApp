package com.example.grundfos_summer_app.data.remote

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    private const val LOG_TAG = "ESP_API"

    /**
     * Single shared OkHttpClient with keep-alive and timeouts recommended by the ESP reference:
     *  - connectTimeout = 2 s  (ESP responds quickly once reachable)
     *  - readTimeout    = 3 s  (longest endpoint is /status with full state)
     * Reusing one client across all Retrofit instances means TCP connections are pooled
     * and reused (keep-alive), eliminating the TCP handshake cost on every request.
     */
    private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(JsonHeadersInterceptor())
        .addInterceptor(EspApiLoggingInterceptor())
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    fun buildRetrofit(baseUrl: String): EspApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(sharedHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EspApiService::class.java)
    }

    private class JsonHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val builder = request.newBuilder()
                .header("Accept", "application/json")

            if (request.body != null && request.header("Content-Type").isNullOrBlank()) {
                builder.header("Content-Type", "application/json")
            }

            return chain.proceed(builder.build())
        }
    }

    private class EspApiLoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val requestPath = request.url.encodedPath
            val requestBody = request.body?.let { bodyToString(request, it.contentType()) }
            val startedAtMs = System.currentTimeMillis()

            Log.d(
                LOG_TAG,
                "--> ${request.method} $requestPath body=${maskSensitive(requestBody)}"
            )

            return try {
                val response = chain.proceed(request)
                val durationMs = System.currentTimeMillis() - startedAtMs
                val responseBody = response.peekBody(Long.MAX_VALUE).string()
                Log.d(
                    LOG_TAG,
                    "<-- ${response.code} ${request.method} $requestPath (${durationMs}ms) body=${maskSensitive(responseBody)}"
                )
                response
            } catch (e: IOException) {
                val durationMs = System.currentTimeMillis() - startedAtMs
                Log.e(
                    LOG_TAG,
                    "xx> ${request.method} $requestPath FAILED (${durationMs}ms): ${e.message}",
                    e
                )
                throw e
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startedAtMs
                Log.e(
                    LOG_TAG,
                    "xx> ${request.method} $requestPath ERROR (${durationMs}ms): ${e.message}",
                    e
                )
                throw e
            }
        }

        private fun bodyToString(request: Request, contentType: MediaType?): String {
            val copy = request.newBuilder().build()
            val body = copy.body ?: return ""
            return try {
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readString(contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
            } catch (_: Exception) {
                "<unreadable>"
            }
        }

        private fun maskSensitive(value: String?): String {
            if (value.isNullOrBlank()) {
                return ""
            }

            return value.replace(
                Regex("\"password\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE),
                "\"password\":\"***\""
            )
        }
    }
}
