AIReader：新增 Markdown 转 HTML、预览和浏览器打开功能

请先阅读 AGENTS.md、README.md，并检查当前项目的 Markdown 展示和阅读结果管理代码。

本任务是增量开发。不得重新创建项目，不得破坏已有 AI 请求、SSE 流式输出、文章预处理、提示词管理、API Key 存储和 GitHub Actions 构建流程。

一、产品目标

将 AI 生成的 Markdown 阅读报告转换成完整 HTML 页面。

新增三个操作：

1. HTML 预览。
2. 导出 HTML 文件。
3. 使用系统默认浏览器打开 HTML。

保留现有的 Markdown 结果，不得覆盖或删除。

二、Markdown 转 HTML

优先使用成熟的 CommonMark 解析库，例如 commonmark-java。

根据当前项目依赖情况选择兼容版本，并在 Gradle 中添加必要扩展。

支持：

- H1–H6 标题
- 加粗、斜体
- 有序列表
- 无序列表
- 多级嵌套列表
- 引用
- 代码块
- 行内代码
- Markdown 链接
- 分割线
- GFM 表格
- 删除线

不要通过逐行正则表达式重新实现 Markdown 解析器。

创建独立的 MarkdownHtmlConverter 类。

建议接口：

fun convert(
    markdown: String,
    fontSizePx: Int = 24
): String

转换结果应当是完整 HTML5 文档，而不是仅有 HTML body 片段。

三、HTML 模板

生成：

- DOCTYPE html
- html lang="zh-CN"
- meta charset="UTF-8"
- viewport
- title
- style
- body

默认正文大小 24px。

请使用适合手机阅读的 CSS：

body {
    font-size: 24px;
    line-height: 1.7;
    max-width: 900px;
    margin: 0 auto;
    padding: 16px;
    overflow-wrap: anywhere;
    font-family: system-ui, sans-serif;
}

pre {
    overflow-x: auto;
}

table {
    border-collapse: collapse;
    display: block;
    overflow-x: auto;
    max-width: 100%;
}

img {
    max-width: 100%;
}

可以根据需要补充标题、列表、引用、代码块和表格的样式。

不要添加远程 CSS、远程 JavaScript 或不必要的网络依赖。

四、HTML 安全

AI 返回的 Markdown 应视为不可信内容。

转换时：

1. 禁止未经处理的原始 HTML 被直接执行。
2. 转义普通文本中的 HTML 特殊字符。
3. 不允许脚本注入。
4. 禁止危险 URL Scheme，例如 javascript:。
5. 对链接与图片资源采取安全策略。
6. 不自动加载远程图片、脚本或其他资源。
7. 禁止自动执行模型生成的 HTML 或 JavaScript。

对于无法安全转换的 Markdown 片段，优先保留可阅读文本。

五、App 内 HTML 预览

在阅读结果区域增加：

“预览 HTML”

使用 Android WebView 展示生成的 HTML。

要求：

- 支持正常滚动。
- 支持中文。
- 支持不同字体大小。
- JavaScript 默认禁用。
- 禁止不必要的文件访问。
- 不允许访问应用内部敏感文件。
- 不加载远程资源。
- 外部链接只有用户点击时才允许交给外部浏览器。
- 禁止自动跳转或不可信页面导航。
- 返回按钮可以关闭 HTML 预览，回到阅读主页。
- 不修改已经生成的 Markdown 内容。

使用 AndroidView 与 Jetpack Compose 集成 WebView。

预览时使用已经完成转换的完整 HTML。

六、HTML 字体大小

增加 HTML 阅读字体大小设置。

默认：

24px

允许范围：

14–36px

建议使用滑块或者加减按钮控制。

设置通过 DataStore 保存。

修改字体大小后，可以重新生成 HTML 模板并刷新预览，但不得重新调用 AI API。

七、导出 HTML 文件

增加按钮：

“保存 HTML”

使用 Android Storage Access Framework：

ACTION_CREATE_DOCUMENT

MIME Type：

text/html

建议文件名：

AIReader-阅读报告.html

让用户自行选择保存目录。

使用 UTF-8 编码写入。

不要请求不必要的全局存储权限。

如果用户取消保存，不显示错误。

保存失败时给出清楚提示。

文件应该是独立完整的 HTML 文档，可以离线阅读。

八、默认浏览器打开 HTML

增加按钮：

“浏览器打开”

实现方案：

1. 将完整 HTML 写入应用缓存目录。
2. 使用 FileProvider 生成 content:// URI。
3. 使用 ACTION_VIEW。
4. MIME Type 设置为 text/html。
5. 添加 FLAG_GRANT_READ_URI_PERMISSION。
6. 优先使用 Android 系统默认处理程序。
7. 如果没有默认程序或浏览器无法打开，提供清楚的提示。
8. 提供 App 内 HTML 预览作为可靠的备用方式。

禁止使用 file:// URI 直接跨应用分享私有文件。

不要为了打开 HTML 而启动本地 HTTP 服务器。

特别注意：

不同浏览器对 content:// HTML 的支持不同，因此不能假设所有 Android 浏览器都能打开该文件。

如果遇到兼容问题，明确提示用户，可以选择导出 HTML 后使用支持本地文件的浏览器打开。

不要把“调用 ACTION_VIEW 成功”误认为“浏览器已成功渲染页面”。

九、UI 设计

在 AI 阅读结果区域增加操作菜单：

- 复制 Markdown
- HTML 预览
- 浏览器打开
- 保存 HTML

不要让阅读结果区域出现过多拥挤按钮。

可以将 HTML 相关功能统一放在“更多操作”菜单里。

当阅读结果为空时，禁用 HTML 操作。

生成过程中可以禁用 HTML 导出，待生成完成后操作，以避免保存不完整报告。

十、性能

仅在用户预览、导出或打开 HTML 时执行完整转换。

不要在 SSE 每收到一个 Token 时重新转换整个 Markdown。

对于较长的阅读报告，在后台线程执行 Markdown 解析和 HTML 生成，避免阻塞 UI。

对于相同的 Markdown 内容与字体参数，可以适当缓存转换结果。

十一、测试

增加单元测试，至少覆盖：

1. H1–H6 标题。
2. 粗体与斜体。
3. 内联代码。
4. 代码块。
5. 无序列表。
6. 有序列表。
7. 嵌套列表。
8. 引用。
9. 分割线。
10. Markdown 表格。
11. 中文 UTF-8 文本。
12. HTML 特殊字符转义。
13. 不可信 HTML 输入。
14. 危险链接过滤。
15. 空 Markdown。
16. 长 Markdown 文档。
17. 字体大小设置。
18. 完整 HTML 文档结构。
19. 保存文件内容与转换结果一致。
20. 现有 AI 请求和预处理测试继续通过。

十二、严格保护现有功能

不得修改：

- 四套内置提示词内容。
- 自定义提示词的管理规则。
- ArticlePreprocessor 合并算法。
- paraOnce 的组内直接拼接规则。
- UserMessageBuilder。
- 单条 User Message 的 API 请求规则。
- API Key 安全存储机制。
- 现有 GitHub Actions 构建及签名方案。

十三、开发环境

当前服务器：

- Ubuntu 22.04 x86_64
- 2 核 CPU
- 约 2 GB RAM
- Codex CLI
- GitHub Actions 编译 APK

不要在本地执行 Android Gradle 构建。

不要自动提交或推送 Git。

十四、完成后报告

请告诉我：

1. 新增和修改了哪些文件。
2. 使用什么 Markdown 解析库。
3. HTML 的实际生成方式。
4. WebView 的安全配置。
5. 浏览器打开的实现方式。
6. 是否存在特定浏览器兼容性限制。
7. 新增的单元测试。
8. 哪些功能尚未经过真机验证。

请直接修改代码，实现这项功能。
