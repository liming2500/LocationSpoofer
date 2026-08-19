<div align="center">

<h1>LocationSpoofer</h1>

<p>基于 KernelSU + LSPosed 的 Android 系统级虚拟定位与无线环境伪装框架</p>
<p>Android system-level location spoofing and wireless environment simulation framework based on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20101%2B-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Telegram](https://img.shields.io/badge/Telegram-交流群-blue.svg)](https://t.me/+CsxZGItXdW40ZWVl)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **📢 加入我们的 [Telegram 交流群](https://t.me/+CsxZGItXdW40ZWVl) 进行**~~技术探讨、催更、~~**吹牛逼、搞抽象。**
>
> **📖 查看 [LocationSpoofer 详细使用教程](https://docs.google.com/document/d/1fFEz3k7ATdN2dwY1L3RJn1QuzgokIsslNa88-vUPxPk/edit?usp=sharing)**

---

## 项目简介

在现代 Android 系统的风控与反作弊环境中，传统的“模拟位置（Mock Location）”开发者选项已被商业 SDK（如高德风控、腾讯安全、百度定位、网易易盾、各类考勤及打卡风控系统）列为高风险特征。这类检测机制不仅验证 `isFromMockProvider` / `isMock` 标志位，还会主动采集并交叉比对设备所处的周围物理环境：

*   **Wi-Fi 接入点与 BSSID 列表**（比对 Wi-Fi 信号指纹库）
*   **移动蜂窝基站数据**（GSM / WCDMA / LTE / 5G NR 小区指纹与运营商信息）
*   **周围 BLE 蓝牙信标**
*   **底层 GNSS 卫星分布与可见卫星星历**
*   **加速度计与计步器传感器联动状态**
*   对连续定位坐标序列进行傅里叶变换（FFT）或离散度分析，识别非自然的静态固定点或机械式等速直线轨迹。

**LocationSpoofer** 是专为应对深度风控检测而设计的**系统级虚拟定位与无线环境模拟方案**。
项目基于 **KernelSU / APatch / Magisk** 获取底层 Root 权限，并利用 **LSPosed (libxposed API 101+)** 框架在 Zygote 阶段注入目标应用进程，以高物理契合度拦截并伪造所有与位置、卫星、基站、Wi-Fi、蓝牙、传感器相关的底层 API 响应，确保应用获取自洽且真实的虚假环境指纹。

---

## 功能特性

```
┌─────────────────────────────────────────────────────────────────────────┐
│                             LocationSpoofer                             │
│                    系统级虚拟定位与无线环境模拟                         │
└─────────────────────────────────────────────────────────────────────────┘
        │                            │                            │
        ▼                            ▼                            ▼
  【空间定位与物理仿真】       【无线电与传感器模拟】         【反检测套件】
  • 三地图引擎无缝切换         • Wi-Fi 扫描与连接态伪装       • Xposed 堆栈调用帧清洗
  • WGS-84/GCJ-02/BD-09 自适应 • 2G-5G NR 蜂窝基站小区模拟   • ClassLoader 探测隔离
  • Ornstein-Uhlenbeck 抖动    • BLE 蓝牙信标扫描过滤         • isFromMockProvider 抹除
  • 步态横向摇摆与高斯噪声     • WiGLE / OpenCellID 云端导入  • AppOps OP_MOCK_LOCATION 隐藏
  • 真实路网拟合与红绿灯停候   • 空间反距离加权 (IDW) 插值    • Settings.Secure 开关覆写
  • 悬浮窗遥控阻尼摇杆         • 步频与计步传感器联动         • MultiDex 动态 Class 拦截
```

### 1. 多地图引擎与坐标系适配
* **三引擎切换**：集成高德 3D 地图 (AMap)、百度地图 (BaiduMap) 与 Google Maps，实现国内外路网检索与 POI 选点。
* **智能坐标系自适应（Smart Auto）**：
  * 系统原生接口统一输出标准 `WGS-84` 物理坐标与卫星数据；
  * 各大地图定位 SDK（高德/腾讯/百度）及视图渲染层（如百度地图蓝点 `MyLocationData`）自动映射对应坐标系（`GCJ-02` / `BD-09`），规避坐标偏移与 `(0.0, 0.0)` 兜底问题；
  * 支持在“配置应用坐标系”中为目标应用强制指定 `WGS-84`、`GCJ-02` 或 `BD-09`。
* **零延迟计算**：Hook 阶段直接从预计算的内存数据读取坐标，避免高频回调中的重复三角函数开销。

### 2. GPS 物理抖动与步态模拟
真实 GPS 芯片输出的坐标因电离层延迟、多径效应及接收机热噪声，天然具有高斯分布的白噪声特征。
* **Ornstein-Uhlenbeck 随机过程**：引入物理学均值回归随机过程模型来生成自然位置抖动，其状态随机微分方程定义为：

  $$\mathrm{d}X_t = -\alpha X_t \mathrm{d}t + \sigma \mathrm{d}W_t$$

  其中 $\sigma$ 为漂移强度，$\alpha$ 为均值回归系数（设定为 `0.05`，即每秒将当前漂移拉回 5%）。它产生符合物理规律的低频缓慢漂移，并在 3-Sigma 原则下严格有界（硬性限制在 4 米内），防止漂移发散引发异常。

* **步频横向抖动（Gait Jitter）**：步行或跑步模式下，引擎沿当前移动方向的正交横向上施加高斯横向位移：

  $$\Delta L_{\text{lateral}} = 0.15 \cdot \mathcal{N}(0, 1) \quad (\text{米})$$

  模拟人类真实行走时身体左右晃动的步态特征。

* **高度（Altitude）与精度（Accuracy）慢漂移**：精度值与海拔高度动态波动，模拟大气对流层延迟与卫星几何分布的自然变化。

### 3. 反检测与 MultiDex 支持
* **调用栈深度清洗（Stack Traces Scrubbing）**：拦截 `Throwable.getStackTrace` 和 `Thread.getStackTrace`，自动过滤移除包含 `de.robv.android.xposed`、`io.github.libxposed`、`org.lsposed` 等特征调用帧，阻止反作弊 SDK 在异常堆栈回溯中嗅探 Hook 框架。
* **类加载器探测隔离**：拦截 `Class.forName` 和 `ClassLoader.loadClass`，对 Xposed 特征类名的探测统一返回 `ClassNotFoundException`。
* **MultiDex 动态感知**：拦截 `ClassLoader.loadClass` 动态捕获并安装次级 Dex（如 `classes16.dex`）中的定位组件，配合 `/proc/self/cmdline` 锁定宿主进程主包名，避免内嵌 WebView 或插件改变全局上下文。
* **Mock 属性彻底抹除**：
  * `Location.isFromMockProvider()` 和 `Location.isMock()` 永久覆写为 `false`；
  * 反射将 `Location` 内部字段 `mMock` 和 `mIsFromMockProvider` 重写为 `false`，并移除 Extra Bundle 中的 `mockLocation` 标记；
  * 拦截 `AppOpsManager` 的 `OP_MOCK_LOCATION (58)` 权限查询，返回 `MODE_IGNORED (1)`；
  * 拦截 `Settings.Secure` 中 `mock_location` 及 `allow_mock_location` 的读取，返回 `0`；
  * 隐藏 `LocationManager` 中的虚拟 Test Provider，将其统一伪装为系统原生 `gps` 提供者。

### 4. Wi-Fi、基站与蓝牙环境模拟
* **实地扫街扫描器（EnvironmentScanner）**：后台自动扫描物理世界中的 Wi-Fi 接入点（SSID/BSSID/RSSI/频率/信道/Wi-Fi标准）、基站小区信息（GSM, WCDMA, CDMA, LTE, 5G NR 及 dbm 信号强度）以及附近 BLE 蓝牙信标。
* **空间反距离加权（IDW）插值**：在 Room 数据库中检索周边 50 米范围内的历史采集点，使用反距离平方比作为权重：

  $$w_i = \frac{1}{d_i^2}$$

  对 Wi-Fi RSSI 信号强度与蜂窝小区 dbm 信号进行平滑插值，保证移动过程中信号连续渐变。
* **无线电数据精细化管理与手动指定**：
  * 支持在“管理采集数据”页面中手动指定具体使用的 Wi-Fi、基站或蓝牙条目；
  * 支持自定义修改 Wi-Fi SSID、BSSID、RSSI 信号强度、信道频段、加密类型以及基站小区数据；
  * 支持在主页地图点选锁定特定环境数据，超出设定范围自动恢复环境插值计算。
* **Wi-Fi 与基站全接口模拟**：
  * 拦截 `WifiManager.getScanResults()`、`getConnectionInfo()`、`getConfiguredNetworks()`、`getDhcpInfo()`；
  * 拦截 `TelephonyManager.getAllCellInfo()`、`getCellLocation()`、`getNetworkOperator()`、`getServiceState()`、`getSignalStrength()`、`PhoneStateListener`、`TelephonyCallback`；
  * 采用真实品牌合法 OUI（TP-Link、Huawei、Xiaomi、Cisco 等）生成非采集区的虚拟 MAC 地址。
* **云端数据导入 (WiGLE & OpenCellID)**：支持配置 WiGLE API 与 OpenCellID API，在线拉取全球指定坐标周边的真实物理 Wi-Fi 与移动基站，自动入库缓存并离线复用。
* **海量数据空间索引**：采用空间索引与分页机制，支持高效检索与存储数千条本地与云端无线电记录，保障界面与后台运行流畅。

### 5. 卫星矩阵与 NMEA 模拟
* **GnssStatus 矩阵注入**：Hook 系统的 `GnssStatus` 类，模拟 20+ 颗包括 GPS、北斗、GLONASS 的卫星分布矩阵，注入 PRN 标识、信噪比（CNR）、俯仰角、方位角等，并正确汇报 `usedInFix` 状态。
* **NMEA 语句流动态拼装**：劫持 `OnNmeaMessageListener` / `GpsStatus.NmeaListener`，根据当前模拟坐标、航向角、速度和卫星信息，动态拼装符合规范的原始 `$GPGGA`, `$GPRMC`, `$GPGSA`, `$GPGSV` 语句并计算校验和（Checksum）输出。
* **卫星元数据保活注入**：在向各大地图 SDK 派发 `Location` 对象时，强制在 `getExtras()` 中注入 `satellites=20`, `satellites_in_view=20`, `satellites_used_in_fix=18`，防止定位 SDK 判定无卫星搜星而丢弃 GPS 信号。

### 6. 传感器与步频模拟
* **计步传感器联动**：Hook `SensorManager`，接管 `Sensor.TYPE_STEP_COUNTER`（总步数）和 `Sensor.TYPE_STEP_DETECTOR`（单步脉冲）。
* **步态频率计算**：当开启路线模拟且处于步行或跑步状态时，根据移动距离与步长（默认按 `0.7m/步` 换算）自动生成平滑递增的步数事件，适配运动健康与校园跑打卡软件。

### 7. 路线规划与红绿灯模拟
* **真实路网拟合**：支持多点路径规划，调用路网搜索算法拟合实际道路轮廓，防止直线穿墙。
* **红绿灯智能识别与驻留**：路线规划自动解析红绿灯路口，行进到红绿灯点时自动停驻 15 秒再重新平滑加速起步。
* **悬浮窗遥控摇杆**：提供悬浮窗虚拟摇杆，支持 0 ~ 10 m/s 速度无级调节与航向角阻尼转向。

### 8. 用户界面
* 基于 Jetpack Compose 构建，采用现代毛玻璃质感、内阴影与阻尼手势拖拽设计，实现完全模块化与解耦的组件架构。

---

## 系统架构

本项目采用 **MVVM + Clean Architecture** 架构，利用 Root 权限与系统共享内存通道规避了 Android 11+ 的沙盒可见性隔离，实现零权限跨进程配置传递：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       LocationSpoofer (宿主 App)                        │
│  ┌─────────────────────────┐  ┌──────────────────────────────────────┐  │
│  │     Triple Map Engine   │  │          RouteStateMachine           │  │
│  │ (AMap / Baidu / Google) │  │     (IDLE / READY / RUN / PAUSE)     │  │
│  └────────────┬────────────┘  └──────────────────┬───────────────────┘  │
│               │                                  │                      │
│  ┌────────────▼──────────────────────────────────▼───────────────────┐  │
│  │                       ConfigManager                               │  │
│  │        (将完整配置与多坐标系映射写入 /data/local/tmp 共享目录)       │  │
│  └──────────────────────────────────┬────────────────────────────────┘  │
│  ┌──────────────────────────────────▼────────────────────────────────┐  │
│  │                      SpoofingService                              │  │
│  │          (前台保活服务、步态引擎、路网计算与悬浮窗控制器)         │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────────────┘
                                      │ (写入 JSON 配置，chmod 777 + chcon)
                                      ▼
                        ┌───────────────────────────┐
                        │ /data/local/tmp/ 共享文件 │
                        └─────────────┬─────────────┘
                                      │ (守护线程 1000ms 轮询 + Volatile 缓存)
                                      ▼ LSPosed / libxposed (API 101+) 注入
┌─────────────────────────────────────────────────────────────────────────┐
│                            目标 App 进程                                │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                         LocationHooker                            │  │
│  │  • BaseLocationHooker (Location / LocationManager / NMEA / GNSS)  │  │
│  │  • MapSdkHooker (Baidu BDLocation / AMap / Tencent SDK & 蓝点图层) │  │
│  │  • WifiHooker (WifiManager / ScanResults / Connection / DHCP)     │  │
│  │  • CellHooker (TelephonyManager / 2G-5G NR 小区 / 运营商 / 基带)  │  │
│  │  • BluetoothHooker (BluetoothLeScanner / BLE Beacons 过滤)        │  │
│  │  • SensorStepHooker (StepCounter / StepDetector 计步联动)         │  │
│  │  • AntiDetectionHooker (Xposed 堆栈清洗 / ClassLoader 隔离)       │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

> [!NOTE]
> **跨进程通信 (IPC) 设计**：
> 目标 App 进程在沙盒内运行时，由于 Android 11+ 包可见性及 SELinux 策略，使用 `ContentProvider` 会导致主线程卡顿并产生 `Failed to find provider info` 错误。
> 宿主 App 借助 Root 权限将配置以 JSON 格式写入 `/data/local/tmp/locationspoofer_config.json`，赋予 `777` 权限及 `shell_data_file` SELinux 上下文。
> 目标沙盒内的 `LocationHooker` 启动后台守护线程，每 1000ms 异步读取文件并存储在 Volatile 内存中。主线程的 Hook 方法读取配置时为 0-IO 延迟，避免导致目标 App 丢帧与卡顿。

---

## 环境要求

* **系统版本**：Android 8.0 (API 26) 及以上
* **Root 方案**：已获取 Root 权限（推荐 [**KernelSU**](https://kernelsu.org) / **APatch** / **Magisk**）。
* **Xposed 框架**：已安装并激活 **LSPosed (API 101+)** 或兼容 libxposed API 101+ 的框架环境。

---

## 使用指南

### 1. 编译与安装

```bash
# 1. 克隆代码仓库
git clone https://github.com/your-username/LocationSpoofer.git

# 2. 编译 Debug APK 并直接安装到设备
./gradlew installDebug
```

### 2. 权限与激活
1. 打开 **KernelSU / APatch / Magisk** 管理器，授予 LocationSpoofer **Root 权限**。
2. 打开 **LSPosed** 管理器，在模块列表中找到 **LocationSpoofer** 并启用。
3. 在模块的作用域（Scope）中，**勾选需要进行定位伪装的目标应用**（如微信、企业微信、钉钉、超星学习通、百度地图、高德地图等）。
4. **强行停止**勾选的目标 App（或重启手机）以使其加载 Hook 逻辑。

### 3. 常见场景

#### 定点模拟与环境锁定
1. 启动 LocationSpoofer，在主页地图上拖动准星或搜索栏搜索目标位置。
2. 在底部抽屉开启所需伪装开关：**伪造 Wi-Fi**、**模拟基站**、**模拟蓝牙**、**模拟传感器计步**、**开启随机抖动**。
3. 点击“启动模拟”接管系统 GPS。
4. 如需锁定使用具体的本地采集点，可在“管理数据”中点击对应采集点进行锁定绑定。

#### 路线模拟
1. 切换至“路线规划”标签页，在地图上依次标记多个路点。
2. 设定移动模式（循环 / 往返 / 单程）以及速度档位（步行、跑步、骑行、自驾或自定义速度）。
3. 开启 **“使用真实路线规划”**（系统将自动拉取真实道路轮廓并标记红绿灯等待节点）。
4. 点击“开始模拟”。可随时开启“悬浮窗摇杆”在前台微调坐标与速度。

#### 实地采集与数据自定义
1. 开启“设置” -> **“环境图谱与扫街”** 模式，手机将在后台记录沿途的真实 Wi-Fi、基站与蓝牙信标。
2. 进入“管理采集数据”页面，可查看列表、编辑备注，或**手动指定/修改**某条 Wi-Fi（如 SSID、BSSID、RSSI 等）或基站小区数据。
3. 支持将采集到的无线电指纹一键**导出为 JSON 文件**，用于备份或共享导入。

#### 独立应用坐标系配置
* 若遇到特定 App 存在固定坐标偏移：
  * 进入 LocationSpoofer “设置” -> **“配置应用坐标系”**；
  * 添加目标应用包名；
  * 将坐标系指定为 `WGS-84`、`GCJ-02` 或 `BD-09`，Hook 层将自动根据目标 App 进行转换。

---

## 技术栈

* **编程语言**：100% Kotlin
* **UI 框架**：Jetpack Compose & Material Design 3 (Liquid Glass 拟态设计)
* **依赖注入**：Koin
* **持久化存储**：Room Database (SQLite) + 空间索引
* **网络与序列化**：OkHttp 3 + Kotlinx Serialization
* **地图组件**：AMap 3DMap SDK / BaiduMap SDK / Google Maps & Places SDK
* **Xposed 框架**：LSPosed API 101+ / libxposed (Service 模式)

---

## 免责声明

本程序**仅供学习研究、技术交流以及个人合法合规测试（如开发者定位测试、设备兼容性调试）使用**。
使用者请勿将本工具用于任何违法违规或违反相关平台服务协议的活动（包括但不限于虚假打卡、网络考试作弊、商业欺诈等）。
使用本模块造成的任何账号封禁、数据丢失、法律纠纷或其他直接/间接损失，均由使用者自行承担，作者不对此承担任何责任。

---

## 开源协议

本项目基于 [GNU General Public License v3.0](LICENSE) 协议开源。

```
Copyright (C) 2026 SuseOAA
```
