AIReader 小范围优化：Debug 独立安装及版本名称区分

一、任务目标

AIReader 已经有正常运行的 Debug 和 Release GitHub Actions 构建流程。

现在需要让 Debug APK 和 Release APK 可以在同一部 Android 手机上并存安装，同时在版本名称上区分两者。

本次明确要求在 Debug buildType 中同时增加：

applicationIdSuffix = ".debug"
versionNameSuffix = "-debug"

本次仅修改必要的 Android 构建配置，不涉及应用业务功能调整。

二、开发前检查

请先阅读项目根目录的 "AGENTS.md"，并检查：

1. "app/build.gradle.kts"
2. "app/src/main/AndroidManifest.xml"
3. 当前 Debug 和 Release buildTypes
4. 当前 "applicationId"
5. 当前 "namespace"
6. FileProvider 的 authorities 配置
7. 项目中与 applicationId 相关的 Manifest 占位符和资源配置

请以实际源码为准，不要猜测现有包名或重复创建构建配置。

三、核心修改要求

在 "app/build.gradle.kts" 现有的 Debug buildType 中加入：

applicationIdSuffix = ".debug"
versionNameSuffix = "-debug"

例如，项目采用 Kotlin DSL 并且已有 Debug buildType 时：

buildTypes {
    getByName("debug") {
        applicationIdSuffix = ".debug"
        versionNameSuffix = "-debug"
    }
}

以上仅为示例，实际修改须保留当前 Debug buildType 已有的其他配置。

修改后的预期效果

假设项目原本配置为：

applicationId = com.example.aireader
versionName = 1.0.0

那么：

Release：

applicationId = com.example.aireader
versionName = 1.0.0

Debug：

applicationId = com.example.aireader.debug
versionName = 1.0.0-debug

示例中的包名和版本号不代表当前项目的真实值，必须以源码为准。

不得改变原有 "versionCode"。

四、桌面名称及图标保持不变

用户已经明确决定：

Debug 和 Release 的桌面名称都保持 AIReader，不做区分。

因此：

- 不修改 "strings.xml"。
- 不修改 "app_name"。
- 不新增 Debug 专用应用名称。
- 不修改 "AndroidManifest.xml" 中的应用名称，除非存在实现并存安装所必需的兼容性问题。
- 不修改桌面图标。
- 不修改 Adaptive Icon。
- 不增加 Debug 图标角标。
- 不修改应用启动画面。

"versionNameSuffix = "-debug"" 只修改 Android 系统识别的应用版本名称，不应改变桌面显示名称。

五、FileProvider 与组件兼容性检查

AIReader 已经具有 Markdown 转 HTML、HTML 导出及浏览器打开功能。

请重点检查：

1. FileProvider authorities 是否使用 "${applicationId}"。
2. 是否存在硬编码 Release applicationId 的 Provider。
3. 是否存在其他固定 authorities、权限名称或 URI 标识。
4. Debug 与 Release 同时安装时是否可能产生组件命名冲突。

如果已经正确采用动态 applicationId，则不需要修改。

如果存在必须处理的 Manifest 冲突，允许进行最小必要的 Manifest 配置修改。

如果确实需要改动 Kotlin 源码才能使 Debug 正常使用 FileProvider 或 URI，请先说明原因及涉及文件，不要擅自修改核心业务逻辑。

不得重新开发 HTML 导出和浏览器打开功能。

六、数据隔离

Debug 和 Release 使用不同 applicationId 后，Android 会将它们视为两个独立应用。

两者应分别保存：

- API 配置。
- API Key。
- Android Keystore 密钥。
- DataStore 设置。
- 内置提示词的用户修改。
- 自定义提示词。
- Room 阅读历史。
- 夜间模式设置。

不得新增数据库表、修改 Room 结构或实现跨版本数据同步。

不得为了 Debug 和 Release 并存而改变 API Key 的安全存储方式。

两个应用对公共存储目录的文件访问不一定隔离，本次不需要重构现有公共文件导出行为。

七、严格保护 Release 构建

Release APK 已经成功构建和签名。

以下配置必须全部保持不变：

- Release applicationId。
- Release versionName。
- Release versionCode。
- Release signingConfig。
- 原有签名证书。
- GitHub "release" Environment。
- Environment Secrets。
- APK v2 + v3 签名方案。
- 签名证书 SHA-256 验证。
- R8 代码压缩。
- 资源缩减。
- ProGuard 规则。
- Release APK Artifact 上传。
- mapping.txt 上传。

不修改 ".github/workflows/release.yml"。

不修改 Debug GitHub Actions 工作流。

本次新增的两个后缀只能作用于 Debug buildType，不能影响 Release。

八、严格限制修改范围

原则上只修改：

"app/build.gradle.kts"

如果发现 Manifest 中存在双版本并存冲突，可按最小必要范围修改相关 Manifest 配置。

不得修改：

- Kotlin 核心业务代码。
- 四套内置提示词。
- 自定义提示词管理。
- ArticlePreprocessor。
- UserMessageBuilder。
- API 请求和 SSE。
- API Key 加密机制。
- Markdown 编辑与预览。
- HTML 转换逻辑。
- Room 数据库及迁移。
- 夜间模式。
- 底部导航。
- 应用名称。
- 桌面图标。
- GitHub Actions 触发规则。
- Gradle、AGP、Kotlin、Compose 的版本。

不新增第三方依赖。

不重新引入 "material-icons-extended"。

九、检查与验收

静态检查

请确认：

1. Debug applicationId = 原 applicationId + ".debug"。
2. Debug versionName = 原 versionName + "-debug"。
3. Release applicationId 没有改变。
4. Release versionName 没有改变。
5. versionCode 没有改变。
6. namespace 没有改变。
7. Debug 签名方式保持原样。
8. Release 签名方式保持原样。
9. 没有新增 FileProvider authorities 冲突。
10. 没有修改无关文件。

GitHub Actions 和真机验证

之后由用户在 GitHub Actions 构建 Debug APK，并在手机上验证：

1. 现有 Release AIReader 保持安装。
2. 新 Debug APK 能同时安装。
3. 手机上出现两个 AIReader 应用。
4. 两者桌面名称相同。
5. 两者桌面图标相同。
6. Debug 在系统应用信息中的版本名称带有 "-debug"。
7. Debug 能够正常调用 API。
8. Debug 的 Markdown、HTML 功能正常。
9. Debug 能独立保存阅读历史。
10. Debug 修改 API 配置不影响 Release。
11. 卸载 Debug 不影响 Release 数据。
12. 后续 Debug APK 可以正常覆盖升级已有 Debug 版。

十、开发环境及限制

当前开发环境：

- Ubuntu 26.04.1 LTS。
- Codex CLI。
- GitHub Actions 编译 APK。

不要安装 Android Studio。

不要在 Ubuntu 服务器本地执行 Android Gradle 构建。

不要自动执行 Git Commit。

不要自动执行 Git Push。

不要修改 GitHub Actions 工作流。

十一、完成后汇报

请简要报告：

1. 实际修改的文件。
2. 原始 applicationId。
3. 修改后的 Debug applicationId。
4. Release applicationId。
5. 原始 versionName。
6. 修改后的 Debug versionName。
7. Release versionName。
8. 是否检查了 FileProvider authorities。
9. 是否涉及 Kotlin 源码。
10. 是否修改任何 Release 相关配置。
11. 尚需 GitHub Actions 和真机验证的内容。

如果没有实际编译 APK，不得声称构建或安装验证已经通过。

本次任务只实现 Debug 的 ".debug" applicationIdSuffix 和 "-debug" versionNameSuffix，保持其余功能完全不变。
