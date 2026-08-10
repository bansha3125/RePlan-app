package com.example.replan

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// =========================================================================
// 백엔드 API 서비스 인터페이스
// =========================================================================
interface ScheduleApiService {

    /**
     * 주간 일정 조회
     * GET /schedules/weekly?userId=1
     */
    @GET("schedules/weekly")
    suspend fun getWeeklySchedules(
        @Query("userId") userId: Long = 1L,
        @Query("weekStartDate") weekStartDate: String? = null
    ): WeeklyScheduleResponse

    /**
     * AI 일정 자동 생성 API (POST /schedules/generate)
     * 🚀 백엔드 @RequestBody Map<String, Object> 규격에 맞춰 Body 전송
     */
    @POST("schedules/generate")
    suspend fun generateSchedules(
        @Body request: GenerateScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * AI 일정 재배치 API (POST /schedules/replan)
     */
    @POST("schedules/replan")
    suspend fun replanSchedules(
        @Body request: ReplanApiRequest
    ): Response<ResponseBody>

    /**
     * 등록된 할 일 목록 조회 API (GET /schedules/tasks)
     */
    @GET("schedules/tasks")
    suspend fun getTasks(
        @Query("userId") userId: Long = 1L
    ): List<TaskResponse>

    /**
     * 할 일 신규 등록
     * POST /schedules/tasks?userId=1
     */
    @POST("schedules/tasks")
    suspend fun createTask(
        @Body request: CreateTaskApiRequest
    ): Response<ResponseBody>

    /**
     * 할 일 완료 상태 업데이트 API
     * PATCH /schedules/tasks/{taskId}?completed=true
     */
    @PATCH("schedules/tasks/{taskId}")
    suspend fun updateTaskStatus(
        @Path("taskId") taskId: Long,
        @Query("completed") completed: Boolean
    ): Response<ResponseBody>

    /**
     * 고정 일정 신규 등록
     * POST /schedules/fixed-schedules?userId=1
     */
    @POST("schedules/fixed-schedules")
    suspend fun createFixedSchedule(
        @Body request: CreateFixedScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * 고정 일정 목록 조회 API (GET /schedules/fixed-schedules)
     */
    @GET("schedules/fixed-schedules")
    suspend fun getFixedSchedules(
        @Query("userId") userId: Long = 1L
    ): List<FixedScheduleDto>
}

// =========================================================================
// Retrofit Client
// =========================================================================
object ApiClient {

    // ngrok URL 설정 (반드시 http:// 또는 https:// 포함 및 끝에 / 필요)
    private const val BASE_URL = "https://darci-jaggiest-intendedly.ngrok-free.dev/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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