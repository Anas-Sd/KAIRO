package com.kairo.app.feature.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccessCodeDto(
    @SerialName("code") val code: String,
    @SerialName("name") val name: String
)
