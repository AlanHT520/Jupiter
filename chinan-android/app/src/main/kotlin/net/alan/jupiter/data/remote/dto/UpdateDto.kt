package net.alan.jupiter.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GiteeRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val prerelease: Boolean = false,
    val created_at: String = "",
    val assets: List<GiteeAsset> = emptyList()
)

@Serializable
data class GiteeAsset(
    val name: String = "",
    val browser_download_url: String = ""
)