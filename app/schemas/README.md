Room 的 schema JSON 在 GitHub Actions 编译时由 KSP 生成到此目录。当前数据库版本为 2，版本 1 到 2 使用显式 `MIGRATION_1_2`。仓库尚未包含生成的 schema JSON；应在 Actions 构建后补交生成文件，作为后续迁移测试的基线。项目没有启用 destructive migration。
