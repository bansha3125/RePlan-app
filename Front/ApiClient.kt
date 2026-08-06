package com.example.replan

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
        @Query("userId") userId: Long = 1L
    ): WeeklyScheduleResponse

    /**
     * AI 일정 자동 생성
     * POST /schedules/generate
     *
     * 생성 API 성공 후 getWeeklySchedules()를 다시 호출해야 함.
     */
    @POST("schedules/generate")
    suspend fun generateSchedules(
        @Body request: GenerateScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * AI 일정 재배치
     * POST /schedules/replan
     *
     * 재배치 성공 후 getWeeklySchedules()를 다시 호출해야 함.
     */
    @POST("schedules/replan")
    suspend fun replanSchedules(
        @Body request: ReplanScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * 등록된 할 일 목록 조회
     * GET /schedules/tasks?userId=1
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
        @Query("userId") userId: Long = 1L,
        @Body request: CreateTaskApiRequest
    ): Response<ResponseBody>

    /**
     * 고정 일정 신규 등록
     * POST /schedules/fixed-schedules?userId=1
     */
    @POST("schedules/fixed-schedules")
    suspend fun createFixedSchedule(
        @Query("userId") userId: Long = 1L,
        @Body request: CreateFixedScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * 현재 백엔드에 존재 여부가 확인되지 않은 API.
     * 기존 MainActivity에서 사용 중일 수 있어 일단 유지함.
     * 실제 호출하면 404가 발생할 수 있음.
     */
    @POST("schedules/status")
    suspend fun updateScheduleStatus(
        @Body request: UpdateScheduleStatusApiRequest
    ): WeeklyScheduleResponse
}

// =========================================================================
// Retrofit Client
// =========================================================================
object ApiClient {

    /**
     * Android 에뮬레이터에서 10.0.2.2는 개발 PC의 localhost를 의미함.
     */
    private const val BASE_URL = "http://10.0.2.2:8080/"

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