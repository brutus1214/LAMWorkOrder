package com.lamworkorder.data

import com.lamworkorder.BuildConfig
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import okhttp3.MediaType.Companion.toMediaType

interface WorkOrderApi {
    @GET("api/work-orders")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): List<WorkOrder>

    @POST("api/work-orders")
    suspend fun create(@Body request: CreateWorkOrder): WorkOrder

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

