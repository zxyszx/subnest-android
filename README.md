# SubNest Android

基于 [Nodyssey](https://github.com/5151561/nodyssey) 改造的 SubNest 原生安卓客户端，采用 Kotlin 与 Compose，不是网页套壳。

## 当前状态

**改造初始化阶段，尚未提供可用的 SubNest APK。** 当前保留上游代码作为开发基础；论坛界面、登录、通知和更新逻辑尚未替换，直接构建仍是上游论坛应用，不要用于登录 SubNest。

本仓库独立于 [SubNest 网页与服务端](https://github.com/zxyszx/subnest)，不修改现有服务器及用户数据。上游 GitHub Actions 暂时停用，防止误发布论坛 APK 或沿用上游部署任务。

## 首个测试版范围

- 设置自己的 SubNest 服务器地址并登录。
- 原生订阅列表、会员共享与车位管理。
- 编号搜索，平台选择，空位、到期与逾期快捷筛选。
- 保留纵向滚动，不强制分页。
- 收到车友转账后手动确认续费，不实现自动扣款。
- 不集成消息推送；不影响以后手动下载安装新版 APK。
- 简化首页，仅展示有用的收入、支出、车位与待处理事项。

具体边界与迁移顺序见 [迁移计划](docs/subnest-migration.md)。

## 开发

上游要求 JDK 21 和 Android SDK 37。使用仓库内 Gradle wrapper：

```sh
./gradlew :app:assembleDebug
```

此命令目前构建的仍是上游应用，不表示 SubNest 业务已经接入。架构和质量要求见 [AGENTS.md](AGENTS.md)。服务器凭据、登录令牌、签名密钥与本地 SDK 路径不得提交。

## 来源与许可

基于 Nodyssey，原作者及贡献者的版权声明、GPL-3.0 许可证与 Git 历史保留。基线提交：`7eccd05bc45093dbf2fe31671ebd32979a0c6aee`。

本衍生项目按 [GPL-3.0](LICENSE) 开源。分发 APK 时同时提供对应版本源码及必要的构建说明。原项目介绍见 [上游 README](README.upstream.md)；其中的论坛下载、反馈与更新链接属于上游项目，不是 SubNest 的发布入口。本项目不代表 Nodyssey 或 NodeSeek 官方。
