package com.example.replan

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 2초(2000ms) 동안 로고 띄운 후 바로 MainActivity로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish() // 뒤로가기 버튼 눌렀을 때 다시 로딩 화면으로 돌아오지 않도록 종료
        }, 2000)
    }
}