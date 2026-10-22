<div align="center">

<h1>LocationSpoofer</h1>

<p>基于 KernelSU + LSPosed 的 Android 系统级虚拟定位与无线环境模拟框架</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20101%2B-purple.svg)](https://github.com/LSPosed/LSPosed)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **本仓库只提供源码，不提供预编译 APK。** 需要自行编译安装，方法见[编译](#编译)。

---

## 项目简介

现代 Android 的风控与反作弊机制早已不只看 `isFromMockProvider` / `isMock` 标志位，还会交叉比对设备所处的物理环境：

* Wi-Fi 接入点与 BSSID 列表
* 移动蜂窝基站数据（GSM / WCDMA / LTE / 5G NR）
* 周围 BLE 蓝牙信标
* 底层 GNSS 卫星分布与星历
* 加速度计与计步器联动状态
* 对连续坐标序列做 FFT / 离散度分析

本项目通过 **KernelSU / APatch / Magisk** 获取 Root 权限，配合 **LSPosed（libxposed API 101+）** 在 Zygote 阶段注入目标进程，拦截位置、卫星、基站、Wi-Fi、蓝牙、传感器相关 API，返回彼此自洽的模拟数据；同时提供路线规划、运动拟真、环境数据采集等完整工具链。

---

## 功能特性

### 定位与坐标

| 功能 | 说明 |
|---|---|
| 定点模拟 | 在地图上选定位置后锁定坐标 |
| 三地图引擎 | 高德 / 百度 / Google 任意切换 |
| 坐标系自适应 | WGS-84 / GCJ-02 / BD-09 自动换算，可按包名单独指定 |
| 位置抖动 | Ornstein-Uhlenbeck 随机过程，模拟真实 GPS 漂移 |
| 海拔模拟 | 基准海拔 ± 可调范围，按地形起伏且坡度不超过 8% |
| 精度波动 | 定位精度在合理区间内自然浮动 |

### 路线模拟

| 功能 | 说明 |
|---|---|
| 路线规划 | 地图上逐点选取，支持撤销与重新选点 |
| 真实路网拟合 | 拉取真实道路轮廓，避免直线穿墙 |
| 红绿灯驻留 | 解析路口节点，到点停驻再起步 |
| 运行模式 | 循环（闭环正向 / 开放折返）、手动摇杆 |
| **循环圈数** | **直接输入圈数（0~999，0 或留空为不限），跑满自动停在终点** |
| **预估** | **实时显示预计总距离与预计耗时** |
| **跑完提醒** | **通知栏 / 声音 / 震动三种方式，可分别开关** |
| 就位等待 | 先停在起点喂稳定定位，等目标应用显示 GPS 正常并开始后，再点"开始跑步" |
| 路线录制 | 边移动边记录轨迹，按距离与转角抽稀后保存 |
| 悬浮窗摇杆 | 在其他应用上层实时微调方向与速度 |

### 运动拟真

| 功能 | 说明 |
|---|---|
| 速度起伏 | 长周期慢漂 + 短周期呼吸波，共享浮动额度（0~30%） |
| 步频起伏 | 独立滑块 0~30%，只影响计步传感器，不改变路线位置 |
| 起步/停车加减速 | 平滑斜坡，可调 0~10 秒 |
| 坡度联动 | 上坡减速、下坡加速，敏感度可调（0~100） |
| 起伏总开关 | 关闭后配速与步频完全恒定 |
| 航向跟随 | 航向由实测位移计算，折返、转角、起点等待、终点停车均正确 |
| 方向传感器覆盖 | 拦截旋转向量 / 方向传感器并兜底 `getOrientation`，保留真实俯仰与横滚 |
| 个人步态 | 录制 20 秒步态曲线，用于加速度计回放 |
| 计步模拟 | 计步器、步数检测器、加速度计三路联动，步频随速度自适应 |

### 无线电环境

| 功能 | 说明 |
|---|---|
| Wi-Fi 伪装 | 扫描列表、连接态、BSSID / RSSI / 信道 |
| 基站伪装 | 2G~5G NR 小区信息、信号强度、邻区列表 |
| BLE 伪装 | 蓝牙信标扫描结果过滤与重建 |
| 实地采集 | 沿途扫描真实 Wi-Fi / 基站 / 蓝牙并入库 |
| 数据管理 | 列表查看、备注编辑、单项增删改 |
| 云端导入 | WiGLE（Wi-Fi）、OpenCellID（基站） |
| 空间插值 | 反距离加权，按附近采样点生成环境数据 |
| 导入导出 | JSON 打包，可选择性导出位置、路线、环境、设置、密钥 |

### 系统级模拟

| 功能 | 说明 |
|---|---|
| 作用域模式 | 仅对 LSPosed 中勾选的应用生效 |
| 全局模式 | Hook 系统服务，对所有应用生效（需按厂商适配） |
| 应用坐标系 | 按包名指定该应用使用的坐标系 |
| 强制定位 | 为目标应用强制开启定位开关 |
| 适配进度 | 内置各厂商 / 机型实机验证状态 |

### 反检测

| 功能 | 说明 |
|---|---|
| Mock 标志抹除 | `isMock()` / `isFromMockProvider()` 及内部字段 |
| AppOps 隐藏 | `OP_MOCK_LOCATION` 查询返回忽略 |
| Settings 欺骗 | `mock_location` 相关键值覆写 |
| 堆栈清洗 | 过滤 Xposed / LSPosed 特征调用帧 |
| ClassLoader 隔离 | 特征类名探测返回 `ClassNotFoundException` |
| MultiDex 感知 | 动态捕获次级 Dex 中的定位组件 |
| 卫星矩阵注入 | GnssStatus / NMEA / GpsStatus 全套伪造 |
| 模拟器伪装 | 屏蔽常见模拟器特征 |

### 界面与工具

| 功能 | 说明 |
|---|---|
| 材质化界面 | Jetpack Compose + M3，含毛玻璃与阻尼拖拽组件 |
| 多语言 | 简体中文 / English / العربية |
| 后台保活 | 前台服务 + 开机自启 |
| Hook 状态页 | 实时显示各组件是否挂载成功 |
| 诊断工具 | Root 状态、框架连接、配置来源核对 |
| 数据备份 | 一键导出 / 合并导入 |

---

## 系统架构

采用 MVVM + Clean Architecture，拆分为 6 个 Gradle 模块：

| 模块 | 类型 | 职责 |
|---|---|---|
| `app` | App | 宿主壳工程，聚合依赖注入与打包配置，把 `xposed` 产物打入 APK 供 LSPosed 加载 |
| `app-ui` | Library | 全部 Jetpack Compose UI 与 ViewModel |
| `service` | Library | 前台保活服务、悬浮摇杆、开机自启 |
| `xposed` | Library | LSPosed 注入模块，各类 Hook 实现 |
| `core-data` | Library | Room 数据库、Repository、配置与 Root 管理 |
| `core-geo` | JVM | 坐标系、路线几何、运动真实度引擎（不依赖 Android） |

App 与 Hook 之间通过 libxposed 远程配置通道传递 JSON：`ConfigManager` 发布配置快照，Hook 侧后台线程解码后原子替换内存缓存，Hook 调用只读缓存，避免高频回调中的解析开销。

**多进程一致性**：路线位置、航向、步数、速度在 App 进程、目标应用进程、`system_server` 中各自独立计算，全部以确定性函数实现，任意进程对同一时刻得到逐位相同的结果。

---

## 环境要求

* **系统**：Android 8.0 (API 26) 及以上
* **Root**：KernelSU / APatch / Magisk
* **框架**：LSPosed（API 101+）或兼容 libxposed API 101+ 的框架

---

## 编译

需要 **JDK 17** 与 **Android SDK**（compileSdk 见 `app/build.gradle.kts`）。

```bash
git clone https://github.com/liming2500/LocationSpoofer.git
cd LocationSpoofer

# 配置 SDK 路径（或使用 ANDROID_HOME 环境变量）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 编译 Debug APK
./gradlew assembleScopedDebug
# 产物：app/build/outputs/apk/scoped/debug/
```

**Global 变体**：`./gradlew assembleGlobalDebug`。

**Windows 注意**：项目放在中文路径下时，`gradle.properties` 已通过 `android.overridePathCheck=true` 关闭路径检查；但部分 Gradle 版本在中文路径下运行单元测试会抛 `ClassNotFoundException`，建议使用纯英文路径。

**Release 版**：仓库不附带签名密钥，需在 `app/build.gradle.kts` 中配置 `signingConfigs` 后执行 `./gradlew assembleScopedRelease`。

---

## 使用

1. 在 Root 管理器中授予本应用 Root 权限。
2. 在 LSPosed 管理器中启用模块，勾选需要模拟的目标应用。
3. 强制停止目标应用（或重启设备）使其加载 Hook。
4. 打开应用选择位置或规划路线，配置参数后开始模拟。

### 路线模拟推荐流程

```
规划路线 → 点"开始模拟" → 进入就位等待
        ↓
打开目标应用，等它显示 GPS 正常并点击开始
        ↓
回到本应用点"开始跑步" → 按设定圈数沿路线行进
        ↓
跑满圈数 → 按设置发出通知 / 声音 / 震动提醒
```

先停在起点等待是必要的：目标应用首次拿到定位时会校验 GPS 质量，直接以运动状态开始容易被判为异常。

### 运动真实度设置

在「设置 → 运动真实度」中配置：

| 设置项 | 说明 |
|---|---|
| 随机强度 | 步幅、海拔、加速度波形的附加起伏幅度 |
| 速度起伏范围 | 路线配速的缓慢起伏（0~30%） |
| 步频起伏范围 | 计步传感器步频的起伏（0~30%），不影响路线位置 |
| 起步/停车加减速 | 平滑斜坡时长（0~10 秒） |
| 地形对配速的影响 | 坡度敏感度（0~100），0 为关闭 |
| 个人步态 | 录制自己的步态曲线 |
| 跑完提醒 | 通知栏 / 声音 / 震动开关 |

这些参数在开始模拟时取快照，修改后需重新开始模拟才会生效。

---

## 已知限制

* 全局方案（Hook 系统服务）依赖各厂商内部实现，适配情况见 `ADAPTATION_PROGRESS.md`。
* 部分地图 / 定位服务的行为可能随其 SDK 更新而变化。
* 少数应用直接读取 Native 层 GNSS 数据，Java 层 Hook 无法覆盖。
* 在中文目录下构建时，单元测试可能因 Gradle 路径处理而失败，请使用英文路径。

---

## 免责声明

本程序仅供学习研究、技术交流及个人合法合规测试（如开发者定位测试、设备兼容性调试）使用。

**请勿将其用于虚假考勤、伪造考核或运动记录、网络考试作弊、商业欺诈，或任何违反法律法规与平台服务协议的行为。** 使用本程序造成的一切后果（账号封禁、数据丢失、法律纠纷等）由使用者自行承担。

Root 授权与系统级 Hook 可能影响系统稳定性与应用兼容性，建议使用独立测试设备并提前备份数据。

---

## 版本更新

### 3.0.4

**新增**
- 路线循环圈数：直接输入数字（0~999，0 或留空表示不限），跑满后自动停在终点
- 预计时间与距离：配置界面实时显示，随圈数与速度档位变化
- 跑完提醒：通知栏 / 声音 / 震动三项独立开关，位于「设置 → 运动真实度」
- 就位等待：路线启动先停在起点，等目标应用显示 GPS 正常并开始后，再点"开始跑步"

**改进**
- 方向传感器覆盖：旋转向量、方向传感器、`getOrientation` 三重拦截，设备朝向改为运动航向并保留真实俯仰与横滚
- 速度拟真：起步 / 停车加减速斜坡、坡度联动、短周期呼吸波
- 步频起伏独立可调，与速度起伏共享总开关
- 路线位置修正：分段积分一次性预计算，修复多进程间的位置跳变
- 修复路线模式坐标抖动导致的"GPS 信号弱"（经纬度重复调用随机游走）
- 修复伪造卫星状态异常时吞掉回调、导致 App 收不到卫星数据

**修复**
- 修复运动真实度页面因格式化字符串不合法导致的闪退
- 补全零长度路线的取模保护

**构建**
- 支持中文目录构建（`android.overridePathCheck`）
- 移除历史提交中的预编译 APK，仓库体积 122MB → 约 3MB

---

## 开源协议

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。

```
Copyright (C) 2026 SuseOAA
```
