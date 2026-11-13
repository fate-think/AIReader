# AIReader Android 开发规则

## 开发环境

- Ubuntu 22.04 x86_64
- 云服务器：2 vCPU、约 2 GB RAM
- Codex CLI 已安装并登录
- 使用 GitHub Actions 编译 APK
- 不在本地安装 Android SDK
- 不在本地执行 Gradle Android 编译
- 不使用 Android Studio

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- StateFlow
- Coroutines
- OkHttp
- DataStore
- Room（本地阅读历史）
- Android Keystore
- Gradle Kotlin DSL
- minSdk 26

## 四套内置提示词

源文件目录：
/root/codex/提示词/

文件：

1. 信息点提取V1.md
2. 信息点提取V2.md
3. 全文分析总结V1.md
4. 全文分析总结V2.md

要求：

- 完整读取四个 Markdown 文件。
- 不修改、不压缩、不截断提示词原文。
- 将文件复制到 Android assets 目录。
- 四套内置模式允许编辑及恢复默认。
- 内置模式不能删除。

## 自定义提示词

- 支持添加任意多套自定义阅读模式。
- 每套模式有独立 ID、名称、提示词。
- 支持新增、编辑、重命名、删除。
- 不能只提供一个固定的自定义提示词入口。
- 使用持久化存储保存用户修改。
- 阅读模式使用动态列表，不使用固定枚举限制数量。

### 第三阶段数据与界面约定

- 四套内置模式使用稳定 ID，提示词原文只从 assets 读取；用户修改保存到 DataStore，恢复默认前确认。
- 自定义模式使用 UUID 和创建顺序，全部模式与当前选择以一个可迁移的数据快照保存；每次修改在 `DataStore.edit` 中基于最新数据进行。
- 旧版 `modes` 数据必须迁移。若已保存提示词无法解析，显示错误并停止写入，不得静默覆盖。
- 删除自定义模式、恢复内置提示词、放弃未保存编辑都需要确认。删除当前模式后选择第一套内置模式。
- “预览最终发送内容”和开始阅读共用 `buildUserMessage`；只构建单条 User Message，不添加额外标题。

## API 请求

支持自定义：

- API Base URL
- API Key
- Model ID
- Temperature
- Max Output Tokens

使用 OpenAI 兼容的 /chat/completions 接口。

阅读提示词不作为 System Message。

发送流程：

1. 读取当前阅读模式提示词。
2. 对用户文章执行本地预处理。
3. 将完整提示词与预处理后的文章拼接。
4. 作为单条 User Message 发送。
5. 不添加 System 或 Developer Message。

支持 SSE 流式输出、停止生成、Markdown 展示及复制结果。

### 第四阶段网络约定

- 只发送一条 user 消息；请求开始时固定当前模式、文章、预处理参数和 API 配置的快照。
- API Key 使用 Android Keystore AES-GCM 加密；DataStore 只保存密文。留空新密钥不得清空旧密钥，删除必须由用户明确操作。
- 默认只允许 HTTPS；携带认证信息的 OkHttp 请求禁用自动重定向。不得记录密钥、完整文章、提示词或原始错误响应。
- SSE 增量按顺序追加；停止生成时取消协程和 OkHttp Call，保留已生成内容，并阻止旧请求更新新结果。
- 测试连接只发送独立短消息，不使用用户文章；告知用户可能产生调用费用。

## 文章预处理

默认启用。

默认参数：
paraOnce = 3

用户可以在设置中修改。

规则：

1. 按原文非空行识别自然段。
2. 删除空白行。
3. 每 paraOnce 个普通自然段合并成一个编号段落。
4. 同组合并的自然段直接拼接，不添加空格、换行或其他分隔符。
5. 不同编号段落之间使用一个换行符。
6. 新段落添加 {段落N}。
7. 不含指定标点符号的行添加 {段落N}{标题N}。
8. 中文冒号不计入标题检测用的标点。
9. 数字或字母编号列表独立编号。
10. 标题和编号列表不参与普通正文合并。
11. 保留原文输入框内容，只处理发送副本。
12. 预览内容与实际发送内容必须一致。

### 第二阶段实现约定

- 预处理逻辑集中在纯 Kotlin `ArticlePreprocessor.process(article, options)`；预览和开始阅读共用该函数。
- `PreprocessOptions` 包含启用状态、1–100 的 `paraOnce`、标题识别和编号列表识别，默认值分别为 `true`、`3`、`true`、`true`。
- 标题和编号列表优先独立编号；遇到它们时先输出未满组的普通正文。中文冒号不参与标题标点判断。
- 用户原文仅保留在内存中的输入状态。预处理选项使用 DataStore 保存；后续 API 输入由 `buildUserMessage` 将完整提示词与预处理正文拼为单条 User Message。
- 第四阶段已接入真实 API；不得显示模拟 AI 响应。预览与实际请求必须继续使用同一预处理和消息构建函数。

## 数据安全

- API Key 不写入代码或日志。
- API Key 使用 Android Keystore 保护。
- 普通配置使用 DataStore 保存。
- 当前输入框的文章草稿不自动单独持久化；成功生成报告时，按阅读历史开关将原文快照保存到本机 Room。
- 不提交密钥、认证文件和敏感数据到 Git。

## 阅读历史（第八阶段）

- 成功完成的报告使用 Room 本地保存；`requestId` 是历史主键，重复完成事件只能插入一次。失败、取消和未完成流不得保存。
- 自动保存和保存原文开关位于现有 DataStore，默认均开启；请求开始时固定设置、原文、阅读模式名称、模型和 API 配置名称快照。
- 历史列表只查询摘要，详情按 ID 读取完整内容；历史记录不保存 API Key、提示词正文或完整请求。
- 数据库升级必须添加显式 Migration，不使用无条件 destructive migration。现有全局 `allowBackup=false` 保持不变。
- 历史删除只操作 Room，不影响提示词、API 配置或 DataStore。Android 设备测试放在 `androidTest`，本地云服务器不运行 Android 构建。

## 设置导航（第九阶段）

- 底栏依次为阅读、提示词、历史、设置；历史直接打开第八阶段历史列表，设置首页依次显示大模型 API 配置、夜间模式、阅读历史与数据管理、关于。
- 网络稳定性继续属于每套 API 配置，设置页面改造不得改变 API 数据快照、Keystore、主题 DataStore 或 Room 表结构。
- API 编辑页有未保存修改时返回需确认；底栏历史详情返回独立历史列表，设置内历史详情返回列表后再返回数据管理页。
- 关于页面从实际应用包信息读取版本号，不硬编码版本。

## 图标与 APK 体积规范

1. 后续新增图标时优先复用已有资源。
2. 需要新的 Material 图标时，优先添加单独的 Android VectorDrawable XML。
3. 允许使用项目现有 Material Icons Core 图标。
4. 不得为了少量图标重新引入 `material-icons-extended`。
5. 不得用 PNG 或 JPG 替代本来可以使用矢量绘制的简单菜单图标。
6. 新图标应与现有图标风格一致。
7. 新图标必须兼容浅色和深色主题。
8. 如确实需要引入大型图标依赖，必须先向用户说明原因及 APK 体积影响。
9. 不得擅自恢复已经移除的大型图标库。

本规范适用于 AIReader 后续所有开发阶段。

## Markdown 工作区（第十阶段）

- 阅读页的“AI 阅读结果”始终显示；`ReaderState.result` 是编辑、预览、复制、分享和 HTML 操作共用的当前 Markdown 文本，默认空白且处于编辑模式。
- 手动粘贴或编辑只更新内存中的工作区，不调用 AI、不做文章预处理，也不自动保存到 Room；已保存的历史报告不随工作区编辑而改写。
- 工作区区分手动内容、AI 内容和已手动修改的 AI 内容。新请求覆盖手动内容前必须确认；取消确认不得启动请求。
- 生成期间显示增量文本并禁用手动编辑；完成后切到 Markdown 预览。停止或失败保留已收到的文本，历史仍只保存成功完成的 AI 报告。
- HTML 转换、浏览器打开和导出按用户操作读取当前 Markdown；编辑后不得复用旧 HTML 缓存。清空工作区不清空文章、设置或历史。

## 开发和验证

- 代码在云服务器上编辑。
- GitHub 托管 Runner 负责编译。
- 使用 GitHub Actions 执行测试和 assembleDebug。
- 上传 Debug APK 作为构建产物。
- 不使用 self-hosted Runner。
- 没有实际构建成功时不得声称编译通过。
- 优先保证功能稳定，不要过度设计。
