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

## 数据安全

- API Key 不写入代码或日志。
- API Key 使用 Android Keystore 保护。
- 普通配置使用 DataStore 保存。
- 用户文章默认不持久化到磁盘。
- 不提交密钥、认证文件和敏感数据到 Git。

## 开发和验证

- 代码在云服务器上编辑。
- GitHub 托管 Runner 负责编译。
- 使用 GitHub Actions 执行测试和 assembleDebug。
- 上传 Debug APK 作为构建产物。
- 不使用 self-hosted Runner。
- 没有实际构建成功时不得声称编译通过。
- 优先保证功能稳定，不要过度设计。
