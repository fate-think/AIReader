AIReader 第六阶段：新增七档思考深度及参数发送开关

一、项目背景

你正在维护一款已经开发完成核心功能的 Android 应用：AIReader。

请先完整阅读当前项目中的：

- AGENTS.md
- README.md
- 现有 API 设置相关代码
- 现有 OpenAI 兼容请求构造代码
- 现有 ReaderViewModel 和网络 Repository
- 现有单元测试

本次任务为现有项目的增量开发。

不要重新创建 Android 工程，不要大规模重构网络架构。

当前 AIReader 已有功能

1. Kotlin + Jetpack Compose + Material 3。
2. 四套内置 Markdown 提示词。
3. 可新增任意多套自定义提示词。
4. 提示词编辑、保存、恢复默认。
5. ArticlePreprocessor 文章预处理。
6. paraOnce 自定义段落合并数量。
7. UserMessageBuilder 消息拼接。
8. 自定义 OpenAI 兼容 API Base URL、API Key、Model ID。
9. Temperature、Max Output Tokens 等请求设置。
10. Chat Completions 请求。
11. SSE 流式输出和停止生成。
12. DataStore 设置持久化。
13. Android Keystore 保护 API Key。
14. GitHub Actions 编译 APK。

本次新增功能

当前 App 尚未实现任何思考深度设置。

现在需要增加：

- 七档思考深度选择。
- 独立的“发送思考深度参数”开关。
- 设置持久化。
- 在 Chat Completions 请求 JSON 顶层有条件地添加 "reasoning_effort"。

本次只处理 Chat Completions，不实现、不考虑、不引入 OpenAI Responses API。

---

二、七种思考深度

必须支持以下完整列表：

UI 显示名称| API 参数值
Max（最大）| max
XHigh（超高）| xhigh
High（高）| high
Medium（中等）| medium
Low（低）| low
Minimal（极低）| minimal
None（无）| none

这些参数值必须原样发送。

严格要求：

1. 不将 max 转换为 xhigh。
2. 不将 minimal 转换为 low。
3. 不将 none 理解成省略参数。
4. 不将任何档位转换为 Token 数量。
5. 不根据模型名称过滤档位。
6. 不根据服务商自动更改用户选择。
7. 不新增 Auto 档位。

推荐 Kotlin 数据结构：

enum class ReasoningEffort(
    val apiValue: String,
    val displayName: String
) {
    MAX("max", "Max（最大）"),
    XHIGH("xhigh", "XHigh（超高）"),
    HIGH("high", "High（高）"),
    MEDIUM("medium", "Medium（中等）"),
    LOW("low", "Low（低）"),
    MINIMAL("minimal", "Minimal（极低）"),
    NONE("none", "None（无）")
}

可以根据现有项目架构调整实现，但七个 API 参数字符串必须保持完全一致。

---

三、独立的发送开关

新增设置：

发送思考深度参数

类型：Switch。

默认值：

"false"

新增当前选中的深度：

"ReasoningEffort.MAX"

建议的数据结构：

data class ReasoningSettings(
    val sendReasoningEffort: Boolean = false,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MAX
)

开关关闭

无论当前选中哪个档位，最终 HTTP 请求 JSON 中都不包含 "reasoning_effort"。

例如：

{
  "model": "glm-5.3",
  "messages": [
    {
      "role": "user",
      "content": "请分析这篇文章"
    }
  ],
  "stream": true
}

注意：以上只展示相关字段，实际请求继续保留 AIReader 原有的 Temperature、Max Output Tokens 等参数。

开关开启

如果选择 Max：

{
  "model": "glm-5.3",
  "messages": [
    {
      "role": "user",
      "content": "请分析这篇文章"
    }
  ],
  "stream": true,
  "reasoning_effort": "max"
}

如果选择 High：

{
  "model": "glm-5.3",
  "messages": [
    {
      "role": "user",
      "content": "请分析这篇文章"
    }
  ],
  "stream": true,
  "reasoning_effort": "high"
}

"reasoning_effort" 必须是 JSON 顶层字段，与 "model"、"messages"、"stream" 同级。

关闭开关的含义

关闭发送开关，只表示不发送 "reasoning_effort" 参数。

不能理解成关闭模型思考能力。

模型不收到该参数时，其实际推理行为由服务商及模型默认设置决定。

保留用户选择

例如：

1. 用户开启发送开关。
2. 选择 High。
3. 关闭发送开关。
4. 退出 App。
5. 重新打开 App。
6. 再次开启发送开关。

预期仍然选择 High。

关闭开关不能把档位重置为 Max。

---

四、设置界面

直接在 AIReader 已有的 API 设置页面增加“思考深度”区域。

不新增独立页面。

控件一：发送思考深度参数

使用 Material 3 Switch。

标题：

发送思考深度参数

说明：

开启后，向 API 请求添加 reasoning_effort。关闭后不发送该参数，由模型服务端决定默认行为。

默认关闭。

控件二：思考深度

使用现有 UI 风格的下拉菜单或选择弹窗。

完整显示：

- Max（最大）
- XHigh（超高）
- High（高）
- Medium（中等）
- Low（低）
- Minimal（极低）
- None（无）

默认选择 Max。

当发送开关关闭时：

- 思考深度控件可以置灰。
- 仍然显示用户之前选择的档位。
- 不清空当前值。

当发送开关开启时：

- 用户可以立即修改档位。
- 修改后按照现有 API 设置保存机制持久化。
- 下一次 API 请求立即使用新参数。

不要增加独立的“启用思考”开关。

不要增加“Thinking Token 预算”设置。

不要为了这个功能新增模型能力检测页面。

---

五、DataStore 持久化

AIReader 已使用 DataStore 保存普通 API 配置。

请直接沿用现有持久化机制。

建议增加：

sendReasoningEffort: Boolean = false

reasoningEffort: ReasoningEffort = ReasoningEffort.MAX

要求：

1. 新增字段不能影响旧版配置读取。
2. 老用户升级后默认关闭发送。
3. 老用户已有 Base URL、Model ID、Temperature 等设置不丢失。
4. API Key 的加密存储机制不变。
5. 修改设置后可以持久保存。
6. App 重启后恢复上次设置。
7. 关闭发送开关时保留当前档位。
8. 遇到未知或损坏的档位存储值时，安全回退到 MAX，避免 App 崩溃。
9. 不要因为更新 ReasoningSettings 导致其他 API 设置被覆盖。

设置粒度

当前 AIReader 只有一套 API 配置，因此本次使用全局思考深度设置。

切换当前 Model ID 时，继续使用当前已保存的思考深度配置。

不要为本次功能引入 Provider + Model ID 配置数据库，也不要大规模改造 API 配置结构。

未来如果增加多套 API 配置，再考虑每个模型独立保存思考深度。

---

六、Chat Completions 请求构造

找到当前 AIReader 的 OpenAICompatibleClient 或同等网络请求类。

必须在现有 Chat Completions JSON 构建过程中注入参数。

建议集中封装：

fun injectReasoningEffort(
    requestBody: MutableMap<String, Any?>,
    settings: ReasoningSettings
) {
    if (settings.sendReasoningEffort) {
        requestBody["reasoning_effort"] =
            settings.reasoningEffort.apiValue
    } else {
        requestBody.remove("reasoning_effort")
    }
}

这是逻辑示例，不要求照搬数据结构。

如果现有项目使用 JSONObject、JsonObject、kotlinx.serialization、Moshi 或其他方式，请使用最小改动实现。

发送开启

最终请求包含：

""reasoning_effort": "用户选择的原始字符串""

发送关闭

最终请求完全不包含 "reasoning_effort"。

禁止发送：

{"reasoning_effort": null}

{"reasoning_effort": ""}

{"reasoning_effort": "auto"}

保留现有参数

不能修改或丢失：

- model
- messages
- stream
- temperature
- max_tokens
- max_completion_tokens（如果当前已支持）
- 现有其他请求参数

如果现有项目支持自定义 JSON 参数，必须检查是否存在 "reasoning_effort" 冲突。

建议规则：

- 没有自定义参数功能时，不需要额外开发。
- 如果已有自定义参数功能，保留其原有合并机制。
- 发送开关只控制 AIReader 自动注入的 reasoning_effort。
- 用户显式自定义参数的优先级应当清楚，不产生重复 JSON 字段。
- 不在用户不知情时删除已有自定义参数。

---

七、保持原有 AIReader 消息结构

这是兼容性重点。

AIReader 当前的消息处理流程为：

用户原始文章

→ ArticlePreprocessor

→ 带 "{段落N}" 编号的文章

→ UserMessageBuilder

→ 当前阅读模式的完整 Markdown 提示词 + 两个换行符 + 预处理文章

→ 单条 User Message

→ Chat Completions API

这套流程完全不变。

不要新增 System Message。

不要新增 Developer Message。

不要更改文章预处理规则。

特别是：

- paraOnce 默认 3。
- 同组合并自然段直接拼接。
- 不增加空格。
- 不增加换行。
- 不修改 "{段落N}" 和 "{标题N}"。
- 四套内置提示词原文不变。
- 任意多套自定义提示词不受影响。

新增思考深度只影响 HTTP 请求 JSON 顶层的一个可选字段。

不得修改 UserMessageBuilder 的业务逻辑。

---

八、流式与非流式请求

AIReader 已有 SSE 流式输出功能。

本次必须同时适配：

1. "stream=true"
2. "stream=false"

两种请求都使用同一套思考深度注入规则。

不要在流式请求和普通请求中分别实现两套不同的判断逻辑。

建议将注入逻辑放在统一的请求构造阶段。

不修改：

- SSE 事件解析器。
- 增量文本拼接。
- 停止生成逻辑。
- 重新生成逻辑。
- Markdown 输出显示。
- HTML 转换和导出功能（如果当前已实现）。

思考深度功能只负责请求参数，不负责展示模型内部思考内容。

---

九、GLM-5.3 及其他模型兼容性

重点考虑用户可能配置 GLM-5.3，但不要把功能绑定到 GLM-5.3。

默认使用通用的 Chat Completions 顶层参数：

{
  "reasoning_effort": "max"
}

不额外自动发送：

{
  "thinking": {
    "type": "enabled"
  }
}

也不自动发送：

- enable_thinking
- thinking_budget
- reasoning
- budget_tokens

某些服务商或模型可能支持不同的思考参数格式，本次不实现所有厂商的特殊适配。

不建立模型名称白名单。

用户使用未知模型 ID 时，只要开启发送开关，仍然可以提交所选 reasoning_effort。

不自动限制档位。

不自动把 Max 改成 High。

不自动把 None 转换成关闭发送。

服务端是否支持某个档位，以实际 API 响应为准。

如果目标 GLM-5.3 服务商支持这七档，就按用户设置发送；不要假设所有同名模型部署都具有相同能力。

---

十、错误处理

优先复用现有 API 错误处理机制。

如果服务端明确返回：

"Unknown parameter: reasoning_effort"

可以提示：

“当前 API 可能不支持思考深度参数，请在设置中关闭‘发送思考深度参数’。”

如果服务端明确返回：

"Unsupported reasoning effort: max"

可以提示：

“当前模型可能不支持所选思考深度，请尝试其他档位。”

注意：

1. 不要将所有 HTTP 400 归类为思考深度错误。
2. 不自动降低思考深度。
3. 不自动删除参数后重试。
4. 不在失败后修改用户配置。
5. 不额外触发第二次计费 API 请求。
6. 保留原有网络错误分类和用户提示。
7. 不显示包含密钥的原始错误信息。

---

十一、API 测试连接

AIReader 已有“测试连接”功能。

请检查测试连接使用的 Chat Completions 请求构造流程。

建议让测试连接与普通 AI 阅读请求遵循同一套 reasoning_effort 发送规则，以便测试当前配置是否真正被服务端接受。

但测试连接必须继续使用独立短消息，不发送用户文章。

不得因为新增思考深度而破坏原有测试连接功能。

如果开启较高思考深度导致测试成本或耗时增加，可在 UI 中提示测试连接可能产生少量费用。

不要擅自修改用户选择的深度进行测试。

---

十二、单元测试

请为功能新增独立测试，同时保留现有全部测试。

A. 请求参数测试

1. 默认关闭：JSON 不包含 reasoning_effort。
2. 开启 Max：发送 "max"。
3. 开启 XHigh：发送 "xhigh"。
4. 开启 High：发送 "high"。
5. 开启 Medium：发送 "medium"。
6. 开启 Low：发送 "low"。
7. 开启 Minimal：发送 "minimal"。
8. 开启 None：发送 "none"。
9. 关闭时不得发送 null。
10. 关闭时不得发送空字符串。
11. 不得自动发送 auto。

B. 设置持久化测试

12. 默认发送开关为 false。
13. 默认思考深度为 MAX。
14. 选择 High 后关闭开关，再开启仍为 High。
15. 重启或重新读取 DataStore 后配置不变。
16. 读取旧版配置时使用默认值。
17. 非法档位值安全回退。
18. 保存思考设置不会覆盖现有 API 配置。

C. 网络请求兼容测试

19. 流式请求正确注入参数。
20. 非流式请求正确注入参数。
21. 未知模型 ID 也可以注入参数。
22. 原有请求字段保持不变。
23. messages 仍然只有一条 user 消息。
24. UserMessageBuilder 输出没有变化。
25. 预处理后的文章没有变化。
26. 测试连接遵循相同参数规则。
27. 如果已有自定义 JSON 参数，验证优先级和冲突处理。

D. 错误处理测试

28. 服务端拒绝 reasoning_effort 时显示合理错误。
29. 不自动降级。
30. 不自动重试。
31. 不修改用户保存的档位。

E. 回归测试

保留并确保以下测试继续通过：

- ArticlePreprocessorTest
- PreprocessSettingsStoreTest
- PromptRepositoryTest
- 原有 API 请求测试
- 原有 SSE 流式处理测试

尽可能测试最终序列化后的 HTTP JSON，而不只是测试中间数据对象。

---

十三、代码结构

优先复用现有架构。

可以增加：

- ReasoningEffort.kt
- ReasoningSettings.kt
- ReasoningParameterInjector.kt

如果项目已有合适的数据类或请求构造函数，也可以直接集成，不强制创建新文件。

建议职责：

- ReasoningEffort：定义七个 API 字符串。
- ReasoningSettings：保存当前设置。
- Settings Repository：负责 DataStore 持久化。
- Settings UI：负责 Switch 和下拉选择器。
- Request Builder：在请求 JSON 顶层添加或省略参数。

不要把思考深度注入逻辑分散在多个 UI 和 ViewModel 中。

不要引入新的大型网络框架。

---

十四、不需要实现的功能

本次明确不实现：

1. OpenAI Responses API。
2. 模型思考过程解析与展示。
3. 独立的启用 Thinking 开关。
4. 自动模型能力检测。
5. 不同模型独立保存思考深度。
6. Provider + Model ID 配置数据库。
7. 自动降低思考深度。
8. 自动更换服务商。
9. 请求失败后自动重新发送。
10. 自定义思考 Token 预算。
11. 厂商特有的 Thinking 参数映射。
12. 新增聊天记录或多轮对话功能。
13. 大规模网络层重构。

本次只解决一个明确问题：

让用户决定是否在 Chat Completions 请求中发送 reasoning_effort，以及发送哪一个档位。

---

十五、开发环境与 GitHub Actions

当前开发环境：

- Ubuntu 22.04 x86_64
- 2 核 CPU
- 约 2 GB RAM
- Codex CLI
- GitHub Actions 负责 Android APK 构建

要求：

1. 不安装 Android Studio。
2. 不在云服务器上进行 Android Gradle 编译。
3. 不修改已有 APK 签名流程。
4. 不修改 GitHub Actions 工作流，除非确有必要。
5. 不随意升级 AGP、Gradle、Kotlin 或 Compose。
6. 不自动执行 Git Commit。
7. 不自动 Git Push。
8. 不读取或记录用户真实 API Key。
9. 不修改四套内置 Markdown 提示词。
10. 不破坏现有数据持久化。

完成代码后由我推送 GitHub，通过 Actions 执行构建和测试。

---

十六、开发顺序

请按照以下顺序实施：

第一步：检查现有代码

找到 API 设置界面、DataStore、请求构造代码及流式和非流式请求入口。

第二步：设计最小修改

确认思考深度放在哪个现有配置数据类中，以及如何让所有 Chat Completions 请求共用注入逻辑。

第三步：实现设置

增加发送开关、七档选择器和持久化。

第四步：实现参数注入

确保开启才发送，关闭则彻底省略字段。

第五步：补充测试

测试七档、发送开关、持久化、实际请求 JSON 及旧功能兼容。

第六步：静态检查

检查 Kotlin 语法、调用关系、序列化行为及已有代码兼容性。

不要在本机执行 Android 构建。

第七步：输出开发报告

说明：

- 修改的文件。
- 新增的 UI 控件。
- 数据保存方式。
- reasoning_effort 注入位置。
- 默认关闭时如何保证字段不存在。
- 流式和非流式是否共用逻辑。
- 新增测试数量及覆盖范围。
- 尚未经过 GitHub Actions 验证的部分。
- 真机需要重点测试的操作。

请直接开始开发，不要只输出设计建议。

---

十七、最终验收标准

功能完成后，我应该能够：

1. 打开 AIReader 的 API 设置页面。
2. 找到“发送思考深度参数”开关。
3. 看到开关默认关闭。
4. 看到 Max、XHigh、High、Medium、Low、Minimal、None 七档。
5. 默认选中 Max。
6. 开启开关并选择某档位。
7. 保存配置后正常进行 AI 阅读。
8. 请求 JSON 顶层包含正确的 reasoning_effort。
9. 关闭开关后请求中完全没有该字段。
10. 重新启动 App 后保留之前的设置。
11. 切换模型 ID 后仍能正常调用 API。
12. 不支持某档位时显示合理错误，不自动重试。
13. SSE 流式生成、停止生成及 Markdown 阅读结果继续正常工作。
14. 四套内置提示词和自定义提示词不受影响。
15. ArticlePreprocessor 的段落合并行为不发生变化。
16. 原有 API Key、Base URL 和其他配置不丢失。

最重要的验收原则：默认不发送 reasoning_effort，功能低侵入，旧行为保持不变。


附加要求：为未来 Responses API 预留扩展能力

目前 AIReader 只支持 OpenAI Chat Completions API。

本阶段仍然只实现 Chat Completions，不实现 Responses API。

但请在新增 reasoning_effort 功能之前，检查当前网络层是否具有合理的可扩展性。

目标是未来能够增加 /v1/responses 支持，而无需重写阅读业务逻辑。

具体要求：

检查 UI、ReaderViewModel、ReadingRepository 和网络请求客户端的职责划分。

保证文章预处理和 UserMessageBuilder 不依赖 Chat Completions 的 JSON 请求结构。

如果当前已有合适的 Repository 抽象，优先复用，不要重复建立接口。

如果当前代码高度耦合，可以进行最小范围的解耦，但不能为了尚未实现的 Responses API 大规模重构。

将通用 AI 请求信息（用户消息、模型名称、流式开关、生成参数等）与 Chat Completions 专属 JSON 构造逻辑分离。

如果已有通用的生成事件模型，继续复用；否则只在确有必要时建立简单的 TextDelta、Completed、Error 等事件表达。

reasoning_effort 参数注入逻辑本阶段仍然只针对 Chat Completions。

不实现 Responses API 的 reasoning.effort、input、max_output_tokens 或流式事件解析。

不新增 API 类型切换页面。

不创建尚未使用的空 ResponsesClient 或大量占位代码。

保持现有 Chat Completions 请求、SSE、取消生成及错误处理完全兼容。

增加必要的单元测试，确认网络层调整没有改变实际发送的 Chat Completions JSON。

本次工作的核心是低成本预留，而不是提前开发另一套 API。

如果现有网络层已经足够清晰，请不要为了形式上的可扩展性而重构。

完成后简要说明：以后新增 Responses API 时，预计需要新增哪些文件、修改哪些现有接口，以及哪些阅读业务代码可以保持不变。
