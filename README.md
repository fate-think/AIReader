<div align="center">

# AIReader

**A native Android AI reading assistant designed to help you read long texts faster, extract key information, and understand more.**

Extract important points section by section, analyze entire texts, or create your own reading workflows with customizable prompts.

**Less reading overhead. More insight.**

**English** · [简体中文](README_zh.md)

</div>

## Overview

AIReader is an AI-assisted reading app for **books, long-form articles, and other lengthy texts**.

If you regularly read book chapters, long articles, study materials, or other text-heavy content, AIReader helps you process that material with large language models (LLMs) and focus on what matters most.

It offers two primary approaches to reading:

- **Section-by-section key point extraction:** Identify important information throughout a long text while preserving its structure, making it easier to follow individual passages and avoid missing useful details.
- **Full-text analysis and summarization:** Analyze the text as a whole to understand its main ideas, arguments, and conclusions.

You can also create custom prompts to build your own workflows for reading, studying, summarizing, or organizing information.

AIReader is not tied to a specific AI provider. You can connect your own OpenAI-compatible API or prepare the complete prompt and text for use in another AI client, even without an API key.

Generated or manually pasted Markdown results can be edited, previewed, saved, and converted to HTML. You can also open the HTML in a browser and use supported browser text-to-speech features to **listen to your reading reports**.

## Features

### 1. Key point extraction and full-text analysis

AIReader includes **five built-in reading prompts** for different long-text processing needs, including:

- **Key point extraction:** Identify important information throughout the text, following its structure.
- **Full-text analysis:** Examine the entire text to understand its main ideas, arguments, and overall meaning.
- **Additional reading workflows:** Select different built-in prompts depending on the kind of analysis you need.

Built-in prompts use Markdown and can be viewed, edited, saved, or restored to their defaults.

You can also create **unlimited custom prompts** based on the built-in examples, enabling workflows such as knowledge organization, concept explanation, detailed summarization, and personalized study.

> AI-generated results depend on the selected model, prompt, and input text. Verify important claims and details against the original material.

### 2. Automatic article preprocessing

Before sending text to a model, AIReader automatically preprocesses the article to make its structure easier to reference.

Preprocessing supports:

- Numbered paragraph markers.
- Numbered heading markers.
- Configurable grouping of natural paragraphs.
- Combining the processed text with the selected prompt into the final message sent to the model.

**By default, every three natural paragraphs are grouped together.**

You can change the grouping value in **Article Preprocessing Settings**, using any value from **1 to 100**.

| Value | Behavior |
| --- | --- |
| `1` | One natural paragraph per group |
| `2` | Two natural paragraphs per group |
| `3` | Three natural paragraphs per group (default) |
| `5` | Five natural paragraphs per group |

Smaller grouping values can provide more granular references to the text, while larger values reduce the number of groups.

### 3. Custom LLM API connections

AIReader supports user-configured **OpenAI-compatible Chat Completions APIs**.

Features include:

- Multiple independent API profiles.
- Custom model endpoints and model names.
- Encrypted API-key storage.
- Configurable reasoning effort where supported by the model.
- Adjustable network timeouts and automatic retries.
- Streaming model responses via Server-Sent Events (SSE).
- The ability to stop generation.

API keys are protected using Android Keystore-based encryption.

AIReader does not include free model usage credits. Availability, rate limits, and charges depend on your selected AI provider.

### 4. Use AIReader without an API key

**An API connection is optional if you only need AIReader to prepare prompts and text for another AI client.**

To use this workflow:

1. Choose a built-in or custom prompt.
2. Paste a book chapter, article, or other text.
3. Adjust preprocessing settings if needed.
4. Tap **Preview Final Sent Content**.
5. Copy the complete content into another AI client.
6. Generate the response using that client.

This lets you benefit from AIReader's prompt management and text preprocessing without configuring an API connection.

To make the result easier to copy back into AIReader, you can add an output rule like this to your prompt:

```markdown
## Output Format

Return the complete response in Markdown format,
enclosed in a single Markdown code block.
```

Copy the Markdown content from the resulting code block and paste it into AIReader's Markdown workspace. You can then preview, edit, or convert it to HTML.

> When using another AI client, you submit the text directly to that service. Check its privacy policy, input limits, and usage terms.

### 5. Markdown reading workspace

AIReader includes a Markdown workspace for viewing and working with reading results.

When you tap **Start Reading**, the app sends a request to the configured model API and displays the streamed response in the workspace.

The workspace supports:

- Markdown editing and preview.
- Copying reading reports.
- Pasting Markdown generated by external AI clients.
- Converting Markdown into HTML.
- Previewing HTML inside the app.
- Exporting HTML files.
- Opening HTML in an external browser.

This means the workspace is useful both for reports generated directly through the API and for results obtained from other AI tools.

### 6. Read or listen in your browser

One particularly useful workflow is turning AI-generated reading reports into content you can listen to.

After generating or pasting a Markdown report, select **More Actions → Open in Browser**. AIReader converts the content to HTML and opens it in a browser.

**Microsoft Edge** is a recommended option because it offers a **Read aloud** feature on supported versions.

A typical workflow is:

1. Generate or paste a Markdown reading report in AIReader.
2. Select **More Actions → Open in Browser**.
3. View the converted HTML in Microsoft Edge.
4. Use the browser's **Read aloud** feature to listen to the text.

This is useful for listening to summaries or key points from books and long articles while giving your eyes a break.

**Note:** Text-to-speech is provided by the external browser, not by AIReader itself. Availability, voices, and controls depend on the browser and version you use.

### 7. Reading history

AIReader stores reading history locally so that you can revisit previous reports.

Depending on the data saved with each entry, you can:

- Review saved Markdown reports.
- View or open HTML reading content.
- Access the original article if it was saved.
- Inspect the final content sent to the model if a request snapshot was recorded.
- Manage and clear all reading history.

Saving the original article is optional.

### 8. Interface and appearance

- Native Android application.
- Built with Jetpack Compose and Material 3.
- Four main navigation tabs: **Reading · Prompts · History · Settings**.
- Light, dark, and system theme support.

## How to use

### Option 1: Read with a configured API

1. Add an LLM API configuration under Settings.
2. Select a built-in or custom prompt for key point extraction, full-text analysis, or another reading task.
3. Paste a book chapter, long article, or other text into the Reading screen.
4. Adjust article preprocessing settings as needed.
5. Tap **Start Reading**.
6. Review the generated response in the Markdown workspace.
7. Edit, copy, save, export, or open the report in a browser for reading or text-to-speech.

### Option 2: Use another AI client without configuring an API

1. Select a prompt and paste your text.
2. Tap **Preview Final Sent Content**.
3. Copy the prepared content into another AI client.
4. Generate a Markdown response.
5. Paste the Markdown result into AIReader's workspace.
6. Preview it in the app or convert it to HTML for browser reading and text-to-speech.

## Install

Install a signed Release APK built from the repository's GitHub Actions workflow or obtained from the project's distribution channel, if one is available.

Android may require permission to install APKs from your chosen source. For in-place updates, the application ID and signing identity must match the existing installation, and the new `versionCode` must satisfy Android's upgrade requirements.

**Debug and Release can be installed side by side** when the Debug build uses a separate `.debug` application ID suffix. Their private app data is separate.

## Build

The project uses **Kotlin, Jetpack Compose, Material 3, Gradle, Room, DataStore, and OkHttp**.

Build with the repository's Gradle wrapper in a correctly configured Android SDK/JDK environment:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Release builds require signing credentials supplied through environment variables. The GitHub Actions workflows are intended for manually triggered builds:

- **Debug** — development and testing APK.
- **Release** — R8 code optimization and resource shrinking, signed APK verification, and separate R8 mapping artifact.

### Release signing

The Release workflow uses a protected GitHub Actions **`release` Environment**. Configure these Environment Secrets:

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing key alias |
| `RELEASE_KEY_PASSWORD` | Signing key password |

Configure `EXPECTED_RELEASE_CERT_SHA256` as an Environment Variable to verify the **signing certificate fingerprint**, not the SHA-256 hash of the keystore file.

Keep the keystore and passwords outside Git. Base64 is encoding, **not encryption**. Protect the release Environment, the main branch, and release tags. Never commit secrets or share private signing material in issues or logs.

## Project notes

- **Purpose:** AI-assisted reading of books, long articles, and other text-heavy content, with key point extraction, full-text analysis, and customizable reading workflows.
- **Navigation:** Reading · Prompts · History · Settings.
- **Privacy:** Reading history and model credentials are stored locally. When you submit an API reading request, the article and associated prompt content are sent to your configured model endpoint. If you use an external AI client, that provider's data-handling policies apply.
- **Data management:** Original article storage is optional; local reading history can be managed or cleared.
- **Development:** Use the `dev` branch for testing, then merge verified changes into `main` for Release builds.
- **Contributor instructions:** See [AGENTS.md](AGENTS.md) for repository-specific engineering conventions.

## Feedback

Bug reports and feature suggestions are welcome through the repository's issue tracker, if enabled. Do not include API keys, private article contents, or signing credentials in reports.

## License

AIReader is licensed under the [Apache License, Version 2.0](LICENSE).

You may use, modify, and distribute this project, including for commercial purposes, subject to the terms and conditions of the license. When redistributing it, retain required copyright and license notices and identify modified files as required by the license.

See [LICENSE](LICENSE) for the full license text.
