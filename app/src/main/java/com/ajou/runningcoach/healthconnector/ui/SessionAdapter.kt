package com.ajou.runningcoach.healthconnector.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ajou.runningcoach.healthconnector.data.model.RunningSession
import com.ajou.runningcoach.healthconnector.databinding.ItemSessionBinding
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class SessionAdapter : ListAdapter<RunningSession, SessionAdapter.ViewHolder>(DiffCallback()) {

    private val selectedIds = mutableSetOf<String>()
    private val formatter = DateTimeFormatter
        .ofPattern("yyyy/MM/dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun clearSelection() {
        selectedIds.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(session: RunningSession) {
            binding.tvDate.text = formatter.format(session.startTime)
            binding.tvDistance.text = "거리: %.2f km".format(session.totalDistanceMeters / 1000)
            binding.tvSteps.text = "걸음수: ${session.totalSteps}"

            val avgBpm = if (session.heartRateSamples.isNotEmpty())
                session.heartRateSamples.map { it.bpm }.average().roundToInt()
            else 0
            binding.tvHeartRate.text = "평균 심박수: $avgBpm bpm"
            binding.tvSampleCount.text = "샘플 수: ${session.heartRateSamples.size}개 (1분 단위)"

            // 선택 상태를 반영 (리사이클 시 이전 상태 덮어쓰기)
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = session.id in selectedIds

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedIds.add(session.id)
                else selectedIds.remove(session.id)
            }

            // 카드 전체를 눌러도 체크박스 토글
            binding.root.setOnClickListener {
                binding.checkbox.toggle()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RunningSession>() {
        override fun areItemsTheSame(a: RunningSession, b: RunningSession) = a.id == b.id
        override fun areContentsTheSame(a: RunningSession, b: RunningSession) = a == b
    }
}
