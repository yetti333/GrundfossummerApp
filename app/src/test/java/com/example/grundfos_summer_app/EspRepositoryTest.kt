package com.example.grundfos_summer_app

import com.example.grundfos_summer_app.data.model.EspErrors
import com.example.grundfos_summer_app.data.model.EspPumpStatus
import com.example.grundfos_summer_app.data.model.EspSchedule
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
    fun saveSettings_stopsAfterFirstSuccessfulPayload() = runBlocking {
        val api = FakeEspApiService(
            scheduleResponses = mutableListOf(Response.success(Unit))
        )
        val repository = EspRepository(api)

        val result = repository.saveSettings("19:00", 5, 60)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("{\"start_hour\":19,\"start_minute\":0,\"duration_minutes\":5}"),
            api.scheduleBodies
        )
    }

    private class FakeEspApiService(
        private val modeResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit)),
        private val bypassResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit)),
        private val scheduleResponses: MutableList<Response<Unit>> = mutableListOf(Response.success(Unit))
    ) : EspApiService {
        val modeBodies = mutableListOf<String>()
        val bypassBodies = mutableListOf<String>()
        val scheduleBodies = mutableListOf<String>()

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
            ),
            schedule = EspSchedule(19, 0, 5)
        )

        override suspend fun setMode(body: RequestBody): Response<Unit> {
            modeBodies += readBody(body)
            return modeResponses.removeAt(0)
        }

        override suspend fun setBypass(body: RequestBody): Response<Unit> {
            bypassBodies += readBody(body)
            return bypassResponses.removeAt(0)
        }

        override suspend fun pumpStart(): Response<Unit> = Response.success(Unit)

        override suspend fun pumpStop(): Response<Unit> = Response.success(Unit)

        override suspend fun saveSchedule(body: RequestBody): Response<Unit> {
            scheduleBodies += readBody(body)
            return scheduleResponses.removeAt(0)
        }
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

