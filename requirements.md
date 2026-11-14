AIReader Release 签名方案调整：显式启用 v2 + v3

一、任务目标

AIReader 当前已经配置了 GitHub Actions Release 自动签名流程。

现有 Release signingConfig 通过环境变量读取：

- RELEASE_KEYSTORE_FILE
- RELEASE_STORE_PASSWORD
- RELEASE_KEY_ALIAS
- RELEASE_KEY_PASSWORD

但是尚未显式指定 APK Signature Scheme。

本次要求：

Release APK 只启用 v2 + v3 签名，明确关闭 v1 + v4。

同时确保 GitHub Actions 对最终 APK 进行实际验证。

二、先检查项目

请先阅读 AGENTS.md，并检查：

1. "app/build.gradle.kts"
2. ".github/workflows/release.yml"
3. 当前 Android Gradle Plugin 版本
4. 当前 "minSdk"
5. 现有 Release signingConfig

首先确认 "minSdk >= 24"。

如果 "minSdk < 24"，请停止本次修改并报告原因，不要擅自提高 minSdk 或关闭旧系统兼容性。

三、显式指定 Release 签名方案

在现有 "signingConfigs.create("release")" 中增加以下配置：

enableV1Signing = false
enableV2Signing = true
enableV3Signing = true
enableV4Signing = false

最终应保持现有的环境变量读取方式，例如：

signingConfigs {
    create("release") {
        providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull?.let {
            storeFile = file(it)
        }
        storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
        keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
        keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull

        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = false
    }
}

请确认这些属性与项目当前 AGP 版本兼容。

不要修改密钥库路径、密码读取、Key Alias 或原有签名身份。

不要生成新的 Keystore。

四、GitHub Actions 验证

当前 ".github/workflows/release.yml" 已经包含：

- workflow_dispatch 手动触发
- main 分支校验
- release Environment
- Environment Secrets 自动签名
- apksigner 签名验证
- EXPECTED_RELEASE_CERT_SHA256 证书指纹校验
- Release APK 上传
- R8 mapping.txt 上传

以上机制全部保留。

在现有 "apksigner verify --verbose --print-certs" 验证步骤中，增加严格校验：

1. v1 签名为 false。
2. v2 签名为 true。
3. v3 签名为 true。
4. v4 签名为 false（如工具提供该项输出）。
5. 签名证书 SHA-256 与 EXPECTED_RELEASE_CERT_SHA256 完全一致。

需要注意，不同版本的 apksigner 输出可能略有差异，应检查实际输出格式，采用可靠的校验方式。

v2 或 v3 验证失败时，工作流必须失败。

检测到意外启用 v1 时，工作流也必须失败。

不要将 v3.1 与 v3 混为一谈；如果当前工具输出 v3.1 状态，不要误判为 v3 失败。

保留原有 APK 完整性验证和证书指纹验证。

不要输出 Keystore 内容、Base64、签名密码或其他 Secrets。

五、安全要求

继续使用原有 GitHub "release" Environment。

继续通过以下 Secrets 读取凭据：

- RELEASE_KEYSTORE_BASE64
- RELEASE_STORE_PASSWORD
- RELEASE_KEY_ALIAS
- RELEASE_KEY_PASSWORD

继续通过 Environment Variable 读取：

- EXPECTED_RELEASE_CERT_SHA256

不得改变现有安全机制。

特别注意：

APK 签名方案与签名证书身份是两个不同的概念。

旧版 AIReader 使用 v1 + v2 + v3，新版只使用 v2 + v3，不会仅因为关闭 v1 就改变签名证书。

仍须确保 applicationId 不变、签名身份一致，并满足 Android 覆盖升级的版本要求。

六、严格限制修改范围

本次原则上只允许修改：

1. "app/build.gradle.kts"
2. ".github/workflows/release.yml"

不得修改：

- Debug signingConfig。
- R8 和资源压缩配置。
- ProGuard 规则。
- GitHub Actions 触发方式。
- Environment 配置名称。
- GitHub Secrets 名称。
- APK Artifact 上传。
- mapping.txt 上传。
- Gradle、AGP 或 Kotlin 版本。
- applicationId。
- minSdk。
- versionCode。
- versionName。
- Kotlin 业务代码。
- AIReader 的任何 UI 或功能。

不要新增依赖。

不要自动 Git Commit 或 Git Push。

不要在 Ubuntu 服务器本地执行 Android Gradle 构建。

七、完成后报告

请报告：

1. 当前实际 minSdk。
2. 当前实际 AGP 版本。
3. 修改了哪些文件。
4. Release 是否明确关闭 v1。
5. Release 是否明确启用 v2。
6. Release 是否明确启用 v3。
7. Release 是否明确关闭 v4。
8. GitHub Actions 如何验证实际 APK 签名方案。
9. 原有证书 SHA-256 校验是否保留。
10. 原有自动签名与 R8 构建流程是否保持不变。

如果尚未运行 GitHub Actions，请明确说明实际 APK 签名结果仍需要 CI 构建后验证。

请直接实施这项小范围修改，不扩大开发范围。
