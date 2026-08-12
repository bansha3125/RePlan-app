package com.example.replan

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val todoList: List<Todo>,
    private val onItemClick: (Todo) -> Unit,
    private val onEditClick: (Todo) -> Unit,   // ★ 수정 클릭 전용 콜백
    private val onDeleteClick: (Todo) -> Unit  // ★ 삭제 클릭 전용 콜백
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView? = itemView as? CardView
        private val context = itemView.context
        private val resources = context.resources
        private val packageName = context.packageName

        private fun <T : View> findView(idName: String): T? {
            val id = resources.getIdentifier(idName, "id", packageName)
            return if (id != 0) itemView.findViewById(id) else null
        }

        val tvName: TextView? = findView("tvTodoItemName") ?: findView("tvTodoName")
        val tvTime: TextView? = findView("tvTodoItemTime") ?: findView("tvExpectedTime")
        val tvPriority: TextView? = findView("tvTodoItemPriority") ?: findView("tvPriority")
        val btnMenu: View? = findView("tvMenu") ?: findView("btnMenu")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val layoutId = parent.context.resources.getIdentifier("item_todo", "layout", parent.context.packageName)
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = todoList[position]

        holder.tvName?.text = todo.name

        // 소요시간 & AI 단계 표시 부분
        val stepsText = if (todo.desiredSteps > 0) {
            " | 🤖 ${todo.desiredSteps}단계"  // 쪼개기 설정 시 (3단계, 5단계 등)
        } else {
            " | 🤖 쪼개기 X"                 // 쪼개기 안 함 선택 시
        }

        holder.tvTime?.text = "⌛ ${todo.expectedTime}시간 소요$stepsText"

        // ★ [가독성 개선] 우선순위 상/중/하 별 색상 적용
        holder.tvPriority?.apply {
            text = "우선순위: ${todo.priority}"

            when (todo.priority) {
                "상" -> {
                    setBackgroundColor(Color.parseColor("#FFEBEE")) // 연한 빨강
                    setTextColor(Color.parseColor("#C62828"))       // 선명한 빨강
                }
                "중" -> {
                    setBackgroundColor(Color.parseColor("#E8EAF6")) // 연한 남색
                    setTextColor(Color.parseColor("#1A237E"))       // 진한 남색
                }
                "하" -> {
                    setBackgroundColor(Color.parseColor("#F5F5F5")) // 연한 회색
                    setTextColor(Color.parseColor("#616161"))       // 짙은 회색
                }
                else -> {
                    setBackgroundColor(Color.parseColor("#E8EAF6"))
                    setTextColor(Color.parseColor("#1A237E"))
                }
            }
        }

        if (todo.isCompleted) {
            holder.cardView?.setCardBackgroundColor(Color.parseColor("#E0E0E0"))
            holder.tvName?.apply {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setTextColor(Color.parseColor("#757575"))
            }
        } else {
            holder.cardView?.setCardBackgroundColor(Color.WHITE)
            holder.tvName?.apply {
                paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                setTextColor(Color.parseColor("#212121"))
            }
        }

        // 전체 카드 클릭 -> AI 쪼개기 바텀시트
        holder.itemView.setOnClickListener { onItemClick(todo) }

        // 점 3개 메뉴 선택 시 수정/삭제 바로 연결
        holder.btnMenu?.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, 1, 0, "✏️ 수정하기")
            popup.menu.add(0, 2, 1, "🗑️ 삭제하기")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        onEditClick(todo) // ✏️ 수정 바텀시트 연결
                        true
                    }
                    2 -> {
                        onDeleteClick(todo) // 🗑️ 삭제 확인 다이얼로그 연결
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = todoList.size
}