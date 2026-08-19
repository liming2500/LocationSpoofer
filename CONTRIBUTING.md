[简体中文](CONTRIBUTING.md) | [English](CONTRIBUTING_EN.md)

---

# LocationSpoofer 贡献指南

感谢你关注并愿意为 LocationSpoofer 做出贡献！🎉

来自开源社区的每一份贡献都能帮助 LocationSpoofer 变得更加稳定、高效与可靠。

---

## 📑 目录

1. [行为准则](#行为准则)
2. [我能如何做出贡献？](#我能如何做出贡献)
   * [反馈缺陷 (Bug)](#反馈缺陷-bug)
   * [提出新功能建议](#提出新功能建议)
   * [提交代码 (Pull Request)](#提交代码-pull-request)
3. [本地开发与编译](#本地开发与编译)
4. [项目架构与开发规范](#项目架构与开发规范)
5. [Git 提交信息规范 (Commit Conventions)](#git-提交信息规范-commit-conventions)

---

## 行为准则

本项目及所有参与者均受 [LocationSpoofer 行为准则](CODE_OF_CONDUCT.md) 的约束。参与项目即表示你同意遵守该准则。若发现违规行为，请及时联系项目维护者。

---

## 我能如何做出贡献？

### 反馈缺陷 (Bug)

在提交 Issue 前，请确认：
* 检索 [已有的 Issues](https://github.com/your-username/LocationSpoofer/issues) 确保问题未被重复汇报。
* 确保运行在支持的基础环境中（**Android 8.0+**、**KernelSU / APatch / Magisk**、**LSPosed API 101+**）。

通过 **缺陷报告模板** 提交问题时，请尽可能提供详细信息：
* **设备与环境**：Android 系统版本、机型 / ROM（如 HyperOS、LineageOS）、Root 方案（KernelSU/APatch/Magisk 及版本）、LSPosed 框架版本。
* **目标应用**：发生问题的目标应用名称与版本号。
* **复现步骤**：清晰的步骤说明。
* **日志与现象**：相关 Logcat 错误日志或 LSPosed 模块运行日志（特别是崩溃堆栈或异常回退坐标）。

### 提出新功能建议

非常欢迎提出各种功能建议！通过 **功能建议模板** 提交时，请阐述：
* 你目前遇到的痛点或现有功能的局限性。
* 你期望的实现方案与具体行为。
* 可能的边缘场景或兼容性考虑。

### 提交代码 (Pull Request)

1. **Fork 本仓库**，并从 `main` 分支切出你的特性分支：
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. **编写代码**，严格遵循 Kotlin / Jetpack Compose / MVVM 编码风格与架构规范。
3. **本地编译验证**：
   ```bash
   ./gradlew assembleDebug
   ```
4. **提交代码**，遵循标准 Commit 格式（详见 [Git 提交规范](#git-提交信息规范-commit-conventions)）。
5. **推送到你的 Fork 仓库**，并在 GitHub 发起面向 `main` 分支的 Pull Request。
6. 按照 PR 模板详细填写变更说明并完成自检清单。

---

## 本地开发与编译

### 前置环境
* **Android Studio**：Android Studio Hedgehog / Iguana / Jellyfish 或更新版本。
* **JDK**：OpenJDK 17 或 OpenJDK 21。
* **Android SDK**：Build Tools `34.0.0`+，compileSdk `34`。
* **测试设备**：一台已 Root 并安装 **KernelSU / APatch / Magisk** 及 **LSPosed (API 101+)** 的实体测试机。

### 编译构建
```bash
# 克隆仓库
git clone https://github.com/your-username/LocationSpoofer.git

# 进入目录
cd LocationSpoofer

# 编译 Debug APK
./gradlew assembleDebug

# 直接安装到已连接的设备
./gradlew installDebug
```

---

## 项目架构与开发规范

LocationSpoofer 基于 **MVVM + Clean Architecture** 构建：

* **语言**：100% Kotlin，使用 Coroutines 与 StateFlow 处理异步流。
* **UI 交互**：Jetpack Compose 与 Material Design 3。保持 Composable 组件高内聚、低耦合与模块化。
* **依赖注入**：Koin (`appModule`)。
* **本地存储**：Room Database（SQLite），涉及空间查询的部分需配备空间索引优化。
* **Xposed Hook 核心层**：
  * 位于 `com.suseoaa.locationspoofer.xposed`。
  * 严格遵循 **LSPosed API 101+ / libxposed (Service 模式)** 规范。
  * 高频 Hook 线程 0-IO 原则：通过后台守护线程异步轮询 `/data/local/tmp` 更新 Volatile 内存缓存，Hook 方法只从内存直读。
  * MultiDex 兼容安全性：通过动态 ClassLoader 拦截定位组件，并通过 `/proc/self/cmdline` 锁定宿主进程主包名，避免插件或内嵌 Webview 破坏全局上下文。
  * 保持调用栈深度清洗，避免暴露 Xposed 检查痕迹。

---

## Git 提交信息规范 (Commit Conventions)

我们遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/) 提交规范：

```
<type>(<scope>): <subject>
```

### 常用 Type 类型：
* `feat`: 新增功能
* `fix`: 修复缺陷
* `docs`: 文档变更
* `style`: 代码格式调整（不影响代码逻辑）
* `refactor`: 代码重构（既不修复 bug 也不添加特性的代码变更）
* `perf`: 性能优化
* `test`: 测试用例相关
* `chore`: 构建流程、依赖更新或辅助工具变动

### 提交范例：
```
feat(hook): 增加对次级 MultiDex 动态定位监听器的挂钩支持
fix(coords): 修复百度地图渲染图层坐标系偏移问题
docs: 更新 README 中关于 LSPosed API 101+ 的规范描述
```

---

再次感谢你对 LocationSpoofer 开源社区的支持与贡献！
