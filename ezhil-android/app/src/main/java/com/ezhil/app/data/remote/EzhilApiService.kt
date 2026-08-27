package com.ezhil.app.data.remote

import com.ezhil.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface EzhilApiService {

    // ── AUTH ─────────────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/student/login")
    suspend fun studentLogin(@Body request: StudentLoginRequest): StudentLoginResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    // ── SYNC ─────────────────────────────────────────────────────────
    @POST("sync/push")
    suspend fun push(@Body request: SyncPushRequest): SyncPushResponse

    @GET("sync/pull")
    suspend fun pull(@Query("last_sync") lastSync: String): SyncPullResponse

    // ── STUDIO ───────────────────────────────────────────────────────
    @Multipart
    @POST("studio/ocr")
    suspend fun ocrUpload(
        @Part image: MultipartBody.Part,
        @Part("source_hash") hash: RequestBody
    ): OcrResponse

    @POST("studio/generate")
    suspend fun generateLesson(@Body request: GenerateRequest): GenerateResponse

    @Multipart
    @POST("studio/extract-multi")
    suspend fun extractMulti(
        @Part files: List<MultipartBody.Part>
    ): ExtractMultiResponse
}
