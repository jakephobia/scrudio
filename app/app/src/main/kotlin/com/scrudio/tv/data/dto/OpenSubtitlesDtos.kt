package com.scrudio.tv.data.dto

import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for OpenSubtitles API responses.
 * Based on OpenSubtitles REST API v1 documentation.
 */

@Serializable
data class SubtitlesResponse(
    val data: List<SubtitleData>,
    val page: Int,
    val total_pages: Int,
    val total_count: Int
)

@Serializable
data class SubtitleData(
    val id: Int,
    val type: String,
    val attributes: SubtitleAttributes
)

@Serializable
data class SubtitleAttributes(
    val subtitle_id: String,
    val language: String,
    val download_count: Int,
    val new_download_count: Int,
    val hd: Boolean,
    val format: String,
    val fps: Double?,
    val votes: Int,
    val ratings: Double,
    val from_trusted: Boolean,
    val foreign_parts_only: Boolean,
    val hearing_impaired: Boolean,
    val auto_translation: Boolean,
    val ai_translated: Boolean,
    val machine_translated: Boolean,
    val upload_date: String,
    val release: String,
    val comments: String,
    val legacy_subtitle_id: Int,
    val uploader: Uploader,
    val feature_details: FeatureDetails,
    val url: String,
    val related_links: RelatedLinks,
    val files: List<SubtitleFile>
)

@Serializable
data class Uploader(
    val uploader_id: Int,
    val name: String,
    val rank: Int
)

@Serializable
data class FeatureDetails(
    val feature_id: Int,
    val title: String,
    val year: Int,
    val imdb_id: Int,
    val tmdb_id: Int,
    val feature_type: String
)

@Serializable
data class RelatedLinks(
    val self: String,
    val download: String
)

@Serializable
data class SubtitleFile(
    val file_id: Int,
    val cd_number: Int,
    val file_name: String
)

@Serializable
data class DownloadLinkRequest(
    val file_id: Int
)

@Serializable
data class DownloadLinkResponse(
    val link: String,
    val file_name: String,
    val remaining: Int,
    val message: String?,
    val reset_time: String?,
    val requested_file_name: String?,
    val takedown: String?
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val status: Int,
    val data: UserData?
)

@Serializable
data class UserData(
    val user_id: Int,
    val name: String,
    val email: String,
    val level: String,
    val vip: Boolean,
    val downloads: Int,
    val remaining_downloads: Int,
    val reset_time: String
)
