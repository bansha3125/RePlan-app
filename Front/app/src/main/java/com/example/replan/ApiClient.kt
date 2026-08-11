package com.example.replan

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ScheduleApiService {

    /**
     * 1. 주간 일정 조회 (GET /schedules/weekly?weekStartDate=2026-08-11)
     */
    @GET("schedules/weekly")
    suspend fun getWeeklySchedules(
        @Query("weekStartDate") weekStartDate: String? = null
    ): WeeklyScheduleResponse

    /**
     * 2. 일정 완료 상태 및 시간 변경 (PATCH /schedules/blocks/{blockId})
     */
    @PATCH("schedules/blocks/{blockId}")
    suspend fun updateGeneratedScheduleStatus(
        @Path("blockId") blockId: String,
        @Body request: UpdateScheduleStatusApiRequest
    ): Response<ResponseBody>

    /**
     * 3. AI 스케줄 생성 요청 (POST /schedules/generate)
     */
    @POST("schedules/generate")
    suspend fun generateSchedules(
        @Body request: GenerateScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * 4. 고정 일정 추가 (POST /schedules/fixed-schedules)
     */
    @POST("schedules/fixed-schedules")
    suspend fun createFixedSchedule(
        @Body request: CreateFixedScheduleApiRequest
    ): Response<ResponseBody>

    /**
     * 5. 일반 할 일 추가 (POST /schedules/tasks)
     */
    @POST("schedules/tasks")
    suspend fun createTask(
        @Body request: CreateTaskApiRequest
    ): Response<ResponseBody>

    /**
     * 등록된 할 일 목록 조회 API (GET /schedules/tasks)
     */
    @GET("schedules/tasks")
    suspend fun getTasks(): List<TaskResponse>

    /**
     * AI 일정 재배치 API (POST /schedules/replan)
     */
    @POST("schedules/replan")
    suspend fun replanSchedules(
        @Body request: ReplanApiRequest
    ): Response<ResponseBody>

    /**
     * 할 일 수정 API (PATCH /schedules/tasks/{taskId})
     */
    @PATCH("schedules/tasks/{taskId}")
    suspend fun updateTask(
        @Path("taskId") taskId: Long,
        @Body request: CreateTaskApiRequest
    ): Response<ResponseBody>

    /**
     * 할 일 삭제 API (DELETE /schedules/tasks/{taskId})
     */
    @DELETE("schedules/tasks/{taskId}")
    suspend fun deleteTask(
        @Path("taskId") taskId: Long
    ): Response<ResponseBody>

    /**
     * 할 일 완료 상태 업데이트 API (PATCH /schedules/tasks/{taskId}?completed=true)
     */
    @PATCH("schedules/tasks/{taskId}")
    suspend fun updateTaskStatus(
        @Path("taskId") taskId: Long,
        @Query("completed") completed: Boolean
    ): Response<ResponseBody>

    /**
     * 고정 일정 목록 조회 API (GET /schedules/fixed-schedules)
     */
    @GET("schedules/fixed-schedules")
    suspend fun getFixedSchedules(): List<FixedScheduleDto>
}

object ApiClient {

    private const val BASE_URL = "https://darci-jaggiest-intendedly.ngrok-free.dev/"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 모든 API 요청 Header에 X-Device-UUID 및 Content-Type 자동 삽입
    private val headerInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val uuid = if (::appContext.isInitialized) {
            DeviceUuidManager.getDeviceUuid(appContext)
        } else {
            "DEFAULT_REPLAN_DEVICE_ID"
        }

        val newRequest = originalRequest.newBuilder()
            .header("X-Device-UUID", uuid)
            .header("Content-Type", "application/json")
            .build()

        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val service: ScheduleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScheduleApiService::class.java)
    }
}