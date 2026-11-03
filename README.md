# MyMap

安卓原生应用，同时接入 **百度地图导航 SDK** 与 **SAEP 协议**（Screen Automation Execution Protocol）。

## 功能

| 模块 | 说明 |
| --- | --- |
| 地图与导航 | `MapActivity`：百度 `MapView` 展示底图 + 专业导航 View，支持真实导航与模拟导航 |
| SAEP 开关 | 查询平台级 SAEP 启用状态（`RobotsHelperStub.isRobotsEnabled`） |
| 静态策略 | 预览 manifest 中 `com.obric.agentrobots.POLICY_JSON` 指向的策略 |
| 动态策略 | 通过 `content://com.obric.agentrobots.provider/policy` 更新策略 |
| 本地日志查询 | 按时间范围与 intent/activity 过滤审计日志 |
| 可信 Agent | 列出已注册 Agent UUID 并按 UUID 查询详情 |

## 环境要求

- JDK 17
- Android SDK：compileSdk 35、build-tools 35.0.0、platform-tools
- minSdk 23 / targetSdk 35

## 配置百度地图 AK

在 `gradle.properties` 中写入（该文件不入库）：

```properties
baiduMapAk=你的百度地图AK
```

AK 需要与**包名** `com.example.mymap` 和**签名 SHA1**匹配，否则导航 SDK 授权失败、导航不可用。
AK 通过 `manifestPlaceholders` 注入 manifest 的 `com.baidu.lbsapi.API_KEY`。

导航授权在 `MapActivity` 初始化成功后通过 `loadAuth(ak, deviceId)` 完成。

## 构建

```sh
./gradlew :app:assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest   # SAEP 策略单元测试
```

> 若工程路径包含中文，Gradle 的测试任务会报 `ClassNotFoundException`（测试类加载路径问题），
> 把工程复制到纯 ASCII 路径下执行测试与打包即可。

## SAEP 说明

SAEP 是字节跳动豆包手机助手发布的屏幕自动化操作声明协议，用来界定 AI Agent 能否代替用户
操作本应用。平台侧通过隐藏 Stub 暴露能力，本工程全部用反射访问，并在 Stub 缺失时
提示「平台不可用」而不是崩溃。

- 策略 schema：`AGRP-Policy/1.0`，大小上限 10 KiB
- 只约束四类 agent intent：`modify_content` / `post_content` / `delete_content` / `account_incentive`
- 仅在支持 SAEP 的平台（ObricUI 2.2+）上生效；普通 Android 设备上相关入口会返回「不可用」

静态策略位于 `app/src/main/res/raw/agent_saep_policy.json`，随包发布并在安装后被平台索引。

## 依赖版本

| 依赖 | 版本 |
| --- | --- |
| `com.baidu.lbsyun:BaiduMapSDK_Map-Navi` | 8.2.0.2 |
| `com.baidu.lbsyun:BaiduMapSDK_Location` | 9.6.5.1 |
| `com.baidu.lbsyun:BNGlide4` | 1.0.1 |
| `com.airbnb.android:lottie` | 3.6.0（导航底栏依赖，缺失会导致底栏不显示） |

## 已知限制

百度 maven 版导航 SDK 的原生库 `libBaiduMapSDK_map_for_navi_v8_2_0.so` 只提供
**arm64-v8a / armeabi-v7a**，没有 x86/x86_64。因此在 x86_64 模拟器上地图无法初始化，
应用会提示「地图初始化失败」而不会崩溃；验证地图与导航需要 **ARM 真机**（或 arm64 模拟器镜像）。
