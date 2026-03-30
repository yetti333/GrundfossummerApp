package com.example.grundfos_summer_app

import com.example.grundfos_summer_app.data.model.EspErrors
import com.example.grundfos_summer_app.data.model.EspPumpStatus
import com.example.grundfos_summer_app.data.model.ProvisioningRequest
import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.model.EspNetworkInfo
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
import java.io.IOException
import retrofit2.Response

class EspRepositoryTest {
    @Test
    fun getStatus_fallsBackToMdnsAndSwitchesPrimaryToNewIp() = runBlocking {
        val primaryApi = FakeEspApiService(
            statusResults = mutableListOf(Result.failure(IOException("Primary unreachable")))
        )
        val mdnsApi = FakeEspApiService(
            statusResults = mutableListOf(Result.success(statusWithIp("10.66.12.250")))
        )
        val newPrimaryApi = FakeEspApiService(
            statusResults = mutableListOf(Result.success(statusWithIp("10.66.12.250")))
        )
        val createdBaseUrls = mutableListOf<String>()

        val repository = EspRepository(
            api = primaryApi,
            provisioningApi = mdnsApi,
            primaryBaseUrl = "http://10.66.12.184/",
            apiFactory = { baseUrl ->
                createdBaseUrls += baseUrl
                newPrimaryApi
            }
        )

        val firstStatus = repository.getStatus()
        val secondStatus = repository.getStatus()

        assertTrue(firstStatus.isSuccess)
        assertTrue(secondStatus.isSuccess)
        assertEquals(1, primaryApi.statusCalls)
        assertEquals(1, mdnsApi.statusCalls)
        assertEquals(1, newPrimaryApi.statusCalls)
        assertEquals(listOf("http://10.66.12.250/"), createdBaseUrls)
    }

    @Test
    fun submitProvisioning_usesMdnsFirst() = runBlocking {
        val primaryApi = FakeEspApiService()
        val mdnsApi = FakeEspApiService()
        val repository = EspRepository(
            api = primaryApi,
            provisioningApi = mdnsApi
        )

        val provisioningResult = repository.submitProvisioning("MojeWifi", "tajneheslo")

        assertTrue(provisioningResult.isSuccess)
        assertEquals(1, mdnsApi.provisionRequests.size)
        assertEquals(0, primaryApi.provisionRequests.size)
    }

    @Test
    fun provisioningThenStatusRecovery_switchesBackToIpAfterMdnsDiscovery() = runBlocking {
        val primaryApi = FakeEspApiService(
            statusResults = mutableListOf(Result.failure(IOException("Old IP unreachable")))
        )
        val mdnsApi = FakeEspApiService(
            statusResults = mutableListOf(Result.success(statusWithIp("10.66.12.251")))
        )
        val newPrimaryApi = FakeEspApiService(
            statusResults = mutableListOf(Result.success(statusWithIp("10.66.12.251")))
        )
        val createdBaseUrls = mutableListOf<String>()
        val repository = EspRepository(
            api = primaryApi,
            provisioningApi = mdnsApi,
            primaryBaseUrl = "http://10.66.12.184/",
            apiFactory = { baseUrl ->
                createdBaseUrls += baseUrl
                newPrimaryApi
            }
        )

        val provisioningResult = repository.submitProvisioning("MojeWifi", "tajneheslo")
        val firstStatus = repository.getStatus()
        val secondStatus = repository.getStatus()

        assertTrue(provisioningResult.isSuccess)
        assertTrue(firstStatus.isSuccess)
        assertTrue(secondStatus.isSuccess)
        assertEquals(1, mdnsApi.provisionRequests.size)
        assertEquals(1, primaryApi.statusCalls)
        assertEquals(1, mdnsApi.statusCalls)
        assertEquals(1, newPrimaryApi.statusCalls)
        assertEquals(listOf("http://10.66.12.251/"), createdBaseUrls)
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
        private val statusResults: MutableList<Result<EspStatus>> = mutableListOf(Result.success(statusWithIp("10.66.12.184"))),
        private val modeResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit)),
        private val bypassResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit)),
        private val provisioningResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit))
    ) : EspApiService {
        var statusCalls = 0
        val modeBodies = mutableListOf<String>()
        val bypassBodies = mutableListOf<String>()
        val provisionRequests = mutableListOf<ProvisioningRequest>()

        override suspend fun getStatus(): EspStatus {
            statusCalls++
            val result = if (statusResults.isNotEmpty()) {
                statusResults.removeAt(0)
            } else {
                Result.success(statusWithIp("10.66.12.184"))
            }

            return result.getOrElse { throw it }
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

        private fun statusWithIp(ip: String?): EspStatus = EspStatus(
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
            ),
            ip = ip,
            networkInfo = EspNetworkInfo(ip = ip)
        )

        private fun readBody(body: RequestBody): String {
            val buffer = Buffer()
            body.writeTo(buffer)
            return buffer.readUtf8()
        }
    }
}
