package com.example.grundfos_summer_app.data.remote

import com.example.grundfos_summer_app.data.model.ProvisioningRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface   EspApiService {
    @GET("heartbeat")
    suspend fun getHeartbeat(): Response<EspHeartbeatDto>

    @GET("status")
    suspend fun getStatus(): Response<EspStatusDto>

    @GET("bypass")
    suspend fun getBypass(): Response<Map<String, Boolean>>

    @GET("log")
    suspend fun getLog(): Response<Map<String, Any>>

    @GET("diag/ping")
    suspend fun getDiagPing(): Response<Map<String, Any>>

    @GET("diag/network")
    suspend fun getDiagNetwork(): Response<Map<String, Any>>

    @POST("provision")
    suspend fun provision(@Body request: ProvisioningRequest): Response<ApiAckDto>

    @POST("set/mode")
    suspend fun setMode(@Body body: Map<String, String>): Response<ApiAckDto>

    @POST("set/bypass")
    suspend fun setBypass(@Body body: Map<String, Boolean>): Response<ApiAckDto>

    @POST("pump/start")
    suspend fun pumpStart(): Response<ApiAckDto>

    @POST("pump/stop")
    suspend fun pumpStop(): Response<ApiAckDto>

    @POST("set/schedule")
    suspend fun saveSchedule(@Body body: Map<String, Int>): Response<ApiAckDto>

    @POST("pump/error-reset")
    suspend fun resetPumpError(): Response<ApiAckDto>
}
