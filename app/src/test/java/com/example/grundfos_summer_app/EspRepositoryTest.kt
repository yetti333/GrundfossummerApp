package com.example.grundfos_summer_app

import com.example.grundfos_summer_app.data.model.ProvisioningRequest
import com.example.grundfos_summer_app.data.remote.ApiAckDto
import com.example.grundfos_summer_app.data.remote.EspApiService
import com.example.grundfos_summer_app.data.remote.EspHeartbeatDto
import com.example.grundfos_summer_app.data.remote.EspNetworkInfoDto
import com.example.grundfos_summer_app.data.remote.EspPumpStatusDto
import com.example.grundfos_summer_app.data.remote.EspStatusDto
import com.example.grundfos_summer_app.data.remote.EspErrorsDto
import com.example.grundfos_summer_app.data.repository.EspRepository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import retrofit2.Response

class EspRepositoryTest {
    @Test
    fun getStatus_usesPrimaryOnly_withoutMdnsFallback() = runBlocking {
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

        assertTrue(firstStatus.isFailure)
        assertEquals(1, primaryApi.statusCalls)
        assertEquals(0, mdnsApi.statusCalls)
        assertEquals(0, newPrimaryApi.statusCalls)
        assertTrue(createdBaseUrls.isEmpty())
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
    fun submitProvisioning_fallsBackToPrimaryApWhenMdnsUnavailable() = runBlocking {
        // Provisioning AP (primary when mDNS/provisioning API is unavailable)
        val primaryApApi = FakeEspApiService(
            provisioningResponses = mutableListOf(Response.success(ApiAckDto(ok = true)))
        )
        val mdnsApi = FakeEspApiService(
            provisioningResponses = mutableListOf(
                Response.error(503, "{\"error\":\"unavailable\"}".toResponseBody(JSON))
            )
        )
        val repository = EspRepository(
            api = primaryApApi,
            provisioningApi = mdnsApi,
            primaryBaseUrl = "http://10.66.12.184/"
        )

        val result = repository.submitProvisioning("MojeWifi", "tajneheslo")

        assertTrue(result.isSuccess)
        assertEquals(1, mdnsApi.provisionRequests.size)
        assertEquals(1, primaryApApi.provisionRequests.size)
    }

    @Test
    fun updatePrimaryUrl_switchesApiToNewIp() = runBlocking {
        val oldApi = FakeEspApiService(
            statusResults = mutableListOf(Result.failure(IOException("Old IP unreachable")))
        )
        val newApi = FakeEspApiService(
            statusResults = mutableListOf(Result.success(statusWithIp("10.66.12.99")))
        )
        val createdUrls = mutableListOf<String>()
        val repository = EspRepository(
            api = oldApi,
            primaryBaseUrl = "http://10.66.12.184/",
            apiFactory = { url -> createdUrls += url; newApi }
        )

        // Before update: uses old (failing) api
        val before = repository.getStatus()
        assertTrue(before.isFailure)

        // Simulate NSD discovery returning new IP
        repository.updatePrimaryUrl("http://10.66.12.99/")

        // After update: uses new api
        val after = repository.getStatus()
        assertTrue(after.isSuccess)
        assertEquals(listOf("http://10.66.12.99/"), createdUrls)
    }

    @Test
    fun setMode_triesStateFallbackAfter400() = runBlocking {
        val api = FakeEspApiService(
            modeResponses = mutableListOf(
                Response.success(ApiAckDto(ok = true))
            )
        )
        val repository = EspRepository(api)

        val result = repository.setMode("AUTO")

        assertTrue(result.isSuccess)
        assertEquals(listOf(mapOf("mode" to "AUTO")), api.modeBodies)
    }

    @Test
    fun setBypass_sendsFirmwarePayload() = runBlocking {
        val api = FakeEspApiService(
            bypassResponses = mutableListOf(
                Response.success(ApiAckDto(ok = true))
            )
        )
        val repository = EspRepository(api)

        val result = repository.setBypass(true)

        assertTrue(result.isSuccess)
        assertEquals(listOf(mapOf("bypass" to true)), api.bypassBodies)
    }

    @Test
    fun submitProvisioning_returnsFriendlyMessageOn400() = runBlocking {
        val api = FakeEspApiService(
            provisioningResponses = mutableListOf(
                Response.error(400, "{\"error\":\"ssid_empty\"}".toResponseBody(JSON))
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
        private val statusResults: MutableList<Result<EspStatusDto>> = mutableListOf(Result.success(statusWithIp("10.66.12.184"))),
        private val modeResponses: MutableList<Response<ApiAckDto>> = mutableListOf(Response.success(ApiAckDto(ok = true))),
        private val bypassResponses: MutableList<Response<ApiAckDto>> = mutableListOf(Response.success(ApiAckDto(ok = true))),
        private val provisioningResponses: MutableList<Response<ApiAckDto>> = mutableListOf(Response.success(ApiAckDto(ok = true)))
    ) : EspApiService {
        var statusCalls = 0
        val modeBodies = mutableListOf<Map<String, String>>()
        val bypassBodies = mutableListOf<Map<String, Boolean>>()
        val provisionRequests = mutableListOf<ProvisioningRequest>()

        override suspend fun getHeartbeat(): Response<EspHeartbeatDto> {
            return Response.success(EspHeartbeatDto(state = "AUTO_MODE"))
        }

        override suspend fun getStatus(): Response<EspStatusDto> {
            statusCalls++
            val result = if (statusResults.isNotEmpty()) {
                statusResults.removeAt(0)
            } else {
                Result.success(statusWithIp("10.66.12.184"))
            }

            return result.fold(
                onSuccess = { Response.success(it) },
                onFailure = { throw it }
            )
        }

        override suspend fun getBypass(): Response<Map<String, Boolean>> = Response.success(mapOf("bypass" to false))

        override suspend fun getLog(): Response<Map<String, Any>> = Response.success(emptyMap())

        override suspend fun getDiagPing(): Response<Map<String, Any>> = Response.success(emptyMap())

        override suspend fun getDiagNetwork(): Response<Map<String, Any>> = Response.success(emptyMap())

        override suspend fun setMode(body: Map<String, String>): Response<ApiAckDto> {
            modeBodies += body
            return modeResponses.removeAt(0)
        }

        override suspend fun setBypass(body: Map<String, Boolean>): Response<ApiAckDto> {
            bypassBodies += body
            return bypassResponses.removeAt(0)
        }

        override suspend fun provision(request: ProvisioningRequest): Response<ApiAckDto> {
            provisionRequests += request
            return provisioningResponses.removeAt(0)
        }

        override suspend fun pumpStart(): Response<ApiAckDto> = Response.success(ApiAckDto(ok = true))

        override suspend fun pumpStop(): Response<ApiAckDto> = Response.success(ApiAckDto(ok = true))

        override suspend fun saveSchedule(body: Map<String, Int>): Response<ApiAckDto> = Response.success(ApiAckDto(ok = true))

        override suspend fun resetPumpError(): Response<ApiAckDto> = Response.success(ApiAckDto(ok = true))
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        private fun statusWithIp(ip: String?): EspStatusDto = EspStatusDto(
            state = "AUTO_MODE",
            mode = "AUTO",
            bypass = false,
            errors = EspErrorsDto(false, false, false),
            pump = EspPumpStatusDto(
                running = false,
                testPhase = false,
                pulseOk = false,
                pulseFrequencyHz = 0.0,
                pulseCountLastMinute = 0,
                pulseStability = 0.0
            ),
            ip = ip,
            networkInfo = EspNetworkInfoDto(ip = ip)
        )
    }
}
