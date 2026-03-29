package com.example.grundfos_summer_app.data.remote

import com.example.grundfos_summer_app.data.model.EspStatus
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface EspApiService {
    @GET("status")
    suspend fun getStatus(): EspStatus

    @POST("set/mode")
    suspend fun setMode(@Body body: RequestBody): Response<Unit>

    @POST("set/bypass")
    suspend fun setBypass(@Body body: RequestBody): Response<Unit>

    @POST("pump/start")
    suspend fun pumpStart(): Response<Unit>

    @POST("pump/stop")
    suspend fun pumpStop(): Response<Unit>

    @POST("set/schedule")
    suspend fun saveSchedule(@Body body: RequestBody): Response<Unit>

    @POST("reset/errors")
    suspend fun resetErrors(): Response<Unit>

    @POST("pump/error-reset")
    suspend fun resetPumpError(@Body body: RequestBody): Response<Unit>
}
