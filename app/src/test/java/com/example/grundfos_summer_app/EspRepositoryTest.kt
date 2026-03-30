package com.example.grundfos_summer_app

import com.example.grundfos_summer_app.data.model.EspErrors
import com.example.grundfos_summer_app.data.model.EspPumpStatus
import com.example.grundfos_summer_app.data.model.ProvisioningRequest
import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.remote.EspApiService
import com.example.grundfos_summer_app.data.repository.EspRepository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class EspRepositoryTest {
    @Test
    fun submitProvisioning_switchesFollowingStatusCallsToMdnsFirst() = runBlocking {
        val primaryApi = FakeEspApiService()
        val mdnsApi = FakeEspApiService()
        val repository = EspRepository(
            api = primaryApi,
            provisioningApi = mdnsApi
        )

        val provisioningResult = repository.submitProvisioning("MojeWifi", "tajneheslo")
        val statusResult = repository.getStatus()

        assertTrue(provisioningResult.isSuccess)
        assertTrue(statusResult.isSuccess)
        assertEquals(1, mdnsApi.statusCalls)
        assertEquals(0, primaryApi.statusCalls)
    }

    @Test
    fun setMode_triesStateFallbackAfter400() = runBlocking {
        val api = FakeEspApiService(
            modeResponses = mutableListOf(
                Response.error(400, "{\"ok\":false}".toResponseBody(JSON)),
                Response.success(Unit)
            )
        )
        val repository = EspRepository(api)

        val result = repository.setMode("AUTO")

        assertTrue(result.isSuccess)
        assertEquals(listOf("{\"mode\":\"AUTO\"}", "{\"mode\":\"AUTO_MODE\"}"), api.modeBodies)
    }

    @Test
    fun setBypass_triesEnabledFallbackAfter400() = runBlocking {
        val api = FakeEspApiService(
            bypassResponses = mutableListOf(
                Response.error(400, "{\"ok\":false}".toResponseBody(JSON)),
                Response.success(Unit)
            )
        )
        val repository = EspRepository(api)

        val result = repository.setBypass(true)

        assertTrue(result.isSuccess)
        assertEquals(listOf("{\"bypass\":true}", "{\"enabled\":true}"), api.bypassBodies)
    }

    @Test
    fun submitProvisioning_returnsFriendlyMessageOn400() = runBlocking {
        val api = FakeEspApiService(
            provisioningResponses = mutableListOf(
                Response.error(400, "{\"ok\":false}".toResponseBody(JSON))
            )
        )
        val repository = EspRepository(api)

        val result = repository.submitProvisioning(" MojeWifi ", "tajneheslo")

        assertTrue(result.isFailure)
        assertEquals("MojeWifi", api.provisionRequests.single().ssid)
        assertEquals(
            "SSID nesmí být prázdné. Zkontrolujte zadané údaje a zkuste to znovu.",
            result.exceptionOrNull()?.message
        )
    }

    private class FakeEspApiService(
        private val modeResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit)),
        private val bypassResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit)),
        private val provisioningResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit))
    ) : EspApiService {
        var statusCalls = 0
        val modeBodies = mutableListOf<String>()
        val bypassBodies = mutableListOf<String>()
        val provisionRequests = mutableListOf<ProvisioningRequest>()

        override suspend fun getStatus(): EspStatus = EspStatus(
            state = "AUTO_MODE",
            mode = "AUTO",
            bypass = false,
            errors = EspErrors(false, false, false),
            pump = EspPumpStatus(
                running = false,
                testPhase = false,
                pulseOk = false,
                pulseFrequencyHz = 0,
                pulseCountLastMinute = 0,
                pulseStability = 0
            )
        ).also {
            statusCalls++
        }

        override suspend fun setMode(body: RequestBody): Response<Unit> {
            modeBodies += readBody(body)
            return modeResponses.removeAt(0)
        }

        override suspend fun setBypass(body: RequestBody): Response<Unit> {
            bypassBodies += readBody(body)
            return bypassResponses.removeAt(0)
        }

        override suspend fun provision(request: ProvisioningRequest): Response<Unit> {
            provisionRequests += request
            return provisioningResponses.removeAt(0)
        }

        override suspend fun pumpStart(): Response<Unit> = Response.success(Unit)

        override suspend fun pumpStop(): Response<Unit> = Response.success(Unit)

        override suspend fun saveSchedule(body: RequestBody): Response<Unit> = Response.success(Unit)

        override suspend fun resetErrors(): Response<Unit> = Response.success(Unit)

        override suspend fun resetPumpError(body: RequestBody): Response<Unit> = Response.success(Unit)
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        private fun readBody(body: RequestBody): String {
            val buffer = Buffer()
            body.writeTo(buffer)
            return buffer.readUtf8()
        }
    }
}
