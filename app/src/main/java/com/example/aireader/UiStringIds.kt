package com.example.aireader

internal fun uiStringId(source: String): Int? = when (source) {
    "+ 新增提示词" -> R.string.ui_000
    "504 网关超时不代表上游未执行；开启后可能重复请求和计费。总自动重试关闭时此项不生效。" -> R.string.ui_001
    "AI 阅读助手" -> R.string.ui_002
    "AI 阅读结果" -> R.string.ui_003
    "AIReader 是一款基于 Kotlin 和 Jetpack Compose 开发的 Android AI 阅读助手，支持自定义阅读提示词、文章预处理、OpenAI 兼容接口及本地阅读历史。" -> R.string.ui_004
    "AIReader-阅读报告.html" -> R.string.ui_005
    "API Base URL 不能包含账号信息。" -> R.string.ui_006
    "API Base URL 必须使用 HTTPS；如需 HTTP，请在大模型 API 配置的全局参数中开启允许 HTTP BaseURL。" -> R.string.ui_007
    "API Base URL 无效。" -> R.string.ui_008
    "API Key 中间包含换行，请检查后重新粘贴。" -> R.string.ui_009
    "API Key（可选）" -> R.string.ui_010
    "API Key：已配置。留空可保留现有密钥。" -> R.string.ui_011
    "API Key：未配置。" -> R.string.ui_012
    "API 请求发送到您配置的服务地址。API Key 使用 Android Keystore 加密保存。阅读历史保存在本机，是否保存由阅读历史设置控制。文件导出与分享均由您主动发起。" -> R.string.ui_013
    "API 请求失败。" -> R.string.ui_014
    "API 返回的 JSON 格式无效。" -> R.string.ui_015
    "API 返回空内容。" -> R.string.ui_016
    "API 返回空响应。" -> R.string.ui_017
    "API 配置 ID 重复。" -> R.string.ui_018
    "API 配置不存在。" -> R.string.ui_019
    "API 配置列表为空。" -> R.string.ui_020
    "API 配置字段不完整。" -> R.string.ui_021
    "API 配置尚未迁移。" -> R.string.ui_022
    "API 配置已保存" -> R.string.ui_023
    "API 配置已删除" -> R.string.ui_024
    "API 配置已复制" -> R.string.ui_025
    "API 配置数据无法读取，原数据未被覆盖。" -> R.string.ui_026
    "API 配置迁移或读取失败，原数据未被覆盖。" -> R.string.ui_027
    "HTML 字体大小保存失败。" -> R.string.ui_028
    "HTML 已保存。" -> R.string.ui_029
    "HTML 文件已保存。" -> R.string.ui_030
    "HTML 生成失败。" -> R.string.ui_031
    "HTML 预览" -> R.string.ui_032
    "HTML 预览刷新失败。" -> R.string.ui_033
    "HTML 预览生成失败。" -> R.string.ui_034
    "HTTP 会以明文传输 API Key 和文章内容，请仅在信任该连接时使用。" -> R.string.ui_035
    "High（高）" -> R.string.ui_036
    "Low（低）" -> R.string.ui_037
    "Markdown 已保存。" -> R.string.ui_038
    "Markdown 文件已保存。" -> R.string.ui_039
    "Max Output Tokens 必须大于 0。" -> R.string.ui_040
    "Max Output Tokens 必须是整数。" -> R.string.ui_041
    "Max（最大）" -> R.string.ui_042
    "Medium（中等）" -> R.string.ui_043
    "Minimal（极低）" -> R.string.ui_044
    "None（无）" -> R.string.ui_045
    "TLS 连接或证书验证失败，请检查 API 地址和证书。" -> R.string.ui_046
    "Temperature 应在 0 到 2 之间。" -> R.string.ui_047
    "Temperature 必须是有效数字。" -> R.string.ui_048
    "XHigh（超高）" -> R.string.ui_049
    "‹ 返回" -> R.string.ui_050
    "← 返回配置列表" -> R.string.ui_051
    "← 返回阅读历史" -> R.string.ui_052
    "不是设置首页菜单" -> R.string.ui_053
    "主题设置保存失败，请重试。" -> R.string.ui_054
    "仅针对符合条件的临时错误自动重试，可能产生额外 API 调用费用。" -> R.string.ui_055
    "使用" -> R.string.ui_056
    "使用并编辑" -> R.string.ui_058
    "例如设为 3，则每 3 个自然段合并为 1 段。" -> R.string.ui_059
    "保存" -> R.string.ui_060
    "保存 HTML" -> R.string.ui_061
    "保存 HTML 失败。" -> R.string.ui_062
    "保存 HTML 失败，请重新选择位置。" -> R.string.ui_063
    "保存 MD" -> R.string.ui_064
    "保存 Markdown" -> R.string.ui_065
    "保存 Markdown 失败。" -> R.string.ui_066
    "保存中…" -> R.string.ui_067
    "保存后将删除已配置的 API Key。" -> R.string.ui_068
    "保存失败，请重新选择位置。" -> R.string.ui_069
    "保存文章原文" -> R.string.ui_070
    "保存未完成，请重新选择文件。" -> R.string.ui_071
    "保存配置" -> R.string.ui_072
    "修改历史标题" -> R.string.ui_073
    "修改标题失败，请重试。" -> R.string.ui_074
    "停止生成" -> R.string.ui_075
    "允许 HTTP Base URL" -> R.string.ui_076
    "允许重试 504" -> R.string.ui_077
    "全部已保存的报告和文章原文将从本机删除，无法恢复。提示词、API 配置及密钥不会改变。" -> R.string.ui_078
    "关于" -> R.string.ui_079
    "关闭" -> R.string.ui_080
    "内置提示词" -> R.string.ui_081
    "内置阅读模式" -> R.string.ui_082
    "分享MD" -> R.string.ui_083
    "分享结果" -> R.string.ui_084
    "分享阅读报告" -> R.string.ui_085
    "分享阅读结果" -> R.string.ui_086
    "切换 API 配置" -> R.string.ui_087
    "删除" -> R.string.ui_088
    "删除 API 配置？" -> R.string.ui_089
    "删除历史失败，请重试。" -> R.string.ui_090
    "删除后无法恢复。不会影响提示词、API 配置和其他阅读记录。" -> R.string.ui_091
    "删除此提示词" -> R.string.ui_092
    "删除现有密钥" -> R.string.ui_093
    "删除自定义提示词？" -> R.string.ui_094
    "删除这条阅读历史？" -> R.string.ui_095
    "历史" -> R.string.ui_096
    "历史仅保存在本机，应用已禁用 Android 自动备份。卸载应用会删除本机历史；无法保证恢复。" -> R.string.ui_097
    "历史报告" -> R.string.ui_098
    "原始文章" -> R.string.ui_099
    "原文已复制" -> R.string.ui_100
    "原有 API Key 无法解密，请重新输入。" -> R.string.ui_101
    "发送 Temperature 参数" -> R.string.ui_102
    "发送思考深度参数" -> R.string.ui_103
    "取消" -> R.string.ui_104
    "取消删除" -> R.string.ui_105
    "取消重试" -> R.string.ui_106
    "可粘贴其他 AI 软件生成的 Markdown，也可编辑 AIReader 生成的阅读报告。" -> R.string.ui_107
    "启用预处理" -> R.string.ui_108
    "回到底部" -> R.string.ui_109
    "在这里粘贴文章" -> R.string.ui_110
    "基本连接" -> R.string.ui_111
    "复制" -> R.string.ui_112
    "复制 Markdown" -> R.string.ui_113
    "复制MD" -> R.string.ui_114
    "复制全部内容" -> R.string.ui_115
    "复制原文" -> R.string.ui_116
    "复制完整内容" -> R.string.ui_117
    "复制预处理结果" -> R.string.ui_118
    "夜间模式" -> R.string.ui_119
    "大模型 API 配置" -> R.string.ui_120
    "将从 APK 内置文件恢复原始提示词；未保存的编辑内容会丢失。" -> R.string.ui_121
    "将删除报告及保存的文章原文，无法恢复。" -> R.string.ui_122
    "将删除数据库中的所有阅读报告及保存的文章原文，无法恢复。" -> R.string.ui_123
    "已保存的 API 配置" -> R.string.ui_124
    "已停止生成" -> R.string.ui_125
    "已切换 API 配置" -> R.string.ui_126
    "已手动修改" -> R.string.ui_127
    "开启后，向 API 请求添加 reasoning_effort。关闭后不发送该参数，由模型服务端决定默认行为。" -> R.string.ui_128
    "开始阅读" -> R.string.ui_129
    "开源协议" -> R.string.ui_130
    "开源地址" -> R.string.ui_131
    "开源项目" -> R.string.ui_132
    "当前" -> R.string.ui_133
    "当前 API 可能不支持思考深度参数，请在设置中关闭‘发送思考深度参数’。" -> R.string.ui_134
    "当前 API 配置不存在。" -> R.string.ui_135
    "当前 Markdown 已被手动修改。重新生成将替换当前内容，是否继续？" -> R.string.ui_136
    "当前 Markdown 是手动输入的内容。新生成结果将替换当前 Markdown，是否继续？" -> R.string.ui_137
    "当前 · " -> R.string.ui_138
    "当前密钥无权获取模型列表。" -> R.string.ui_139
    "当前按系统语言显示；其他系统语言使用简体中文。" -> R.string.ui_140
    "当前服务可能不支持模型列表接口，请手动填写 Model ID。" -> R.string.ui_141
    "当前模型可能不支持所选思考深度，请尝试其他档位。" -> R.string.ui_142
    "当前编辑内容尚未保存。" -> R.string.ui_143
    "思考深度" -> R.string.ui_144
    "思考过程" -> R.string.ui_145
    "恢复默认" -> R.string.ui_147
    "恢复默认提示词失败。" -> R.string.ui_148
    "恢复默认提示词？" -> R.string.ui_149
    "我的自定义提示词" -> R.string.ui_150
    "手动 Markdown" -> R.string.ui_151
    "批量删除失败，请重试。" -> R.string.ui_152
    "找不到这条阅读历史。" -> R.string.ui_153
    "报告已生成，但保存到阅读历史失败。" -> R.string.ui_154
    "接口不存在，请检查 API 地址、路径及模型。" -> R.string.ui_155
    "提示词" -> R.string.ui_156
    "提示词保存失败" -> R.string.ui_157
    "提示词保存失败，原内容已保留。" -> R.string.ui_158
    "提示词删除失败。" -> R.string.ui_159
    "提示词管理" -> R.string.ui_160
    "搜索 Model ID" -> R.string.ui_161
    "搜索标题或阅读模式" -> R.string.ui_162
    "放弃未保存修改？" -> R.string.ui_163
    "放弃未保存的 API 修改？" -> R.string.ui_164
    "数据与隐私" -> R.string.ui_165
    "文章内容" -> R.string.ui_166
    "文章预处理" -> R.string.ui_167
    "文章预处理设置" -> R.string.ui_168
    "新 API Key（留空保留原值）" -> R.string.ui_169
    "新增 API 配置" -> R.string.ui_170
    "无法保存阅读模式选择。" -> R.string.ui_171
    "无法准备浏览器文件，请使用 HTML 预览或保存 HTML。" -> R.string.ui_172
    "无法切换" -> R.string.ui_173
    "无法切换 API 配置。" -> R.string.ui_174
    "无法删除 API 配置。" -> R.string.ui_175
    "无法复制 API 配置。" -> R.string.ui_176
    "无法打开分享面板。" -> R.string.ui_177
    "无法打开浏览器，请检查设备上的浏览器应用。" -> R.string.ui_178
    "无法打开目标文件" -> R.string.ui_179
    "无法打开阅读历史数据库。" -> R.string.ui_180
    "无法打开阅读历史，请重试。" -> R.string.ui_181
    "无法统计阅读历史。" -> R.string.ui_182
    "无法解析 API 主机地址，请检查网络和 API 地址。" -> R.string.ui_183
    "无法解析模型列表，请手动填写 Model ID。" -> R.string.ui_184
    "无法读取已保存数据，请检查应用数据。不会覆盖现有提示词。" -> R.string.ui_185
    "无法读取版本信息" -> R.string.ui_186
    "无法读取阅读历史，请重试。" -> R.string.ui_187
    "无法连接 API 服务，请检查网络和地址。" -> R.string.ui_188
    "显示密钥" -> R.string.ui_189
    "暂无 Markdown 内容。" -> R.string.ui_190
    "更多操作" -> R.string.ui_191
    "替换当前 Markdown？" -> R.string.ui_192
    "最大重试次数须为 0–5。" -> R.string.ui_193
    "最大重试次数（0–5）" -> R.string.ui_194
    "最终发送内容" -> R.string.ui_195
    "最终发送内容已复制" -> R.string.ui_196
    "服务器要求等待超过 60 秒，请稍后手动重试。" -> R.string.ui_197
    "服务拒绝访问，请检查密钥权限。" -> R.string.ui_198
    "服务端报告请求失败，请检查配置。" -> R.string.ui_199
    "服务端请求超时，请稍后重试。" -> R.string.ui_200
    "服务返回重定向；为保护 API Key，已阻止自动跳转。" -> R.string.ui_201
    "未命名阅读报告" -> R.string.ui_202
    "未知 API 配置版本。" -> R.string.ui_203
    "未载入" -> R.string.ui_204
    "未配置模型" -> R.string.ui_205
    "本地阅读历史" -> R.string.ui_206
    "查看原文" -> R.string.ui_207
    "查看最终发送内容" -> R.string.ui_208
    "查看阅读历史" -> R.string.ui_209
    "标题" -> R.string.ui_210
    "标题已更新。" -> R.string.ui_211
    "模型、思考深度、超时与重试" -> R.string.ui_212
    "模型列表响应为空。" -> R.string.ui_213
    "模型配置" -> R.string.ui_214
    "模式名称" -> R.string.ui_215
    "正在思考…" -> R.string.ui_216
    "正在生成…" -> R.string.ui_217
    "正在获取模型…" -> R.string.ui_218
    "正在连接…" -> R.string.ui_219
    "正在重试…" -> R.string.ui_220
    "此模式将被删除，未保存的编辑内容也会丢失。" -> R.string.ui_221
    "每组自然段数（1–100）" -> R.string.ui_222
    "没有可选模型。" -> R.string.ui_223
    "没有找到匹配的阅读历史。" -> R.string.ui_224
    "流式响应格式无效。" -> R.string.ui_225
    "流式输出" -> R.string.ui_226
    "浅色模式" -> R.string.ui_227
    "测试中…" -> R.string.ui_228
    "测试连接" -> R.string.ui_229
    "测试连接未收到有效内容。" -> R.string.ui_230
    "浏览器可能不支持直接打开此文件。可使用 HTML 预览，或保存 HTML 后用支持本地文件的浏览器打开。" -> R.string.ui_231
    "浏览器打开" -> R.string.ui_232
    "深色模式" -> R.string.ui_233
    "清空 Markdown" -> R.string.ui_234
    "清空 Markdown？" -> R.string.ui_235
    "清空全部" -> R.string.ui_236
    "清空全部历史" -> R.string.ui_237
    "清空全部阅读历史" -> R.string.ui_238
    "清空历史失败，请重试。" -> R.string.ui_239
    "清空文章" -> R.string.ui_240
    "版本信息、应用说明" -> R.string.ui_241
    "生成 HTML 失败。" -> R.string.ui_242
    "生成中断" -> R.string.ui_243
    "生成失败" -> R.string.ui_244
    "生成完成" -> R.string.ui_245
    "知道了" -> R.string.ui_246
    "确定清空全部阅读历史？" -> R.string.ui_247
    "确认" -> R.string.ui_248
    "等待开始" -> R.string.ui_249
    "等待模型响应…" -> R.string.ui_250
    "等待模型输出…" -> R.string.ui_251
    "等待重试…" -> R.string.ui_252
    "简体中文、繁體中文、English" -> R.string.ui_253
    "编辑" -> R.string.ui_254
    "编辑 API 配置" -> R.string.ui_255
    "编辑 Markdown" -> R.string.ui_256
    "编辑内容尚未保存。" -> R.string.ui_257
    "编辑阅读模式" -> R.string.ui_258
    "网关超时（HTTP 504）。上游请求可能仍在执行；手动重新生成或重试可能产生重复费用。" -> R.string.ui_259
    "网络稳定性" -> R.string.ui_260
    "网络稳定性设置超出允许范围。" -> R.string.ui_261
    "网络连接中断，请检查网络后重试。" -> R.string.ui_262
    "自动保存、原文存储、历史清理" -> R.string.ui_263
    "自动保存阅读历史" -> R.string.ui_264
    "自动识别标题" -> R.string.ui_265
    "自动识别编号列表" -> R.string.ui_266
    "自动重试" -> R.string.ui_267
    "自动重试等待时间已达上限，请稍后手动重试。" -> R.string.ui_268
    "至少保留一套 API 配置。" -> R.string.ui_269
    "获取在线模型列表" -> R.string.ui_270
    "获取模型列表认证失败，请检查 API Key。" -> R.string.ui_271
    "认证失败，请检查 API Key。" -> R.string.ui_272
    "记录已不存在。" -> R.string.ui_273
    "设置" -> R.string.ui_274
    "该历史记录未保存最终发送内容。" -> R.string.ui_275
    "语言" -> R.string.ui_276
    "语言设置保存失败，请重试。" -> R.string.ui_277
    "请先为当前 API 配置填写 API Key。" -> R.string.ui_278
    "请先填写 API Base URL。" -> R.string.ui_279
    "请先填写 Model ID。" -> R.string.ui_280
    "请先输入 API Key，再测试连接。" -> R.string.ui_281
    "请先输入文章。" -> R.string.ui_282
    "请先配置 API Key。" -> R.string.ui_283
    "请只回复 OK。" -> R.string.ui_284
    "请检查配置名称及参数范围。" -> R.string.ui_285
    "请求参数不被服务接受，请检查模型及参数设置。" -> R.string.ui_286
    "请求失败，请检查 API 配置后重试。" -> R.string.ui_287
    "请求超时，请稍后重试或检查服务状态。" -> R.string.ui_288
    "请求过于频繁，可能触及速率限制或额度限制。" -> R.string.ui_289
    "请选择有效的阅读模式。" -> R.string.ui_290
    "读取超时须为 30–600 秒。" -> R.string.ui_291
    "读取超时（30–600 秒）" -> R.string.ui_292
    "跟随系统" -> R.string.ui_293
    "跟随系统、浅色、深色" -> R.string.ui_294
    "转换中…" -> R.string.ui_295
    "返回" -> R.string.ui_296
    "还没有自定义阅读模式" -> R.string.ui_297
    "还没有阅读历史。成功生成报告后会自动保存在本机。" -> R.string.ui_298
    "这会清除当前 Markdown，不影响文章和提示词。" -> R.string.ui_299
    "这条历史记录已不存在。" -> R.string.ui_300
    "连接中断，生成未正常结束。" -> R.string.ui_301
    "连接成功：密钥、模型和响应格式可用。" -> R.string.ui_302
    "连接超时须为 10–60 秒。" -> R.string.ui_303
    "连接超时（10–60 秒）" -> R.string.ui_304
    "选择应用主题" -> R.string.ui_305
    "选择模型" -> R.string.ui_306
    "选择界面语言" -> R.string.ui_307
    "选择阅读模式" -> R.string.ui_308
    "配置名称" -> R.string.ui_309
    "配置名称不能为空。" -> R.string.ui_310
    "重命名" -> R.string.ui_311
    "重新生成" -> R.string.ui_312
    "重试次数已用尽" -> R.string.ui_313
    "阅读" -> R.string.ui_314
    "阅读历史" -> R.string.ui_315
    "阅读历史与数据管理" -> R.string.ui_316
    "阅读历史设置保存失败，请重试。" -> R.string.ui_317
    "阅读文章会发送至您配置的第三方 API 服务。测试连接使用下方当前参数，可能产生调用费用。" -> R.string.ui_318
    "阅读配置" -> R.string.ui_319
    "隐藏密钥" -> R.string.ui_320
    "预览 Markdown" -> R.string.ui_321
    "预览处理结果" -> R.string.ui_322
    "预览最终发送内容" -> R.string.ui_323
    "高级模型参数" -> R.string.ui_324
    "默认 API 配置" -> R.string.ui_326
    "API 配置列表" -> R.string.ui_327
    "全局参数" -> R.string.ui_328
    "如无特殊需求，以下参数默认即可。" -> R.string.ui_329
    "允许 HTTP BaseURL" -> R.string.ui_330
    "发送 max_tokens 参数" -> R.string.ui_331
    "保存全局参数" -> R.string.ui_332
    "当前全局参数禁止 HTTP BaseURL，请先在全局参数中开启允许 HTTP。" -> R.string.ui_333
    "全局 API 参数尚未载入。" -> R.string.ui_334
    "全局 API 参数保存失败，请检查参数或存储数据。" -> R.string.ui_335
    "全局 API 参数读取失败，原数据未被覆盖。" -> R.string.ui_336
    else -> null
}
