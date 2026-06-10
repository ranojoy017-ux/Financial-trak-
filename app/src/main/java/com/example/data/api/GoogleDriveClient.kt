package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class CreateDriveFileRequest(
    val name: String,
    val mimeType: String = "application/json",
    val description: String? = "Finance automatic ledger backup"
)

data class CreateDriveFileResponse(
    val id: String,
    val name: String,
    val mimeType: String
)

interface GoogleDriveApiService {
    @POST("drive/v3/files")
    suspend fun createFileMetadata(
        @Header("Authorization") authHeader: String,
        @Body request: CreateDriveFileRequest
    ): CreateDriveFileResponse

    @PATCH("upload/drive/v3/files/{fileId}")
    suspend fun uploadFileMedia(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("uploadType") uploadType: String = "media",
        @Body content: okhttp3.RequestBody
    ): okhttp3.ResponseBody
}

object GoogleDriveClient {
    private const val BASE_URL = "https://www.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GoogleDriveApiService by lazy {
        retrofit.create(GoogleDriveApiService::class.java)
    }
}
