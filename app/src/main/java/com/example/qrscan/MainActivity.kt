package com.example.qrscan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.activity.OnBackPressedCallback
import android.content.Intent
import androidx.core.content.FileProvider
import android.view.Menu
import android.view.MenuItem
import android.graphics.Bitmap
import android.provider.MediaStore
import android.os.Build
import android.graphics.ImageDecoder
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.tabs.TabLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import android.widget.TextView
import android.widget.LinearLayout
import android.util.Log
import android.view.KeyEvent

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: QRCodeAdapter
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val CAMERA_PERMISSION_REQUEST = 100
    private val PICK_IMAGE_REQUEST = 101
    private var isScanning = true
    private var lastScannedContent: String? = null
    private var lastScanTime: Long = 0
    private val SCAN_INTERVAL = 1000L // 1秒内不重复处理相同内容

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        title = getString(R.string.app_name)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val fabScan = findViewById<FloatingActionButton>(R.id.fabScan)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QRCodeAdapter(this)
        recyclerView.adapter = adapter

        cameraExecutor = Executors.newSingleThreadExecutor()

        fabScan.setOnClickListener {
            fabScan.visibility = View.GONE
            openCamera()  // 直接打开相机
        }

        // 修改返回键处理
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val cameraContainer = findViewById<FrameLayout>(R.id.cameraContainer)
                if (cameraContainer.visibility == View.VISIBLE) {
                    cameraContainer.visibility = View.GONE
                    resetScanState()
                    fabScan.visibility = View.VISIBLE
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // 添加滑动删除功能
        val swipeHandler = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                
                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle("删除确认")
                    .setMessage("确定要删除这个二维码吗？")
                    .setPositiveButton("确定") { _, _ ->
                        adapter.removeQRCode(position)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        // 取消删除，恢复列表项
                        adapter.notifyItemChanged(position)
                    }
                    .setCancelable(false)  // 禁止点击外部取消
                    .create()

                // 禁止返回键关闭对话框
                dialog.setOnKeyListener { _, keyCode, _ ->
                    keyCode == KeyEvent.KEYCODE_BACK
                }
                
                dialog.show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun openCamera() {
        if (checkCameraPermission()) {
            resetScanState()
            findViewById<FrameLayout>(R.id.cameraContainer).visibility = View.VISIBLE
            startCamera()
        }
    }

    private fun checkCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
            return false
        }
        return true
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // 配置预览
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                    }

                // 配置图像分析
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                // 配置相机选择器
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                // 解绑之前的用例
                cameraProvider.unbindAll()

                // 绑定用例到相机
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                // 启用自动对焦
                camera.cameraControl.enableTorch(false)

            } catch (exc: Exception) {
                Log.e("CameraX", "相机启动失败", exc)
                Toast.makeText(this, "相机启动失败: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processQRCode(content: String) {
        val qrCode = QRCode(content = content)
        runOnUiThread {
            adapter.addQRCode(qrCode)
            // 扫描完成后显示扫描按钮
            findViewById<FloatingActionButton>(R.id.fabScan).visibility = View.VISIBLE
        }
    }

    private fun resetScanState() {
        isScanning = true
        lastScannedContent = null
        lastScanTime = 0
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            if (!isScanning) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val scanner = BarcodeScanning.getClient()

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                val currentTime = System.currentTimeMillis()
                                if (isScanning && 
                                    (value != lastScannedContent || 
                                    currentTime - lastScanTime > SCAN_INTERVAL)) {
                                    
                                    isScanning = false
                                    lastScannedContent = value
                                    lastScanTime = currentTime
                                    
                                    // 直接处理扫描结果并返回
                                    runOnUiThread {
                                        findViewById<FrameLayout>(R.id.cameraContainer).visibility = View.GONE
                                        processQRCode(value)
                                        resetScanState()
                                        findViewById<FloatingActionButton>(R.id.fabScan).visibility = View.VISIBLE
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("QRScan", "二维码识别失败", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } catch (e: Exception) {
            Log.e("QRScan", "处理图像失败", e)
            imageProxy.close()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                findViewById<FrameLayout>(R.id.cameraContainer).visibility = View.VISIBLE
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                exportToCSV()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportToCSV() {
        try {
            val file = adapter.exportToCSV()
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "停车券数据")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "导出数据"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 