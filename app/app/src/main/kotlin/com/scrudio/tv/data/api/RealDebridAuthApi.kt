package com.scrudio.tv.data.api

import com.scrudio.tv.data.dto.DeviceCodeDto
import com.scrudio.tv.data.dto.DeviceCredentialsDto
import com.scrudio.tv.data.dto.TokenDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Real-Debrid OAuth device-flow endpoints.
 *
 * `client_id` is the open-source community client (`X245A4XAIBGVM`) — RD
 * accepts it for any third-party device pairing. After the user authorizes
 * the device on their phone, RD returns a per-device `client_id`/`client_secret`
 * pair which is then exchanged for a real access_token.
 */
interface RealDebridAuthApi {

    /** Step 1 — fetch the user_code that the user types on real-debrid.com/device. */
    @GET("oauth/v2/device/code")
    suspend fun requestDeviceCode(
        @Query("client_id") clientId: String,
        @Query("new_credentials") newCredentials: String = "yes"
    ): DeviceCodeDto

    /**
     * Step 2 — poll until the user authorizes us. The endpoint returns:
     *  - **200** with [DeviceCredentialsDto] when authorized
     *  - **400** (auth pending or expired) → caller catches HttpException and retries
     */
    @GET("oauth/v2/device/credentials")
    suspend fun pollCredentials(
        @Query("client_id") clientId: String,
        @Query("code") deviceCode: String
    ): DeviceCredentialsDto

    /** Step 3 — exchange device_code + per-device credentials for a real OAuth token. */
    @FormUrlEncoded
    @POST("oauth/v2/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") deviceCode: String,
        @Field("grant_type") grantType: String = "http://oauth.net/grant_type/device/1.0"
    ): TokenDto

    /** Refresh an expired access_token without re-pairing (long-lived refresh_token). */
    @FormUrlEncoded
    @POST("oauth/v2/token")
    suspend fun refresh(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") refreshToken: String,
        @Field("grant_type") grantType: String = "http://oauth.net/grant_type/device/1.0"
    ): TokenDto
}
