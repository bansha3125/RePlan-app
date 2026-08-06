package com.example.replan

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val todoList: List<Todo>,
    // 🌟 [추가] 카드가 클릭되었을 때 메인화면에 알려주는 리스너를 매개변수로 받습니다!
    private val onItemClick: (Todo) -> Unit
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTodoItemName)
        val tvPriority: TextView = view.findViewById(R.id.tvTodoItemPriority)
        val tvDeadline: TextView = view.findViewById(R.id.tvTodoItemDeadline)
        val tvTime: TextView = view.findViewById(R.id.tvTodoItemTime)
        val tvAiSteps: TextView = view.findViewById(R.id.tvTodoItemAiSteps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = todoList[position]
        holder.tvName.text = todo.name
        holder.tvTime.text = "⏳ ${todo.expectedTime}시간 소요"
        holder.tvPriority.text = "우선순위: ${todo.priority}"

        // 우선순위별로 색깔 다르게 주기
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
        }

        // 마감 기한 텍스트 동적 분기
        holder.tvDeadline.text = when (todo.deadlineType) {
            "DATE" -> "⏰ 특정 날짜 기준 마감"
            "SCHEDULE" -> "⏰ 완료 목표: ${todo.specificScheduleName} 전 완료"
            else -> "⏰ 마감 없음"
        }

        // AI 작업 분해 뱃지 활성화 여부
        if (todo.desiredSteps > 0) {
            holder.tvAiSteps.visibility = View.VISIBLE
            holder.tvAiSteps.text = "🤖 AI ${todo.desiredSteps}단계 작업 분해 요청됨"
        } else {
            holder.tvAiSteps.visibility = View.GONE
        }

        // 🌟 [추가] 아이템 카드 전체 영역 터치 이벤트 연결!
        holder.itemView.setOnClickListener {
            onItemClick(todo)
        }
    }

    override fun getItemCount(): Int = todoList.size
}