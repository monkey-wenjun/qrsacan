package com.example.qrscan

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.example.qrscan.R
import com.example.qrscan.utils.CryptoUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.app.Dialog
import androidx.core.content.ContextCompat
import android.util.Log

class QRCodeAdapter(private val context: Context) : RecyclerView.Adapter<QRCodeAdapter.QRCodeViewHolder>() {
    private val allQRCodes = mutableSetOf<QRCode>()
    private val displayList = mutableListOf<QRCode>()
    private val prefs = context.getSharedPreferences("qr_codes", Context.MODE_PRIVATE)

    init {
        loadQRCodes()
        updateDisplayList()
    }

    private fun loadQRCodes() {
        try {
            val savedCodes = prefs.getStringSet("codes", setOf()) ?: setOf()
            savedCodes.forEach { encryptedStr ->
                try {
                    val decrypted = CryptoUtils.decrypt(encryptedStr)
                    val parts = decrypted.split("|")
                    if (parts.size >= 2) {  // 只需要内容和时间戳
                        val content = parts[0]
                        val timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                        
                        val qrCode = QRCode(
                            content = content,
                            timestamp = timestamp
                        )
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
            val dataString = "${qrCode.content}|${qrCode.timestamp}"
            CryptoUtils.encrypt(dataString)
        }.toSet()
        
        prefs.edit().apply {
            clear()
            putStringSet("codes", codesToSave)
            apply()
        }
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
        holder.tvStatus.visibility = View.GONE  // 隐藏状态文本
        
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(
                qrCode.content,
                BarcodeFormat.QR_CODE,
                400,
                400
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

    fun addQRCode(qrCode: QRCode, save: Boolean = true) {
        if (allQRCodes.add(qrCode)) {
            if (save) {
                saveQRCodes()
            }
            updateDisplayList()
        }
    }

    private fun updateDisplayList() {
        displayList.clear()
        displayList.addAll(allQRCodes.sortedByDescending { it.timestamp })
        notifyDataSetChanged()
    }

    fun exportToCSV(): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val fileName = "parking_tickets_$timestamp.csv"
        val file = File(context.getExternalFilesDir(null), fileName)

        file.printWriter().use { out ->
            out.println("内容,时间")
            displayList.forEach { qrCode ->
                out.println("${qrCode.content},${dateFormat.format(Date(qrCode.timestamp))}")
            }
        }

        return file
    }

    fun removeQRCode(position: Int) {
        val qrCode = displayList[position]
        allQRCodes.remove(qrCode)
        updateDisplayList()
        saveQRCodes()
    }

    fun getQRCodeAt(position: Int): QRCode {
        return displayList[position]
    }
} 