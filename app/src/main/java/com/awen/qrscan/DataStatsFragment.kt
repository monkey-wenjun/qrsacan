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
    }

    fun updateStats(totalCount: Int, todayCount: Int) {
        tvTotalCount.text = "总计：$totalCount 张卡券"
        tvTodayCount.text = "今日新增：$todayCount 张"
    }
} 