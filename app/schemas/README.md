Room 的 schema JSON 在 GitHub Actions 编译时由 KSP 生成到此目录。首次构建后应将生成的 `1.json` 提交到仓库，作为后续数据库迁移测试的基线。升级数据库版本时必须提供显式 Migration；项目没有启用 destructive migration。
