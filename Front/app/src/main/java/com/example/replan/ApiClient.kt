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
// AI 일정 생성 요청
// POST /schedules/generate
// =========================================================================


// =========================================================================
// AI 일정 재배치 요청
// POST /schedules/replan
// =========================================================================
data class ReplanScheduleApiRequest(
    val userId: Long,
    val replanFromTime: String,
    val completedTaskIds: List<Long> = emptyList(),
    val postponedTaskIds: List<Long> = emptyList()
)

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
     * 요청 JSON:
     * {
     *   "userId": 1
     * }
     */
    @POST("schedules/generate")
    suspend fun generateSchedules(
        @Body request: GenerateScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * AI 일정 재배치
     * POST /schedules/replan
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
     * POST /schedules/tasks
     *
     * 이 부분은 백엔드 Controller 확인 전까지
     * 기존 프론트 규격을 유지함.
     */
    @POST("schedules/tasks")
    suspend fun createTask(
        @Query("userId") userId: Long = 1L,
        @Body request: CreateTaskApiRequest
    ): Response<ResponseBody>

    /**
     * 고정 일정 신규 등록
     * POST /schedules/fixed-schedules
     *
     * 이 부분은 백엔드 Controller 확인 전까지
     * 기존 프론트 규격을 유지함.
     */
    @POST("schedules/fixed-schedules")
    suspend fun createFixedSchedule(
        @Query("userId") userId: Long = 1L,
        @Body request: CreateFixedScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * 일정 상태 업데이트
     *
     * 현재 백엔드에 /schedules/status가 있는지
     * 아직 최종 확인되지 않았으므로 기존 코드를 유지함.
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
     * Android 에뮬레이터에서
     * PC의 localhost:8080으로 접근하는 주소
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