package com.awen.qrscan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.*

class DataStatsFragment : Fragment() {
    private lateinit var tvTotalCount: TextView
    private lateinit var tvTodayCount: TextView
    private var pendingTotalCount: Int? = null
    private var pendingTodayCount: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_data_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvTotalCount = view.findViewById(R.id.tvTotalCount)
        tvTodayCount = view.findViewById(R.id.tvTodayCount)

        // 如果有待更新的数据，立即更新
        pendingTotalCount?.let { total ->
            pendingTodayCount?.let { today ->
                updateStats(total, today)
            }
        }
        pendingTotalCount = null
        pendingTodayCount = null
    }

    fun updateStats(totalCount: Int, todayCount: Int) {
        if (!this::tvTotalCount.isInitialized) {
            // 如果视图还没有准备好，保存数据等待视图创建完成
            pendingTotalCount = totalCount
            pendingTodayCount = todayCount
            return
        }
        // 格式化数字显示，确保数字准确性
        tvTotalCount.text = String.format("总计：%d 张卡券", totalCount)
        tvTodayCount.text = String.format("今日新增：%d 张", todayCount)
    }
} 