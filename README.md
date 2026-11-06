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

AK 写在 **`local.properties`**（该文件不入库）：

```properties
sdk.dir=C:/Users/kyozq/AppData/Local/Android/Sdk
baiduMapAk=你的百度地图AK
```

> 注意：`gradle.properties` 会被提交到 GitHub，不能用来放 AK。

AK 需要与**包名** `com.example.mymap` 和**签名 SHA1**匹配，否则地图与导航不可用。
AK 通过 `manifestPlaceholders` 注入 manifest 的 `com.baidu.lbsapi.API_KEY`。

启动时 `BaiduSdkInitializer` 会依次做三件事，缺一不可：

1. `SDKInitializer.setAgreePrivacy(context, true)` 与 `LocationClient.setAgreePrivacy(true)`
   —— 官方文档要求调用 SDK 任何接口前先设置隐私合规，否则地图不支持发起检索、
   路线规划等数据请求；
2. `SDKInitializer.setApiKey(ak)` —— 注入 AK；
3. `SDKInitializer.initialize(context)` —— 触发 AK 校验与 SDK 初始化。

导航授权在 `MapActivity` 初始化成功后通过 `loadAuth(ak, deviceId)` 完成。

## 配置语音播报（TTS，可选）

导航语音使用百度内置 TTS，需要额外申请授权（先申请 SDK 授权，再到
`https://ai.baidu.com/tech/speech` 申请语音技术应用，最后在地图开放平台申请离线 TTS 的 audio_key）。

同样写在 `local.properties`：

```properties
baiduTtsAppId=语音技术应用的 App ID
baiduTtsAppKey=语音技术应用的 API Key
baiduTtsSecretKey=语音技术应用的 Secret Key
baiduTtsAuthSn=离线 TTS 的 audio_key
```

四项通过 `manifestPlaceholders` 注入 manifest，运行时由 `NaviTtsConfig` 读取。
**任一项缺失时自动跳过 TTS 初始化**，导航功能不受影响，只是没有语音播报。

## 构建

```sh
./gradlew :app:assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest   # SAEP 策略单元测试
```

> 若工程路径包含中文，Gradle 的测试任务会报 `ClassNotFoundException`（测试类加载路径问题），
> 把工程复制到纯 ASCII 路径下执行测试与打包即可。

> 若所在网络无法访问 `services.gradle.org`（下载 Gradle 发行版），可把
> `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换成
> `https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip`，两者 sha256 一致。

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
| `com.baidu.lbsyun:NaviTts` | 3.2.13（语音播报） |
| `com.airbnb.android:lottie` | 3.6.0（导航底栏依赖，缺失会导致底栏不显示） |

## 已知限制

百度 maven 版导航 SDK 的原生库 `libBaiduMapSDK_map_for_navi_v8_2_0.so` 只提供
**arm64-v8a / armeabi-v7a**，没有 x86/x86_64。因此在 x86_64 模拟器上地图无法初始化，
应用会提示「地图初始化失败」而不会崩溃；验证地图与导航需要 **ARM 真机**（或 arm64 模拟器镜像）。

## CI

`.github/workflows/main.yml` 在每次 push 到 `main` 时编译 Debug APK 并跑单元测试，
产物以 artifact 形式上传。可选的仓库密钥（不配置也能编译，只是地图与语音不可用）：

| Secret | 用途 |
| --- | --- |
| `BAIDU_MAP_AK` | 百度地图 AK |
| `BAIDU_TTS_APP_ID` / `BAIDU_TTS_APP_KEY` / `BAIDU_TTS_SECRET_KEY` / `BAIDU_TTS_AUTH_SN` | 语音播报授权 |
