package com.typeassist.app.data.network

import androidx.annotation.Keep
import com.typeassist.app.data.model.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path

@Keep
interface GitHubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease

    @GET("repos/{owner}/{repo}/releases/tags/{tag}")
    suspend fun getReleaseByTag(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tag") tag: String
    ): GitHubRelease
}
