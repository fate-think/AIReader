# AIReader

原生 Android 阅读助手。当前提供阅读主页、动态提示词管理、API 设置和本地文章预处理。尚未接入 AI 请求；“开始阅读”只准备消息并显示未连接提示。

## 技术与构建

Kotlin、Jetpack Compose、Material 3、MVVM/StateFlow。minSdk 26，compileSdk/targetSdk 35。Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose Compiler 2.1.20、Compose BOM 2025.04.01、JDK 17。

本机不安装 Android SDK，也不执行 Android 构建。推送到 `main` 或手动运行 [构建工作流](.github/workflows/build-apk.yml)，由 GitHub 托管 Runner 执行 `testDebugUnitTest` 与 `assembleDebug`，并上传 Debug APK。

## 数据

四份内置提示词位于 `app/src/main/assets/prompts/`。内置模式有稳定 ID，可编辑，并可在确认后从 assets 恢复默认；不能删除。提示词管理页的“我的自定义提示词”区域可持续新增、编辑、重命名和删除任意多套模式。删除前会确认；删除当前模式后自动选中第一套内置模式。编辑未保存内容时返回会提醒。

模式列表、创建顺序和当前选择统一保存在 DataStore 中。旧版 `modes` 数据首次读取时迁移；数据解析失败会显示错误并停止写入，避免覆盖已保存的提示词。API Key 使用 Android Keystore 加密后保存，文章只保存在界面状态中。

## 文章预处理

在阅读页粘贴文章后，点击“文章预处理设置”可调整启用状态、每组合并段数（1–100）、标题识别和编号列表识别。设置通过 DataStore 保存。点击“预览处理结果”可查看完整处理结果、处理前后字符数并复制；关闭预览后输入框仍是原文。

预处理会跳过空白行，连续普通自然段按设置数量直接拼接，并为正文、标题和编号列表添加连续的段落编号。“预览处理结果”与“开始阅读”共用 `ArticlePreprocessor.process`。阅读页另有“预览最终发送内容”，可查看并复制当前模式名称、完整提示词、处理后的文章及完整 User Message。完整消息由 `buildUserMessage` 拼接，暂存在内存中供后续 API 阶段使用；不生成 System 或 Developer Message。

网络请求、流式输出和 Markdown 结果展示将在后续阶段实现。

## 测试

GitHub Actions 运行 `testDebugUnitTest`。提示词管理测试覆盖内置 assets、编辑与恢复、五套并发新增、UUID 与顺序、删除回退、旧数据迁移、异常数据保护、长 Markdown 持久化和最终消息拼接；原有文章预处理测试继续保留。

## Wrapper 说明

仓库内的 `gradle-wrapper.jar` 与 Gradle 官方发布的 Wrapper JAR SHA-256 校验值一致（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）。实际 CI 构建结果以 GitHub Actions 为准。
