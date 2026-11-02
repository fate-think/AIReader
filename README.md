# AIReader

原生 Android 阅读助手。提供阅读主页、动态提示词管理、API 设置、本地文章预处理和 OpenAI 兼容 Chat Completions 请求。

## 技术与构建

Kotlin、Jetpack Compose、Material 3、MVVM/StateFlow。minSdk 26，compileSdk/targetSdk 35。Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose Compiler 2.1.20、Compose BOM 2025.04.01、JDK 17。

本机不安装 Android SDK，也不执行 Android 构建。推送到 `main` 或手动运行 [构建工作流](.github/workflows/build-apk.yml)，由 GitHub 托管 Runner 执行 `testDebugUnitTest` 与 `assembleDebug`，并上传 Debug APK。

## 数据

四份内置提示词位于 `app/src/main/assets/prompts/`。内置模式有稳定 ID，可编辑，并可在确认后从 assets 恢复默认；不能删除。提示词管理页的“我的自定义提示词”区域可持续新增、编辑、重命名和删除任意多套模式。删除前会确认；删除当前模式后自动选中第一套内置模式。编辑未保存内容时返回会提醒。

模式列表、创建顺序和当前选择统一保存在 DataStore 中。旧版 `modes` 数据首次读取时迁移；数据解析失败会显示错误并停止写入，避免覆盖已保存的提示词。API Key 使用 Android Keystore 的 AES-GCM 密钥加密后存入应用私有 DataStore；Android 自动备份已禁用。文章只保存在界面状态中。

## 文章预处理

在阅读页粘贴文章后，点击“文章预处理设置”可调整启用状态、每组合并段数（1–100）、标题识别和编号列表识别。设置通过 DataStore 保存。点击“预览处理结果”可查看完整处理结果、处理前后字符数并复制；关闭预览后输入框仍是原文。

预处理会跳过空白行，连续普通自然段按设置数量直接拼接，并为正文、标题和编号列表添加连续的段落编号。“预览处理结果”与“开始阅读”共用 `ArticlePreprocessor.process`。阅读页另有“预览最终发送内容”，仅显示可滚动、可复制的完整 User Message。完整消息由 `buildUserMessage` 拼接，发送为唯一一条 user 消息；不生成 System 或 Developer Message。

## API 使用

在“API 设置”填写 HTTPS Base URL、API Key 和 Model ID；可调整 Temperature、最大输出 token、是否发送 Temperature、`max_tokens` 或 `max_completion_tokens`、流式输出。留空新密钥并保存会保留已有密钥；删除密钥需要明确点击删除并保存。密钥丢失或设备迁移后无法解密时需重新输入。文章将发送至用户配置的第三方 API 服务。“测试连接”发送独立的“请只回复 OK。”短请求，可能产生少量费用。

阅读页“开始阅读”会固定当前提示词、原文和预处理设置的快照，发送至 `/chat/completions`。流式内容按顺序追加；点击“停止生成”会取消请求并保留已生成文本。错误不会清空已有内容；可复制结果或重新生成。自动重定向已禁用，且默认只允许 HTTPS。

## 测试

GitHub Actions 运行 `testDebugUnitTest`。提示词管理、预处理与设置测试继续保留；新增 MockWebServer 测试，覆盖 URL、单条 user 消息、参数、流式与普通响应、错误处理、取消及重定向。第四阶段代码尚需通过 GitHub Actions 实际运行验证。

## Wrapper 说明

仓库内的 `gradle-wrapper.jar` 与 Gradle 官方发布的 Wrapper JAR SHA-256 校验值一致（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）。实际 CI 构建结果以 GitHub Actions 为准。
