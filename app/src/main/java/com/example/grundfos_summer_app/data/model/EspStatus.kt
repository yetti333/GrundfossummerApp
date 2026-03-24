package com.example.grundfos_summer_app.data.model

data class EspErrors(
    val wifi: Boolean,
    val time: Boolean,
    val pump: Boolean
)

data class EspStatus(
    val mode: String,
    val pumpRunning: Boolean,
    val feedback: Long,
    val feedbackStable: Boolean,
    val bypass: Boolean,
    val errors: EspErrors
)

