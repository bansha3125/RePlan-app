package com.example.replan

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// =========================================================================
// 백엔드 API 서비스 인터페이스 (Retrofit2)
// =========================================================================
interface ScheduleApiService {

    /**
     * 주간 일정 조회 API (GET /schedules/weekly)
     */
    @GET("schedules/weekly")
    suspend fun getWeeklySchedules(
        @Query("userId") userId: Long = 1L,
        @Query("weekStartDate") weekStartDate: String? = null
    ): WeeklyScheduleResponse

    /**
     * AI 일정 자동 생성 API (POST /schedules/generate)
     * 🚀 백엔드 @RequestParam 규격에 맞게 @Query 파라미터로 전송
     */
    @POST("schedules/generate")
    suspend fun generateSchedules(
        @Query("userId") userId: Long = 1L
    ): WeeklyScheduleResponse

    /**
     * 등록된 할 일 목록 조회 API (GET /schedules/tasks)
     * 🚀 [추가] 앱 재진입 시 DB에서 Task 목록을 불러와 하단 리스트에 복원
     */
    @GET("schedules/tasks")
    suspend fun getTasks(
        @Query("userId") userId: Long = 1L
    ): List<TaskResponse>

    /**
     * 할 일 신규 등록 API (POST /schedules/tasks)
     */
    @POST("schedules/tasks")
    suspend fun createTask(
        @Query("userId") userId: Long = 1L,
        @Body request: CreateTaskApiRequest
    ): Response<ResponseBody>

    /**
     * 고정 일정 신규 등록 API (POST /schedules/fixed-schedules)
     */
    @POST("schedules/fixed-schedules")
    suspend fun createFixedSchedule(
        @Query("userId") userId: Long = 1L,
        @Body request: CreateFixedScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * 일정 상태 업데이트 API (완료, 고정 등)
     */
    @POST("schedules/status")
    suspend fun updateScheduleStatus(
        @Body request: UpdateScheduleStatusApiRequest
    ): WeeklyScheduleResponse
}

// =========================================================================
// Retrofit Client 싱글톤 객체 (OkHttpClient 타임아웃 & Log Interceptor 설정)
// =========================================================================
object ApiClient {
    private const val BASE_URL = "https://darci-jaggiest-intendedly.ngrok-free.dev/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "69420")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .build()

    val service: ScheduleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScheduleApiService::class.java)
    }
}