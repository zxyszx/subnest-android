# SubNest Android

基于 [Nodyssey](https://github.com/5151561/nodyssey) 改造的 SubNest 原生安卓客户端，采用 Kotlin 与 Compose，不是网页套壳。

## 当前状态

**原生客户端预览阶段。** 安装包见 [Releases](https://github.com/zxyszx/subnest-android/releases)。当前预览包使用 debug 签名，仅供试用，尚非完整正式版。新增独立 `:subnest` 应用和 `:subnest-data` 数据模块，复用 Nodyssey 的 Compose 主题，不加载论坛页面、论坛后台任务、通知或上游更新器。上游 `:app` 仅保留作迁移参考，不是 SubNest 客户端。

本仓库独立于 [SubNest 网页与服务端](https://github.com/zxyszx/subnest)，不修改现有服务器及用户数据。上游 GitHub Actions 暂时停用，防止误发布论坛 APK 或沿用上游部署任务。

## 已接入的开发功能

- HTTPS 服务器登录、双因素验证码与恢复码验证；密码不持久化。
- Android Keystore 加密会话，凭据按服务器隔离，退出清除。
- 原生订阅列表：编号/账号搜索、完整平台下拉选择、今日/7 天内/逾期筛选。
- 原生共享账号列表：空位/已满筛选、查看车位和成员到期日。
- 订阅续费前确认，按服务器的原周期规则推进，不执行自动扣款。
- 纵向滚动、三个底部导航入口、主题随系统、切换页面保留筛选。
- Nodyssey 分层卡片与标签、共享账号整卡进入、车位占用进度和成员详情卡片；数字搜索精确匹配编号。

尚未接入：订阅新增/编辑、车友收款续费、仪表盘、Passkey、Turnstile 人机验证。开启人机验证的服务器目前可能无法从客户端登录，不会绕过验证。车位目前只读，不能代替网页版管理。

## 后续范围

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
./gradlew :subnest:assembleDebug
./gradlew :subnest-data:testDebugUnitTest :subnest:lintDebug spotlessCheck
```

开发 APK：`subnest/build/outputs/apk/debug/subnest-debug.apk`，包名 `io.github.zxyszx.subnest.debug`，与原 Nodyssey 独立安装。生产构建只允许 HTTPS；debug 额外允许模拟器回环服务器。

设备集成测试使用独立空库，在本机 `43210` 端口启动 SubNest 后端后运行 `node tools/seed-local-subnest.mjs`，然后 `./gradlew :subnest:connectedDebugAndroidTest`。此测试会续费其本地测试订阅，不得连接生产库；重新完整测试须使用新的测试数据库。

架构和质量要求见 [AGENTS.md](AGENTS.md)。真实服务器凭据、登录令牌、签名密钥与本地 SDK 路径不得提交。

## 来源与许可

基于 Nodyssey，原作者及贡献者的版权声明、GPL-3.0 许可证与 Git 历史保留。基线提交：`7eccd05bc45093dbf2fe31671ebd32979a0c6aee`。

本衍生项目按 [GPL-3.0](LICENSE) 开源。分发 APK 时同时提供对应版本源码及必要的构建说明。原项目介绍见 [上游 README](README.upstream.md)；其中的论坛下载、反馈与更新链接属于上游项目，不是 SubNest 的发布入口。本项目不代表 Nodyssey 或 NodeSeek 官方。
