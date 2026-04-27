package com.scrudio.tv.data.repository

import android.content.Context
import com.scrudio.tv.BuildConfig
import com.scrudio.tv.data.api.HttpModule
import com.scrudio.tv.data.api.RealDebridAuthApi
import com.scrudio.tv.data.dto.DeviceCodeDto
import com.scrudio.tv.data.dto.TokenDto
import com.scrudio.tv.data.settings.ScrudioSettings
import retrofit2.HttpException

/**
 * Real-Debrid OAuth device-flow plumbing.
 *
 * `requestDeviceCode` → user types/scans the code on real-debrid.com/device →
 * we poll `pollCredentials` every `interval` seconds → on success we call
 * `exchangeForToken` and persist the bundle in [ScrudioSettings].
 *
 * After pairing, [accessToken] returns a guaranteed-fresh token (auto-refreshing
 * if the cached one is within 60 s of expiry). Callers that need the RD token
 * should always go through this method so they don't have to know about expiry.
 */
class RealDebridAuthRepository private constructor(context: Context) {

    private val api: RealDebridAuthApi =
        HttpModule.retrofit(context, BuildConfig.RD_BASE_URL).create(RealDebridAuthApi::class.java)
    private val settings = ScrudioSettings.get(context)

    /** Step 1 — get a [DeviceCodeDto] to display on the TV. */
    suspend fun requestDeviceCode(): DeviceCodeDto =
        api.requestDeviceCode(BuildConfig.RD_CLIENT_ID)

    /**
     * Step 2 — poll once. Returns `null` if the user hasn't authorized yet
     * (HTTP 400 with `error: authorization_pending`); throws on any other error.
     */
    suspend fun pollOnce(deviceCode: String): PairCredentials? = try {
        val resp = api.pollCredentials(BuildConfig.RD_CLIENT_ID, deviceCode)
        PairCredentials(resp.clientId, resp.clientSecret)
    } catch (e: HttpException) {
        // 400 == still pending. Anything else (403, 5xx) → bubble up.
        if (e.code() == 400) null else throw e
    }

    /** Step 3 — swap the device_code + per-device credentials for a real OAuth token. */
    suspend fun exchangeForToken(
        deviceCode: String,
        credentials: PairCredentials
    ): TokenDto = api.exchangeToken(
        clientId = credentials.clientId,
        clientSecret = credentials.clientSecret,
        deviceCode = deviceCode
    )

    /** Persists the result of a successful pair (or refresh). */
    suspend fun saveAuth(token: TokenDto, credentials: PairCredentials) {
        settings.setRdAuth(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            clientId = credentials.clientId,
            clientSecret = credentials.clientSecret,
            expiresAtMillis = nowMillis() + token.expiresInSeconds * 1_000L
        )
    }

    /**
     * Returns a non-empty access token, refreshing it transparently if needed.
     * Returns `""` when the user has never paired RD.
     */
    suspend fun accessToken(): String {
        val current = settings.rdKey()
        if (current.isEmpty()) return ""

        // If we don't have a refresh_token, we're in legacy mode (user pasted
        // a private API token). It does not expire — return as-is.
        val refresh = settings.rdRefreshToken()
        if (refresh.isEmpty()) return current

        val expiresAt = settings.rdExpiresAt()
        if (expiresAt > nowMillis() + REFRESH_MARGIN_MS) return current

        // Token expired or close to it: refresh it.
        return runCatching { refreshNow(refresh) }.getOrElse { current }
    }

    private suspend fun refreshNow(refreshToken: String): String {
        val clientId = settings.rdClientId().ifEmpty { return "" }
        val clientSecret = settings.rdClientSecret().ifEmpty { return "" }
        val token = api.refresh(
            clientId = clientId,
            clientSecret = clientSecret,
            refreshToken = refreshToken
        )
        settings.setRdAuth(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            clientId = clientId,
            clientSecret = clientSecret,
            expiresAtMillis = nowMillis() + token.expiresInSeconds * 1_000L
        )
        return token.accessToken
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    data class PairCredentials(val clientId: String, val clientSecret: String)

    companion object {
        // Refresh proactively when the token has less than 60 s left.
        private const val REFRESH_MARGIN_MS = 60_000L

        @Volatile private var instance: RealDebridAuthRepository? = null
        fun get(context: Context): RealDebridAuthRepository =
            instance ?: synchronized(this) {
                instance ?: RealDebridAuthRepository(context.applicationContext).also { instance = it }
            }
    }
}
