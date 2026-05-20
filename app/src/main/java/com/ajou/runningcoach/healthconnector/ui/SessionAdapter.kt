package com.ajou.runningcoach.healthconnector.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.health.connect.client.records.ExerciseSessionRecord
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
            binding.tvDate.text = "${formatter.format(session.startTime)}  [${exerciseTypeName(session.exerciseType)}]"
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

private fun exerciseTypeName(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "러닝"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "트레드밀 러닝"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "걷기"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "하이킹"
    ExerciseSessionRecord.EXERCISE_TYPE_CYCLING -> "자전거"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "수영 (실내)"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "수영 (오픈워터)"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "근력 운동"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "요가"
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "필라테스"
    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "댄스"
    ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "격투기"
    ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "테니스"
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "배드민턴"
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "농구"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "미식축구"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> "호주식 축구"
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "축구"
    ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "배구"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "조정"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "로잉머신"
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "스키"
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "스노보드"
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "골프"
    ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "체조"
    ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "운동 클래스"
    ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "기타 운동"
    else -> "운동 ($type)"
}
