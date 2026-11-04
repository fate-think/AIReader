AIReader 第七阶段：多套 API 配置管理与快速切换

请先完整阅读 AGENTS.md、README.md，并检查当前 AIReader 项目代码。

本次任务是增量开发，不要重新创建项目。

一、开发目标

为 AIReader 增加多套 OpenAI 兼容 API 配置管理功能。

用户可以新增、保存、编辑、删除并快速切换任意多套 API 配置。

目前 App 已经有一套能够正常工作的 API 设置，包含 Base URL、API Key、Model ID、Temperature、最大输出 Token、流式输出、思考深度等参数。

本次必须在完整保留这些功能的前提下扩展。

二、配置数据模型

每套 API 配置至少包含：

- id：稳定 UUID
- name：用户自定义名称
- baseUrl
- modelId
- temperature
- sendTemperature
- maxOutputTokens
- maxTokensParameterType（如果现有项目支持）
- streamEnabled
- sendReasoningEffort
- reasoningEffort
- 现有网络超时及自动重试设置（如果已实现）

API Key 独立加密保存，不得将明文放入普通 DataStore。

各配置的 API Key 必须彼此独立。

不要在日志、错误信息或 Git 仓库中输出密钥。

三、旧数据迁移

本次最重要的要求是兼容已有用户数据。

请先检查当前 API 设置的 DataStore 和 Keystore 实现。

迁移规则：

1. 如果旧版已经存在 API 配置，自动迁移为第一套配置。
2. 保留原有 Base URL、Model ID、Temperature、Token 设置、流式开关、思考深度设置。
3. 旧 API Key 必须保留可用。
4. 不得重新生成或覆盖已有 API Key。
5. 迁移后默认选择原来的 API 配置。
6. 迁移过程应当可重复执行，不生成重复配置。
7. 迁移失败不得清空原始数据。
8. 必须为迁移添加单元测试。
9. 升级过程中如果 Keystore 密钥失效，提示用户重新输入 API Key，不得导致 App 崩溃。
10. 不要在未经验证时删除旧版持久化字段。

四、API 配置管理界面

在现有 API 设置中增加配置列表。

支持：

- 显示所有已保存配置。
- 显示配置名称及 Model ID。
- 显示当前使用的配置。
- 新增配置。
- 编辑配置。
- 删除配置。
- 复制现有配置为新配置。
- 快速切换当前配置。

复制配置时不得无意复制 API Key；如果要复制，必须由用户明确选择。

删除配置时要求确认。

如果删除当前配置，自动切换到另一套有效配置。

至少保留一套配置记录，允许其中字段尚未填写。

配置名称不能为空。

五、快速切换

阅读主页显示当前使用的模型及配置名称。

允许用户通过简单下拉菜单或弹窗切换 API 配置。

切换时：

1. 不修改文章内容。
2. 不修改当前阅读模式。
3. 不清空已有阅读报告。
4. 不重新执行文章预处理。
5. 不自动触发 AI 请求。
6. 下一次请求使用新选择的配置。

正在生成的请求必须继续使用原来的配置快照，不得被中途切换影响。

六、参数隔离

每套配置独立保存：

- Temperature
- 最大输出 Token
- 流式输出
- 思考深度发送开关
- 七档 reasoning_effort
- 现有其他 API 请求参数

例如：

配置 A：

sendReasoningEffort = true
reasoningEffort = MAX

配置 B：

sendReasoningEffort = false
reasoningEffort = HIGH

从 A 切换到 B 时，不得错误地沿用 A 的设置。

关闭 reasoning_effort 发送开关时，请求 JSON 必须省略该字段。

仍然只支持现有 Chat Completions API，不开发 Responses API。

七、网络与安全

复用现有 OkHttp、SSE、错误处理与安全存储代码。

不增加独立网络框架。

每次请求启动时固定完整配置快照，包括解密后的 API Key。

不要将明文 API Key 长期保存在 UI StateFlow 中。

不要将 API Key 放入普通日志。

不同配置的 Key 不能串用。

测试连接应使用当前正在编辑或明确选中的配置，不能意外使用另一套配置。

切换配置后，不能导致正在执行的请求使用错误的 API 地址或密钥。

八、DataStore

使用现有 DataStore 保存配置列表与当前选中的配置 ID。

确保写入原子性，避免并发新增、删除或编辑覆盖其他配置。

读取损坏的数据时，不得静默清空所有配置。

不要为每套配置创建固定的编号字段。

不增加不必要的数据库依赖。

九、测试要求

至少增加：

1. 旧配置成功迁移。
2. 旧 API Key 保留。
3. 迁移重复执行不生成重复配置。
4. 新增多套配置。
5. 配置 ID 唯一。
6. 配置独立保存。
7. 删除非当前配置。
8. 删除当前配置后的回退。
9. 复制配置不意外泄露 API Key。
10. 选择配置持久化。
11. 切换配置不影响当前阅读模式。
12. 切换配置不改变正在执行的请求。
13. 不同配置的思考深度互不影响。
14. 关闭 reasoning_effort 时请求字段被省略。
15. 不同配置的 API Key 不串用。
16. 测试连接使用正确配置。
17. 新增配置不影响原有预处理设置。
18. 原有单元测试全部保留。

十、禁止改动

不得修改：

- 四套内置 Markdown 提示词。
- 自定义提示词的存储规则。
- ArticlePreprocessor。
- paraOnce 组内直接拼接行为。
- UserMessageBuilder。
- 单条 User Message 规则。
- SSE 流式解析。
- 现有 Markdown 和 HTML 输出功能。
- 夜间模式。
- GitHub Actions 构建和签名流程。

除非发现直接相关的问题，不得大规模重构。

十一、开发环境

Ubuntu 22.04，2 核 CPU、约 2 GB RAM。

Codex CLI 负责编辑源代码。

GitHub Actions 负责编译 APK。

不要安装 Android Studio。

不要在本地运行 Android Gradle 构建。

不要自动 Git Commit 或 Git Push。

十二、开发完成后报告

请说明：

1. 修改和新增的文件。
2. 数据模型与加密存储方式。
3. 旧版 API 设置如何迁移。
4. 多套配置如何切换。
5. 切换时如何保护正在执行的请求。
6. 新增哪些测试。
7. 尚未经过 GitHub Actions 和手机验证的部分。

现在请先检查已有实现，制定最小范围修改方案，再直接开始开发。
