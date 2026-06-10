package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// Google Sheets API Model Classes
data class SpreadsheetProperties(val title: String)
data class CreateSpreadsheetRequest(val properties: SpreadsheetProperties)
data class CreateSpreadsheetResponse(val spreadsheetId: String, val spreadsheetUrl: String?)

data class AppendValuesRequest(
    val range: String,
    val majorDimension: String = "ROWS",
    val values: List<List<Any>>
)
data class AppendValuesResponse(
    val spreadsheetId: String,
    val updatedRange: String,
    val updatedRows: Int
)

data class GetValuesResponse(
    val range: String,
    val majorDimension: String,
    val values: List<List<Any>>?
)

interface GoogleSheetsApiService {
    @POST("v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Header("Authorization") authHeader: String,
        @Body request: CreateSpreadsheetRequest
    ): CreateSpreadsheetResponse

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body request: AppendValuesRequest
    ): AppendValuesResponse

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body request: AppendValuesRequest
    ): AppendValuesResponse

    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String
    ): GetValuesResponse
}

object GoogleSheetsClient {
    private const val BASE_URL = "https://sheets.googleapis.com/"

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

    val apiService: GoogleSheetsApiService by lazy {
        retrofit.create(GoogleSheetsApiService::class.java)
    }
}
