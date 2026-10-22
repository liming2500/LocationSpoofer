<div align="center">

<h1>LocationSpoofer （修改版）</h1>

<p>基于 KernelSU + LSPosed 的 Android 系统级虚拟定位与无线环境伪装框架</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org)

</div>

---

## 关于本仓库

本仓库是 [meat-daemon-404/LocationSpoofer](https://github.com/meat-daemon-404/LocationSpoofer) 的**修改版**，遵循 GPL-3.0 继续开源。

**只提供源码，不提供任何预编译 APK 或安装包。** 需要自行编译安装，编译方法见下文「编译」章节。

### 相对上游的改动

| 改动 | 说明 |
|---|---|
| 步频起伏可调 | 步频起伏原先写死在“随机强度”档位里（1.5%~5%），现拆成独立设置项，0~30% 滑块调节，与海拔变化范围并列 |
| 速度起伏入口 | 速度浮动原仅在“运动真实度”页，现补进“开始模拟”和“路线配置”弹窗，与海拔滑块并排 |
| 航向跟随运动方向 | 航向改由前后采样位置的实测位移计算：折返点即时转向、起点等待指向出发方向、终点停车保持最后行进方向、转角处不再有偏差 |
| 方向传感器覆盖 | 拦截旋转向量、方向传感器并兜底 `getOrientation`，把设备朝向强制改为运动航向，同时保留真实俯仰与横滚 |
| 多进程一致性 | App 端 `MotionController` 与 Xposed 端 `RouteEngine` 使用同一公式，保证各进程看到同一位置与航向 |
| 零长度路线保护 | 补全 `RoutePath` 在总长为 0 时的取模保护，避免 `NaN` 位置 |
| 中文路径构建 | `gradle.properties` 增加 `android.overridePathCheck=true`，允许项目放在中文目录下构建 |
| 仓库整理 | 移除了历史提交中的预编译 APK，仓库体积从 122MB 降至约 3MB |

除上述改动外，功能与上游保持一致。改动集中在 `core-geo`、`core-data`、`app-ui` 三个模块。

### 与上游的关系

* 本项目基于上游代码修改，**非原作者官方发布**，不代表上游维护者的立场。
* 上游原作者及版权声明（`Copyright (C) 2026 SuseOAA`）完整保留在 `LICENSE` 中。
* 上游仓库已归档，本修改版同样**不提供任何形式的支持、适配或更新承诺**。
* 遇到问题请先确认是否为上游本身的问题，不要向上游仓库提交与本修改版相关的 issue。

---

## 项目简介

Android 系统的风控与反作弊机制已不再只看 `isFromMockProvider` / `isMock` 标志位，还会交叉比对设备周围环境：

* Wi-Fi 接入点与 BSSID 列表
* 移动蜂窝基站数据（GSM / WCDMA / LTE / 5G NR）
* 周围 BLE 蓝牙信标
* 底层 GNSS 卫星分布与星历
* 加速度计与计步器联动状态
* 对连续坐标序列做 FFT / 离散度分析

本项目通过 **KernelSU / APatch / Magisk** 获取 Root 权限，配合 **LSPosed（libxposed API 101+）** 在 Zygote 阶段注入目标进程，拦截位置、卫星、基站、Wi-Fi、蓝牙、传感器相关 API，返回自洽的模拟数据。

---

## 功能特性

* **定位仿真**：三地图引擎切换、WGS-84 / GCJ-02 / BD-09 自适应、Ornstein-Uhlenbeck 位置抖动
* **运动真实度**：速度起伏、步频起伏、海拔起伏、步态横向摇摆、加速度计波形
* **路线模拟**：真实路网拟合、循环 / 往返 / 单程、红绿灯驻留、悬浮窗摇杆
* **环境模拟**：Wi-Fi / 蜂窝基站 / BLE 蓝牙扫描与连接态伪装，WiGLE、OpenCellID 云端数据导入
* **传感器模拟**：计步器、步数检测器、加速度计联动
* **反检测**：Xposed 堆栈清洗、ClassLoader 探测隔离、Mock 标志抹除、AppOps 隐藏

---

## 系统架构

采用 MVVM + Clean Architecture，拆分为 6 个 Gradle 模块：

| 模块 | 类型 | 职责 |
|---|---|---|
| `app` | App | 宿主壳工程，聚合依赖注入与打包配置，并将 `xposed` 产物打入 APK 供 LSPosed 加载 |
| `app-ui` | Library | 全部 Jetpack Compose UI 与 ViewModel |
| `service` | Library | 前台保活服务、悬浮摇杆、开机自启 |
| `xposed` | Library | LSPosed 注入模块，各类 Hook 实现 |
| `core-data` | Library | Room 数据库、Repository、配置与 Root 管理 |
| `core-geo` | JVM | 坐标系、路线几何、运动真实度引擎（不依赖 Android） |

App 与 Hook 之间通过 libxposed 远程配置通道传递 JSON：`ConfigManager` 发布配置快照，Hook 侧后台线程解码后原子替换内存缓存，Hook 调用只读缓存，避免高频回调中的解析开销。

---

## 环境要求

* **系统**：Android 8.0 (API 26) 及以上
* **Root**：KernelSU / APatch / Magisk
* **框架**：LSPosed（API 101+）或兼容 libxposed API 101+ 的框架

---

## 编译

需要 **JDK 17** 与 **Android SDK**（compileSdk 见 `app/build.gradle.kts`）。

```bash
# 克隆
git clone https://github.com/liming2500/LocationSpoofer.git
cd LocationSpoofer

# 配置 SDK 路径（或用 ANDROID_HOME 环境变量）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 编译 Debug APK
./gradlew assembleScopedDebug

# 产物：app/build/outputs/apk/scoped/debug/
```

**Windows 用户注意**：如果项目放在中文路径下，`gradle.properties` 中的 `android.overridePathCheck=true` 已关闭路径检查；但部分 Gradle 版本在中文路径下运行单元测试会出现 `ClassNotFoundException`，建议将项目放在纯英文路径下编译。

**Release 版**：仓库未附带签名密钥。如需自行签名，在 `app/build.gradle.kts` 中配置 `signingConfigs` 后执行 `./gradlew assembleScopedRelease`。

---

## 使用

1. Root 管理器中授予本应用 Root 权限。
2. LSPosed 管理器中启用模块，并勾选需要模拟的目标应用。
3. 强制停止目标应用（或重启设备）使其加载 Hook。
4. 打开应用选择位置、配置路线与模拟参数，点击“开始模拟”。

### 运动真实度设置

在“设置 → 运动真实度”中配置：

* **随机强度**：步幅、海拔、加速度波形的附加起伏幅度
* **速度起伏范围**：路线配速的缓慢起伏（±0%~30%）
* **步频起伏范围**：计步传感器步频的缓慢起伏（±0%~30%），叠加在随机强度之上，不影响路线位置
* **个人步态**：录制自己的步态曲线用于加速度计回放

速度与步频起伏也可在“开始模拟”和“路线配置”弹窗中直接调节。这些参数在开始模拟时取快照，修改后需重新开始模拟才会生效。

---

## 已知限制

* 上游仓库已归档，无官方适配支持；不同厂商 ROM 需自行排查。
* 全局方案（Hook 系统服务）依赖各厂商内部实现，适配情况见 `ADAPTATION_PROGRESS.md`。
* 部分地图 / 定位服务的行为可能随其 SDK 更新而变化。
* 本项目在中文目录下构建时，单元测试可能因 Gradle 的路径处理而失败，请使用英文路径。

---

## 免责声明

本程序仅供学习研究、技术交流及个人合法合规测试（如开发者定位测试、设备兼容性调试）使用。

**请勿将其用于虚假考勤、伪造考核或运动记录、网络考试作弊、商业欺诈，或任何违反法律法规与平台服务协议的行为。** 使用本程序造成的一切后果（账号封禁、数据丢失、法律纠纷等）由使用者自行承担。

Root 授权与系统级 Hook 可能影响系统稳定性与应用兼容性，建议使用独立测试设备并提前备份数据。

---

## 开源协议

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。

```
Copyright (C) 2026 SuseOAA
```

原始版权归上游作者所有。本修改版同样以 GPL-3.0 发布，修改内容亦遵循该协议。
