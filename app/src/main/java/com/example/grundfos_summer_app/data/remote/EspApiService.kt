package com.example.grundfos_summer_app.data.remote

import com.example.grundfos_summer_app.data.model.EspSettings
import com.example.grundfos_summer_app.data.model.EspStatus
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface EspApiService {
    @GET("status")
    suspend fun getStatus(): EspStatus

    @POST("mode")
    suspend fun setMode(@Body body: Map<String, String>): Response<Unit>

    @POST("set/bypass")
    suspend fun setBypass(@Body body: Map<String, Boolean>): Response<Unit>

    @POST("pump/start")
    suspend fun pumpStart(@Body body: Map<String, Any>): Response<Unit>

    @POST("pump/stop")
    suspend fun pumpStop(@Body body: Map<String, Any>): Response<Unit>

    @POST("settings")
    suspend fun saveSettings(@Body body: EspSettings): Response<Unit>
}

