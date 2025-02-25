package com.awen.qrscan

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
import android.util.Log
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.widget.TextView
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var dataStatsFragment: DataStatsFragment
    private lateinit var qrCodeListFragment: QRCodeListFragment
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var toneGenerator: ToneGenerator
    private var isScanning = true
    private var lastScannedContent: String? = null
    private var lastScanTime: Long = 0
    private val SCAN_INTERVAL = 1000L
    private val CAMERA_PERMISSION_REQUEST = 100
    private lateinit var tvScanTip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 设置 Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "二维码扫描"
            elevation = 4f
        }
        
        tvScanTip = findViewById(R.id.tvScanTip)
        cameraExecutor = Executors.newSingleThreadExecutor()
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        
        setupViewPager()
        setupTabLayout()
        setupCamera()
        
        // 修改返回键处理
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val cameraContainer = findViewById<FrameLayout>(R.id.cameraContainer)
                val fabScan = findViewById<FloatingActionButton>(R.id.fabScan)
                
                if (cameraContainer.visibility == View.VISIBLE) {
                    cameraContainer.visibility = View.GONE
                    fabScan.visibility = View.VISIBLE
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        dataStatsFragment = DataStatsFragment()
        qrCodeListFragment = QRCodeListFragment().apply {
            adapter = QRCodeAdapter(this@MainActivity)
        }
        
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            
            override fun createFragment(position: Int) = when(position) {
                0 -> dataStatsFragment
                else -> qrCodeListFragment
            }
        }
        
        viewPager.adapter = pagerAdapter
    }
    
    private fun setupTabLayout() {
        tabLayout = findViewById(R.id.tabLayout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "数据统计"
                else -> "二维码列表"
            }
        }.attach()
    }
    
    private fun setupCamera() {
        val fabScan = findViewById<FloatingActionButton>(R.id.fabScan)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val cameraContainer = findViewById<FrameLayout>(R.id.cameraContainer)

        fabScan.setOnClickListener {
            if (checkCameraPermission()) {
                fabScan.visibility = View.GONE
                cameraContainer.visibility = View.VISIBLE
                tvScanTip.text = "请将二维码对准扫描框"
                tvScanTip.setTextColor(ContextCompat.getColor(this, R.color.white))
                startCamera()
            }
        }

        // 添加返回按钮点击事件
        btnBack.setOnClickListener {
            cameraContainer.visibility = View.GONE
            fabScan.visibility = View.VISIBLE
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
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            try {
                                processImageProxy(imageProxy)
                            } catch (e: Exception) {
                                Log.e("CameraX", "图像分析失败", e)
                                imageProxy.close()
                            }
                        }
                    }

                // 配置相机选择器
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                try {
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
                } catch (e: Exception) {
                    Log.e("CameraX", "用例绑定失败", e)
                }

            } catch (e: Exception) {
                Log.e("CameraX", "相机启动失败", e)
                Toast.makeText(this, "相机启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val scanner = BarcodeScanning.getClient()

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                val currentTime = System.currentTimeMillis()
                                if (value != lastScannedContent || 
                                    currentTime - lastScanTime > SCAN_INTERVAL) {
                                    lastScannedContent = value
                                    lastScanTime = currentTime
                                    processQRCode(value)
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
                findViewById<FloatingActionButton>(R.id.fabScan).visibility = View.GONE
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processQRCode(content: String) {
        val qrCode = QRCode(content = content)
        runOnUiThread {
            if (::qrCodeListFragment.isInitialized && qrCodeListFragment.isAdded) {
                if (qrCodeListFragment.adapter.addQRCode(qrCode)) {
                    // 扫码成功
                    tvScanTip.text = "扫码成功！"
                    tvScanTip.setTextColor(ContextCompat.getColor(this, R.color.teal_200))
                    updateDataStats()
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    
                    // 2秒后恢复提示文本
                    tvScanTip.postDelayed({
                        tvScanTip.text = "请将二维码对准扫描框"
                        tvScanTip.setTextColor(ContextCompat.getColor(this, R.color.white))
                    }, 2000)
                } else {
                    // 重复扫码
                    tvScanTip.text = "该二维码已扫描过"
                    tvScanTip.setTextColor(ContextCompat.getColor(this, R.color.accent))
                    
                    // 2秒后恢复提示文本
                    tvScanTip.postDelayed({
                        tvScanTip.text = "请将二维码对准扫描框"
                        tvScanTip.setTextColor(ContextCompat.getColor(this, R.color.white))
                    }, 2000)
                }
                resetScanState()
            }
        }
    }
    
    private fun updateDataStats() {
        if (::qrCodeListFragment.isInitialized && qrCodeListFragment.isAdded) {
            val adapter = qrCodeListFragment.adapter
            val totalCount = adapter.getItemCount()
            val todayCount = adapter.getTodayCount()
            dataStatsFragment.updateStats(totalCount, todayCount)
        }
    }

    private fun resetScanState() {
        isScanning = true
        lastScannedContent = null
        lastScanTime = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        toneGenerator.release()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                exportData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportData() {
        if (::qrCodeListFragment.isInitialized && qrCodeListFragment.isAdded) {
            try {
                val file = qrCodeListFragment.adapter.exportToCSV()
                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "二维码数据导出")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(intent, "分享导出文件"))
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("Export", "导出失败", e)
            }
        }
    }
} 