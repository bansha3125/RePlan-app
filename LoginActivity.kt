package com.example.replan

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoRegister = findViewById<Button>(R.id.btnGoRegister)

        // 1. [로그인] 버튼 클릭 시 -> 메인 주간 플래너 화면으로 이동!
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 4주차 기본 흐름 검증: 아주 가벼운 예외처리만 적용
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 모두 입력해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 메인 플래너 화면(MainActivity)으로 넘어가기 위한 Intent 생성
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

            // 로그인 완료 후 뒤로가기를 눌렀을 때 다시 로그인 화면이 나오는 것을 방지하기 위해 액티비티 종료
            finish()
        }

        // 2. [회원가입] 버튼 클릭 시 (지금은 간단한 메시지만 출력)
        btnGoRegister.setOnClickListener {
            Toast.makeText(this, "회원가입 기능은 다음 주차에 연동됩니다! 😉", Toast.LENGTH_SHORT).show()
        }
    }
}