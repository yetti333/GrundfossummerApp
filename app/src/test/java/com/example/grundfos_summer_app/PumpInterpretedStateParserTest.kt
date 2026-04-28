package com.example.grundfos_summer_app

import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.model.PumpInterpretedState
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class PumpInterpretedStateParserTest {
    private val gson = Gson()

    @Test
    fun interpretedState_parsesNormalOperation() {
        val status = parseStatus(
            """
            {
              "state": "AUTO_MODE",
              "mode": "AUTO",
              "bypass": false,
              "errors": {"wifi": false, "time": false, "pump": false},
              "pump": {
                "running": true,
                "test_phase": false,
                "pulse_ok": true,
                "pulse_frequency_hz": 75,
                "pulse_count_last_minute": 4500,
                "pulse_stability": 98,
                "period_ms": 13.33,
                "high_ms": 4.00,
                "duty_percent": 30.0,
                "interpreted_state": "normal_operation",
                "valid_frequency": true,
                "timestamp": 1775300000
              }
            }
            """.trimIndent()
        )

        assertEquals(PumpInterpretedState.NORMAL_OPERATION, status.pump.interpretedState)
    }

    @Test
    fun interpretedState_parsesRotorBlockedAlarm() {
        val status = parseStatus(
            """
            {
              "state": "MANUAL_MODE",
              "mode": "MANUAL",
              "bypass": false,
              "errors": {"wifi": false, "time": false, "pump": true},
              "pump": {
                "running": true,
                "test_phase": false,
                "pulse_ok": false,
                "pulse_frequency_hz": 75,
                "pulse_count_last_minute": 4500,
                "pulse_stability": 41,
                "period_ms": 13.33,
                "high_ms": 12.00,
                "duty_percent": 90.0,
                "interpreted_state": "rotor_blocked",
                "valid_frequency": true,
                "timestamp": 1775300001
              }
            }
            """.trimIndent()
        )

        assertEquals(PumpInterpretedState.ROTOR_BLOCKED, status.pump.interpretedState)
    }

    @Test
    fun interpretedState_defaultsToUnknownWhenMissing() {
        val status = parseStatus(
            """
            {
              "state": "AUTO_MODE",
              "mode": "AUTO",
              "bypass": false,
              "errors": {"wifi": false, "time": false, "pump": false},
              "pump": {
                "running": true,
                "test_phase": false,
                "pulse_ok": true,
                "pulse_frequency_hz": 75,
                "pulse_count_last_minute": 4500,
                "pulse_stability": 97,
                "period_ms": 13.33,
                "high_ms": 4.00,
                "duty_percent": 30.0,
                "valid_frequency": true,
                "timestamp": 1775300002
              }
            }
            """.trimIndent()
        )

        assertEquals(PumpInterpretedState.UNKNOWN, status.pump.interpretedState)
    }

    private fun parseStatus(json: String): EspStatus = gson.fromJson(json, EspStatus::class.java)
}
