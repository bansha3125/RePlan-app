package com.example.replan

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val todoList: List<Todo>,
    private val onItemClick: (Todo) -> Unit,
    private val onMenuClick: (Todo) -> Unit
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTodoItemName)
        val tvPriority: TextView = view.findViewById(R.id.tvTodoItemPriority)
        val tvDeadline: TextView = view.findViewById(R.id.tvTodoItemDeadline)
        val tvTime: TextView = view.findViewById(R.id.tvTodoItemTime)
        val tvAiSteps: TextView = view.findViewById(R.id.tvTodoItemAiSteps)
        val tvMenu: TextView? = view.findViewById(R.id.tvMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = todoList[position]

        // 1. 할 일 제목
        holder.tvName.visibility = View.VISIBLE
        holder.tvName.text = if (todo.name.isNullOrEmpty()) "제목 없음" else todo.name

        // 2. 소요 시간
        holder.tvTime.text = "⏳ ${todo.expectedTime}시간 소요"

        // 3. 우선순위 뱃지 및 색상 설정
        holder.tvPriority.visibility = View.VISIBLE
        holder.tvPriority.text = "우선순위: ${todo.priority}"
        when (todo.priority) {
            "상" -> {
                holder.tvPriority.setBackgroundColor(Color.parseColor("#FFEBEE"))
                holder.tvPriority.setTextColor(Color.parseColor("#C62828"))
            }
            "중" -> {
                holder.tvPriority.setBackgroundColor(Color.parseColor("#E8EAF6"))
                holder.tvPriority.setTextColor(Color.parseColor("#283593"))
            }
            "하" -> {
                holder.tvPriority.setBackgroundColor(Color.parseColor("#E8F5E9"))
                holder.tvPriority.setTextColor(Color.parseColor("#2E7D32"))
            }
            else -> {
                holder.tvPriority.setBackgroundColor(Color.parseColor("#E8EAF6"))
                holder.tvPriority.setTextColor(Color.parseColor("#283593"))
            }
        }

        // 4. 마감 기한 텍스트
        holder.tvDeadline.text = when (todo.deadlineType) {
            "DATE" -> "⏰ 특정 날짜 기준 마감"
            "SCHEDULE" -> "⏰ 완료 목표: ${todo.specificScheduleName ?: "특정 일정"} 전 완료"
            else -> "⏰ 마감 없음"
        }

        // 5. AI 작업 분해 뱃지
        if (todo.desiredSteps > 0) {
            holder.tvAiSteps.visibility = View.VISIBLE
            holder.tvAiSteps.text = "🤖 AI ${todo.desiredSteps}단계 작업 분해 요청됨"
        } else {
            holder.tvAiSteps.visibility = View.GONE
        }

        // 6. 완료 상태 스타일 처리 (흐려짐 및 취소선)
        if (todo.isCompleted) {
            holder.itemView.alpha = 0.45f
            holder.tvName.paintFlags = holder.tvName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvName.setTextColor(Color.parseColor("#888888"))
        } else {
            holder.itemView.alpha = 1.0f
            holder.tvName.paintFlags = holder.tvName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvName.setTextColor(Color.parseColor("#212121"))
        }

        // 7. 카드 본문 클릭 리스너 (체크박스 AI 추천 바텀시트 열기)
        holder.itemView.setOnClickListener {
            onItemClick(todo)
        }

        // 8. 오른쪽 점 세 개 (⋮) 클릭 리스너 (수정/삭제 팝업 열기)
        holder.tvMenu?.setOnClickListener {
            onMenuClick(todo)
        }
    }

    override fun getItemCount(): Int = todoList.size
}