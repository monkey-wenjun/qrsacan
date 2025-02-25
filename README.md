# 二维码扫描导出工具

一款用于扫描保存二维码并支持导出CSV格式的Android应用。

## 项目结构
请提供您现有的项目文件和目录结构，我才能帮您详细列出项目结构。

## 技术栈
- 开发语言：Kotlin 1.9.22
- 构建工具：Gradle 8.2.2
- Android 配置：
  - compileSdk: 34
  - minSdk: 26
  - targetSdk: 34
  - Java 版本: 17

### 主要依赖库
- AndroidX 核心组件：
  - core-ktx: 1.12.0
  - appcompat: 1.6.1
  - constraintlayout: 2.1.4
  
- 相机相关：
  - CameraX: 1.3.0
    - camera-core
    - camera-camera2
    - camera-lifecycle
    - camera-view
    
- 二维码处理：
  - ML Kit Barcode Scanning: 17.2.0
  - ZXing: 4.3.0
  
- 图片处理：
  - Glide: 4.12.0
  
- 网络请求：
  - OkHttp3: 4.12.0
  
- 其他工具：
  - Material Design: 1.11.0
  - SnakeYAML: 2.2

## 开发环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 17
- Gradle 8.2.2
- Android SDK Platform 34
- Android SDK Build-Tools 34

## 构建与运行
1. 克隆项目到本地
2. 在 Android Studio 中打开项目
3. 等待 Gradle 同步完成
4. 连接 Android 设备（Android 8.0/API 26 或更高版本）
5. 点击 "运行" 按钮或使用快捷键 `Shift + F10` 运行应用

## 功能特性
1. 二维码扫描
   - 实时相机预览
   - 自动对焦
   - 防重复扫描（1秒间隔）
   
2. 数据管理
   - 扫描结果本地存储
   - 列表展示历史记录
   - 滑动删除（带确认对话框）
   
3. 数据导出
   - 支持导出CSV格式
   - 文件分享功能

## 权限要求
- `android.permission.CAMERA`: 用于扫描二维码
- `android.permission.WRITE_EXTERNAL_STORAGE`: 用于保存导出的CSV文件（Android 10及以上版本使用作用域存储）

## 版本信息
- 当前版本：1.0
- 版本代码：1
- 包名：com.awen.qrscan

## 注意事项
- 需要 Android 8.0 (API 26) 或更高版本
- 首次使用需要授予相机权限
- 确保设备有足够的存储空间用于导出文件

## 项目说明
待补充项目的主要功能和特点说明。
