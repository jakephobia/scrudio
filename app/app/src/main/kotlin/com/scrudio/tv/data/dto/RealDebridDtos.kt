package com.scrudio.tv.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for Real-Debrid's OAuth 2.0 *device flow*.
 *
 * Endpoints used (all under https://api.real-debrid.com/):
 *  1. GET  /oauth/v2/device/code         → [DeviceCodeDto]
 *  2. GET  /oauth/v2/device/credentials  → [DeviceCredentialsDto] (or 400 while pending)
 *  3. POST /oauth/v2/token               → [TokenDto]
 *
 * The TV polls #2 until the user approves the prompt on real-debrid.com/device,
 * then exchanges the device-code for a real OAuth token via #3.
 */

@Serializable
data class DeviceCodeDto(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("interval") val intervalSeconds: Int = 5,
    @SerialName("expires_in") val expiresInSeconds: Int = 600,
    @SerialName("verification_url") val verificationUrl: String,
    /** Pre-filled URL the QR code points to — opens the page with `user_code` baked in. */
    @SerialName("direct_verification_url") val directVerificationUrl: String? = null
)

@Serializable
data class DeviceCredentialsDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("refresh_token") val refreshToken: String
)
