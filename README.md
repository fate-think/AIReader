# AIReader

原生 Android 阅读助手。当前提供阅读主页、动态提示词管理、API 设置和本地文章预处理。尚未接入 AI 请求；“开始阅读”只准备消息并显示未连接提示。

## 技术与构建

Kotlin、Jetpack Compose、Material 3、MVVM/StateFlow。minSdk 26，compileSdk/targetSdk 35。Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose Compiler 2.1.20、Compose BOM 2025.04.01、JDK 17。

本机不安装 Android SDK，也不执行 Android 构建。推送到 `main` 或手动运行 [构建工作流](.github/workflows/build-apk.yml)，由 GitHub 托管 Runner 执行 `testDebugUnitTest` 与 `assembleDebug`，并上传 Debug APK。

## 数据

四份内置提示词位于 `app/src/main/assets/prompts/`。内置模式可编辑并恢复默认，不能删除；自定义模式可任意新增、编辑、重命名、删除。模式和普通设置使用 DataStore 保存，API Key 使用 Android Keystore 加密后保存。文章只保存在界面状态中。

## 文章预处理

在阅读页粘贴文章后，点击“文章预处理设置”可调整启用状态、每组合并段数（1–100）、标题识别和编号列表识别。设置通过 DataStore 保存。点击“预览处理结果”可查看完整处理结果、处理前后字符数并复制；关闭预览后输入框仍是原文。

预处理会跳过空白行，连续普通自然段按设置数量直接拼接，并为正文、标题和编号列表添加连续的段落编号。预览与“开始阅读”共用 `ArticlePreprocessor.process`。后者将当前模式的完整提示词与处理后的文章拼成单条 User Message，暂存在内存中供后续 API 阶段使用；不生成 System 或 Developer Message。

网络请求、流式输出和 Markdown 结果展示将在后续阶段实现。

## Wrapper 说明

仓库内的 `gradle-wrapper.jar` 与 Gradle 官方发布的 Wrapper JAR SHA-256 校验值一致（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）。实际 CI 构建结果以 GitHub Actions 为准。
