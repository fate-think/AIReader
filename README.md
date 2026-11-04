# AIReader

原生 Android 阅读助手。提供阅读主页、动态提示词管理、API 设置、本地文章预处理和 OpenAI 兼容 Chat Completions 请求。

## 技术与构建

Kotlin、Jetpack Compose、Material 3、MVVM/StateFlow。minSdk 26，compileSdk/targetSdk 35。Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose Compiler 2.1.20、Compose BOM 2025.04.01、JDK 17。

本机不安装 Android SDK，也不执行 Android 构建。推送到 `main` 或手动运行 [构建工作流](.github/workflows/build-apk.yml)，由 GitHub 托管 Runner 执行 `testDebugUnitTest` 与 `assembleDebug`，并上传 Debug APK。

## 数据

四份内置提示词位于 `app/src/main/assets/prompts/`。内置模式有稳定 ID，可编辑，并可在确认后从 assets 恢复默认；不能删除。提示词管理页的“我的自定义提示词”区域可持续新增、编辑、重命名和删除任意多套模式。删除前会确认；删除当前模式后自动选中第一套内置模式。编辑未保存内容时返回会提醒。

模式列表、创建顺序和当前选择统一保存在 DataStore 中。旧版 `modes` 数据首次读取时迁移；数据解析失败会显示错误并停止写入，避免覆盖已保存的提示词。API 配置列表及当前选择也保存在 DataStore 的单个快照中；每套配置的 API Key 分别使用 Android Keystore 的 AES-GCM 密钥加密后保存，不进入普通界面状态。Android 自动备份已禁用。文章只保存在界面状态中。

## 文章预处理

在阅读页粘贴文章后，点击“文章预处理设置”可调整启用状态、每组合并段数（1–100）、标题识别和编号列表识别。设置通过 DataStore 保存。点击“预览处理结果”可查看完整处理结果、处理前后字符数并复制；关闭预览后输入框仍是原文。

预处理会跳过空白行，连续普通自然段按设置数量直接拼接，并为正文、标题和编号列表添加连续的段落编号。“预览处理结果”与“开始阅读”共用 `ArticlePreprocessor.process`。阅读页另有“预览最终发送内容”，仅显示可滚动、可复制的完整 User Message。完整消息由 `buildUserMessage` 拼接，发送为唯一一条 user 消息；不生成 System 或 Developer Message。

## API 使用

在“API 设置”填写 Base URL、API Key 和 Model ID；可调整 Temperature、最大输出 token、是否发送 Temperature、`max_tokens` 或 `max_completion_tokens`、流式输出。Base URL 默认只接受 HTTPS；如需 HTTP，明确开启“允许 HTTP Base URL”并保存。HTTP 会以明文传输 API Key 和文章内容。粘贴 API Key 时会自动去除首尾空白和换行；密钥中间若含换行则提示重新粘贴。留空新密钥并保存会保留已有密钥；删除密钥需要明确点击删除并保存。密钥丢失或设备迁移后无法解密时需重新输入。文章将发送至用户配置的第三方 API 服务。“测试连接”发送独立的“请只回复 OK。”短请求，并使用设置页当前的流式输出、最大输出 token 和 Temperature 参数设置；可能产生调用费用。若参考 curl 排查连接问题，请确保这些参数也一致。

“API 设置”顶部可新增任意多套配置，点“使用 / 编辑”切换到该配置并编辑名称及参数；可复制参数为新配置，复制时不会复制 API Key。删除配置需要确认，至少保留一套；删除正在使用的配置后自动选择剩余列表中的第一套。阅读页也有配置下拉菜单，显示配置名称和模型，下一次请求使用新选择的配置；正在生成的请求继续使用启动时固定的地址、参数及密钥。旧版单套 API 设置在首次启动时自动迁移为“默认 API 配置”，旧密文直接保留，旧字段暂不删除。若 Keystore 密钥失效，界面提示重新输入该配置的 API Key。

“思考深度”区域提供独立的“发送思考深度参数”开关和 Max、XHigh、High、Medium、Low、Minimal、None 七档。默认关闭发送、默认选择 Max。开启后，Chat Completions 请求 JSON 顶层添加所选的 `reasoning_effort` 原始值；关闭后完全省略该字段，但保留上次选中的档位。每套 API 配置独立保存开关和档位。测试连接使用当前编辑配置的参数与密钥，可能产生少量费用；服务端是否支持所选档位以实际响应为准。

阅读页“开始阅读”会固定当前提示词、原文、预处理设置和 API 设置的快照，发送至 `/chat/completions`。流式内容按顺序追加；点击“停止生成”会取消请求并保留已生成文本。错误不会清空已有内容；可复制结果或重新生成。自动重定向已禁用。

API 设置页的“网络稳定性”区域可调整自动重试、最大重试次数（0–5）、连接超时（10–60 秒）和读取空闲超时（30–600 秒）；默认分别为开启、2 次、20 秒和 180 秒。设置通过现有 DataStore 保存，旧配置获得默认值。临时 429、500、502、503 及确认未建立连接的失败可在尚未收到生成正文时按指数退避重试；配额不足、认证或参数错误、TLS 错误、读取超时和已开始输出的流不会自动重试。504 可能已由上游处理，默认提示手动重新生成；只有同时开启自动重试和“允许重试 504”才会有限重试，可能产生重复调用费用。服务端的 `Retry-After` 优先于指数退避；要求等待超过 60 秒时停止自动重试。等待重试时可取消。测试连接始终只发送一次请求。

阅读报告支持标题、粗体、斜体、嵌套列表、引用、代码块、表格及用户点击的网页链接。宽表格可横向滚动。生成过程中先以纯文本显示增量内容，结束后渲染 Markdown；用户向上滚动时暂停自动跟随，可点击“回到底部”恢复。结果可复制、通过系统分享面板分享，或通过系统文件选择器保存为 UTF-8 Markdown 文件。清空结果前会确认。文章和报告默认不会自动写入文件或上传到其他服务。

API 设置页的“外观设置”提供跟随系统、浅色模式和深色模式，默认跟随系统。选择后立即应用到全部 Compose 页面及系统栏，并以独立的 `theme_mode` 键保存在现有 DataStore 中。Markdown 的代码背景、表格边框和链接颜色跟随 Material 3 配色；主题切换不会修改文章、提示词或阅读结果，也不会重新发起 AI 请求。

阅读结果的“更多操作”包含 HTML 预览、浏览器打开和保存 HTML。HTML 由 CommonMark 解析库及表格、删除线扩展生成完整 HTML5 文档；字号为 14–36px，默认 24px，使用 DataStore 保存。仅在用户操作时于后台转换，同一报告和字号的结果可复用。预览使用禁用 JavaScript、文件访问及网络加载的 WebView；导出通过系统文件选择器写入 UTF-8 HTML。浏览器打开时，应用将 HTML 写入缓存，再通过 FileProvider 的 `content://` URI 和系统 `ACTION_VIEW` 交给默认处理程序。部分浏览器可能不支持这种本地 HTML URI；可在 App 内预览，或保存文件后使用支持本地文件的浏览器打开。HTML 不包含远程资源，图片仅保留替代文字。

应用内 HTML 预览严格使用当前 App 的浅色或深色配色，切换主题时仅重新生成本地 HTML。保存和浏览器打开的独立 HTML 使用 `prefers-color-scheme`，由阅读器或浏览器环境决定配色；字号设置继续生效。

## 测试

GitHub Actions 运行 `testDebugUnitTest`。提示词管理、预处理与设置测试继续保留；MockWebServer 测试覆盖 URL、单条 user 消息、参数、流式与普通响应、错误处理、取消及重定向。多配置测试覆盖旧设置与密文迁移、重复迁移、配置及密钥隔离、选择持久化、复制与删除、并发新增、测试连接和请求快照。主题测试覆盖模式选择、持久化、旧配置、未知值与 HTML 配色；思考深度、网络重试和 HTML 导出测试继续保留。界面切换、系统栏、SSE 期间切换主题、WebView、浏览器兼容性、系统分享与文件选择器需要通过 GitHub Actions 构建 APK 后在真机验证。

## Wrapper 说明

仓库内的 `gradle-wrapper.jar` 与 Gradle 官方发布的 Wrapper JAR SHA-256 校验值一致（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）。实际 CI 构建结果以 GitHub Actions 为准。
