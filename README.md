# AIReader

原生 Android 阅读助手。当前第一阶段提供阅读主页、动态提示词管理和 API 设置界面；“开始阅读”暂不发送网络请求，结果区域明确标注为未生成。

## 技术与构建

Kotlin、Jetpack Compose、Material 3、MVVM/StateFlow。minSdk 26，compileSdk/targetSdk 35。Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose Compiler 2.1.20、Compose BOM 2025.04.01、JDK 17。

本机不安装 Android SDK，也不执行 Android 构建。推送到 `main` 或手动运行 [构建工作流](.github/workflows/build-apk.yml)，由 GitHub 托管 Runner 执行 `testDebugUnitTest` 与 `assembleDebug`，并上传 Debug APK。

## 数据

四份内置提示词位于 `app/src/main/assets/prompts/`。内置模式可编辑并恢复默认，不能删除；自定义模式可任意新增、编辑、重命名、删除。模式和普通设置使用 DataStore 保存，API Key 使用 Android Keystore 加密后保存。文章只保存在界面状态中。

网络请求、流式输出、文章预处理和 Markdown 结果展示将在后续阶段实现。

## Wrapper 说明

仓库内的 `gradle-wrapper.jar` 与 Gradle 官方发布的 Wrapper JAR SHA-256 校验值一致（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）。实际 CI 构建结果以 GitHub Actions 为准。
