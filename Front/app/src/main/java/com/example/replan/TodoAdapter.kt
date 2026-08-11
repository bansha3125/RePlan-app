package com.example.replan

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val todoList: List<Todo>,
    private val onItemClick: (Todo) -> Unit,
    private val onMenuClick: (Todo) -> Unit
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView as CardView
        val tvName: TextView = itemView.findViewById(R.id.tvTodoItemName)
        val tvPriority: TextView = itemView.findViewById(R.id.tvTodoItemPriority)
        val tvTime: TextView = itemView.findViewById(R.id.tvTodoItemTime)
        val tvAiSteps: TextView = itemView.findViewById(R.id.tvTodoItemAiSteps)
        val btnMenu: TextView = itemView.findViewById(R.id.tvMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = todoList[position]

        holder.tvName.text = todo.name
        holder.tvPriority.text = "우선순위: ${todo.priority}"
        holder.tvTime.text = "⏳ ${todo.expectedTime}시간 소요"

        // AI 쪼개기 설정 여부에 따른 뱃지 노출
        if (todo.desiredSteps > 0) {
            holder.tvAiSteps.visibility = View.VISIBLE
            holder.tvAiSteps.text = "🤖 AI ${todo.desiredSteps}단계 작업 분해 요청됨"
        } else {
            holder.tvAiSteps.visibility = View.GONE
        }

        // 완료 처리 상태에 따른 회색 스타일 및 취소선 적용
        if (todo.isCompleted) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
            holder.tvName.paintFlags = holder.tvName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvName.setTextColor(Color.parseColor("#888888"))
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE)
            holder.tvName.paintFlags = holder.tvName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvName.setTextColor(Color.parseColor("#333333"))
        }

        holder.itemView.setOnClickListener { onItemClick(todo) }
        holder.btnMenu.setOnClickListener { onMenuClick(todo) }
    }

    override fun getItemCount(): Int = todoList.size
}