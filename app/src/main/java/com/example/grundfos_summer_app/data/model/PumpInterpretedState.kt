package com.example.grundfos_summer_app.data.model

enum class PumpInterpretedState {
    STANDBY,
    READY_STANDBY,
    LOW_OPERATION,
    NORMAL_OPERATION,
    LOW_VOLTAGE_WARNING,
    ROTOR_BLOCKED,
    ELECTRICAL_FAULT,
    UNKNOWN;

    val isAlarm: Boolean
        get() = this == LOW_VOLTAGE_WARNING || this == ROTOR_BLOCKED || this == ELECTRICAL_FAULT

    companion object {
        fun fromApi(raw: String?): PumpInterpretedState {
            return when (raw?.trim()?.lowercase()) {
                "standby" -> STANDBY
                "ready_standby_time" -> READY_STANDBY
                "low_operation" -> LOW_OPERATION
                "normal_operation", "normal" -> NORMAL_OPERATION
                "low_voltage_warning" -> LOW_VOLTAGE_WARNING
                "rotor_blocked" -> ROTOR_BLOCKED
                "electrical_fault" -> ELECTRICAL_FAULT
                else -> UNKNOWN
            }
        }
    }
}
