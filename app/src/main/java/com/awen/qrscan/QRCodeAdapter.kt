package com.awen.qrscan

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.app.Dialog
import android.widget.Toast

class QRCodeAdapter(private val context: Context) : RecyclerView.Adapter<QRCodeAdapter.QRCodeViewHolder>() {
    private val allQRCodes = mutableSetOf<QRCode>()
    private val displayList = mutableListOf<QRCode>()
    private val prefs = context.getSharedPreferences("qr_codes", Context.MODE_PRIVATE)
    private var onDataChangeListener: ((Int, Int) -> Unit)? = null

    init {
        loadQRCodes()
        updateDisplayList()
        removeDuplicates()
    }

    class QRCodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivQRCode: ImageView = view.findViewById(R.id.ivQRCode)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QRCodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_qrcode, parent, false)
        return QRCodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: QRCodeViewHolder, position: Int) {
        val qrCode = displayList[position]
        holder.tvContent.text = qrCode.content
        holder.tvStatus.visibility = View.GONE

        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(
                qrCode.content,
                BarcodeFormat.QR_CODE,
                200,
                200
            )
            holder.ivQRCode.setImageBitmap(bitmap)
            
            holder.itemView.setOnClickListener {
                showQRCodeDialog(qrCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount() = displayList.size

    fun setOnDataChangeListener(listener: (Int, Int) -> Unit) {
        onDataChangeListener = listener
        // 初始化时触发一次
        notifyDataChanged()
    }

    private fun notifyDataChanged() {
        onDataChangeListener?.invoke(displayList.size, getTodayCount())
    }

    fun addQRCode(qrCode: QRCode): Boolean {
        // 检查是否已存在相同内容的二维码
        val existingQRCode = allQRCodes.find { it.content == qrCode.content }
        if (existingQRCode != null) {
            return false  // 已存在相同内容的二维码，返回false
        }

        // 添加新的二维码
        allQRCodes.add(qrCode)
        saveQRCodes()
        updateDisplayList()
        
        // 通知数据变化
        notifyDataChanged()
        return true
    }

    fun removeQRCode(position: Int) {
        if (position < 0 || position >= displayList.size) return
        
        val qrCode = displayList[position]
        allQRCodes.remove(qrCode)
        updateDisplayList()
        saveQRCodes()
        notifyDataChanged()
        
        // 显示删除成功提示
        Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
    }

    private fun loadQRCodes() {
        try {
            val savedCodes = prefs.getStringSet("codes", setOf()) ?: setOf()
            savedCodes.forEach { encryptedStr ->
                try {
                    val parts = encryptedStr.split("|")
                    if (parts.size >= 2) {
                        val content = parts[0]
                        val timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                        val qrCode = QRCode(content = content, timestamp = timestamp)
                        allQRCodes.add(qrCode)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveQRCodes() {
        val codesToSave = allQRCodes.map { qrCode ->
            "${qrCode.content}|${qrCode.timestamp}"
        }.toSet()
        
        prefs.edit().apply {
            clear()
            putStringSet("codes", codesToSave)
            apply()
        }
    }

    private fun updateDisplayList() {
        displayList.clear()
        displayList.addAll(allQRCodes.sortedByDescending { it.timestamp })
        notifyDataSetChanged()
    }

    fun getTodayCount(): Int {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        
        return displayList.count { it.timestamp >= todayStart }
    }

    private fun showQRCodeDialog(qrCode: QRCode) {
        val dialog = Dialog(context)
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.encodeBitmap(
            qrCode.content,
            BarcodeFormat.QR_CODE,
            800,
            800
        )
        imageView.setImageBitmap(bitmap)
        
        dialog.setContentView(imageView)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    fun exportToCSV(): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val fileName = "qrcodes_$timestamp.csv"
        val file = File(context.getExternalFilesDir(null), fileName)

        file.printWriter().use { out ->
            out.println("序号,内容,扫描时间")
            displayList.forEachIndexed { index, qrCode ->
                out.println("${index + 1},${qrCode.content},${dateFormat.format(Date(qrCode.timestamp))}")
            }
        }

        return file
    }

    fun removeDuplicates() {
        val uniqueQRCodes = allQRCodes.distinctBy { it.content }
        if (uniqueQRCodes.size < allQRCodes.size) {
            allQRCodes.clear()
            allQRCodes.addAll(uniqueQRCodes)
            updateDisplayList()
            // 更新统计数据
            notifyDataChanged()
        }
    }
} 