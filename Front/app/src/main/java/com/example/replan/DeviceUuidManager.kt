package com.example.replan

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object DeviceUuidManager {
    private const val PREF_NAME = "replan_prefs"
    private const val KEY_DEVICE_UUID = "X-Device-UUID"

    /**
     * UUID v4 형식의 기기 고유 식별자 가져오기 (없으면 생성 후 영구 저장)
     */
    fun getDeviceUuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var uuid = prefs.getString(KEY_DEVICE_UUID, null)

        if (uuid.isNullOrEmpty()) {
            uuid = UUID.randomUUID().toString() // UUID v4 생성
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }

        return uuid
    }
}