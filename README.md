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

在“API 设置”填写 Base URL、API Key 和 Model ID；可调整 Temperature、最大输出 token、是否发送 Temperature、`max_tokens` 或 `max_completion_tokens`、流式输出。Base URL 默认只接受 HTTPS；如需 HTTP，明确开启“允许 HTTP Base URL”并保存。HTTP 会以明文传输 API Key 和文章内容。粘贴 API Key 时会自动去除首尾空白和换行；密钥中间若含换行则提示重新粘贴。留空新密钥并保存会保留已有密钥；删除密钥需要明确点击删除并保存。密钥丢失或设备迁移后无法解密时需重新输入。文章将发送至用户配置的第三方 API 服务。“测试连接”发送独立的“请只回复 OK。”短请求，并使用设置页当前的流式输出、最大输出 token 和 Temperature 参数设置；可能产生调用费用。若参考 curl 排查连接问题，请确保这些参数也一致。

阅读页“开始阅读”会固定当前提示词、原文、预处理设置和 API 设置的快照，发送至 `/chat/completions`。流式内容按顺序追加；点击“停止生成”会取消请求并保留已生成文本。错误不会清空已有内容；可复制结果或重新生成。自动重定向已禁用。

阅读报告支持标题、粗体、斜体、嵌套列表、引用、代码块、表格及用户点击的网页链接。宽表格可横向滚动。生成过程中先以纯文本显示增量内容，结束后渲染 Markdown；用户向上滚动时暂停自动跟随，可点击“回到底部”恢复。结果可复制、通过系统分享面板分享，或通过系统文件选择器保存为 UTF-8 Markdown 文件。清空结果前会确认。深色模式跟随系统。文章和报告默认不会自动写入文件或上传到其他服务。

阅读结果的“更多操作”包含 HTML 预览、浏览器打开和保存 HTML。HTML 由 CommonMark 解析库及表格、删除线扩展生成完整 HTML5 文档；字号为 14–36px，默认 24px，使用 DataStore 保存。仅在用户操作时于后台转换，同一报告和字号的结果可复用。预览使用禁用 JavaScript、文件访问及网络加载的 WebView；导出通过系统文件选择器写入 UTF-8 HTML。浏览器打开时，应用将 HTML 写入缓存，再通过 FileProvider 的 `content://` URI 和系统 `ACTION_VIEW` 交给默认处理程序。部分浏览器可能不支持这种本地 HTML URI；可在 App 内预览，或保存文件后使用支持本地文件的浏览器打开。HTML 不包含远程资源，图片仅保留替代文字。

## 测试

GitHub Actions 运行 `testDebugUnitTest`。提示词管理、预处理与设置测试继续保留；MockWebServer 测试覆盖 URL、单条 user 消息、参数、流式与普通响应、错误处理、取消及重定向。新增 Markdown 转 HTML、安全过滤、长文本、字号持久化及导出字节一致性测试。界面交互、WebView、浏览器兼容性、系统分享与文件选择器需要通过 GitHub Actions 构建 APK 后在真机验证。

## Wrapper 说明

仓库内的 `gradle-wrapper.jar` 与 Gradle 官方发布的 Wrapper JAR SHA-256 校验值一致（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）。实际 CI 构建结果以 GitHub Actions 为准。
