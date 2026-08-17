package com.lamworkorder.data

import com.lamworkorder.BuildConfig
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType

interface WorkOrderApi {
    @POST("api/auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("api/auth/register") suspend fun register(@Body request: RegistrationRequest): LoginResponse
    @GET("api/profile") suspend fun profile(@Header("Authorization") auth: String): User
    @PATCH("api/profile") suspend fun updateProfile(@Header("Authorization") auth: String, @Body request: ProfileUpdate): User
    @GET("api/users") suspend fun users(@Header("Authorization") auth: String): List<User>
    @PATCH("api/users/{id}") suspend fun updateUser(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: UserAdminUpdate): User
    @POST("api/users/{id}/reset-password") suspend fun resetPassword(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: PasswordReset)

    @GET("api/work-orders")
    suspend fun list(
        @Header("Authorization") auth: String,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): List<WorkOrder>

    @POST("api/work-orders")
    suspend fun create(@Header("Authorization") auth: String, @Body request: CreateWorkOrder): WorkOrder

    @PUT("api/work-orders/{id}") suspend fun update(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: UpdateWorkOrder): WorkOrder
    @PATCH("api/work-orders/{id}/status") suspend fun updateStatus(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: StatusUpdate): WorkOrder
    @Multipart @POST("api/work-orders/{id}/attachments") suspend fun upload(@Header("Authorization") auth: String, @Path("id") id: String, @Part files: List<MultipartBody.Part>): List<Attachment>
    @GET("api/attachments/{id}/content") suspend fun attachmentContent(@Header("Authorization") auth: String, @Path("id") id: String): ResponseBody

    companion object {
        fun create(): WorkOrderApi {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(WorkOrderApi::class.java)
        }
    }
}
