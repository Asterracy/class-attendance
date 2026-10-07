# 班级考勤 / Class Attendance

一个轻量、离线、手机竖屏使用的 Android 考勤工具。Android 6.0（API 23）及以上，无账号、网络权限、后台服务或第三方运行库。

有名单时，首页只显示姓名和“开始考勤”。开始后逐人勾选实际到场的人，草稿即时保存在本机，退出后可继续。正式保存生成独立的时间记录，不覆盖历史；可查看历史并导出真正的 Excel `.xlsx`，包含逐人姓名、已到/未到、记录时间和人数汇总。状态仅表示本次手动勾选结果。取消、删除、清空和替换名单均需明确确认。

**公开源码默认不含任何真实名单。** 默认构建会生成空的名单资产；可在应用中编辑名单，或由 Agent 按以下流程为用户制作首次打开就有姓名的私人 APK。名单编辑是备用入口，定制交付不应要求用户临时手动录入。

## Agent 为用户预置自己的名单

1. **确认授权与范围。** 只读取用户明确提供的名单文件、截图或文本，确认所选行属于该班。不要补入被用户截掉的其他班学生。不要查看无关文件，也不要将原图或名单发送给第三方 OCR 服务。
2. **只提取姓名。** 排除性别、宿舍、房间、床位、电话、学号等字段。图片必须实际查看像素，不能凭摘要猜姓名。模糊字逐字询问用户，保留确实存在的同名条目，并与用户核对总人数及边界。
3. **制作本地私人名单。** 在自己的工作副本中新建 `assets/names.txt`，UTF-8、无 BOM，每行一个姓名，不含标题、序号、空行或其他信息。它受 `.gitignore` 保护，不能提交。`assets/names.example.txt` 仅含虚构示例，可用于练习；公开仓库不跟踪真正的 `names.txt`。
4. **验证再构建。** 运行 `python3 scripts/validate_names.py assets/names.txt`。核对人数及重复姓名，避免在日志中输出真实名单。构建脚本会将这个本地文件复制到构建资产 `names.txt`；没有该文件时才使用空名单。按下一节自建签名并运行 `./build.sh`。
5. **验证实际 APK。** 用本地 ZIP 工具或脚本读取 APK 内 `assets/names.txt`，与本地名单逐字比较、核对人数；用 `apksigner verify --verbose` 验签。在可用的安卓设备或模拟器上确认首次打开直接显示姓名、勾选草稿重开不丢、保存历史、Excel 姓名与人数正确。没有设备时如实注明未实测。
6. **私人交付。** 含真实名单的 APK 仅交付该用户，不上传到本公开仓库、Release、Issue、CI artifact 或公开网站。交付内容包括 APK、人数、SHA256、签名/版本和测试范围。不要自动安装到用户手机或改动设备安全设置。

名单在 SQLite 数据库**首次创建**时从 APK 资产加载。后续重启、覆盖安装或更新不会重置已编辑名单、草稿或历史，历史保存的是当时姓名和结果的快照。对于已有安装，修改 APK 资产不会替换数据库中的旧名单，应通过应用中的“编辑名单”更新；不要通过卸载或清空应用数据强行更新名单。卸载或系统清除应用数据会删除本机记录，应先导出需要保留的历史。

## 构建与自建签名

准备 Bash、ZIP CLI、Python 3、JDK 17+，以及单独安装的 Google 官方 Android SDK Platform 36 和 Build Tools 36.0.0。SDK/JDK 不随源码分发；遵循其各自条款。可以用 SDK Manager 安装必要组件：

```sh
sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

设置 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME` 指向已安装 SDK。也可单独指定 `ATTENDANCE_BUILD_TOOLS` 和 `ATTENDANCE_ANDROID_JAR`。`JAVA_HOME` 或 `ATTENDANCE_JAVA_HOME` 可指定 JDK；未设置时使用 PATH 上的 Java 命令。不要将个人机器路径写入被提交的文件。

自行创建私人签名密钥，命令会交互式请求口令。采用用户自己的强口令，保存在本地，不写入源码：

```sh
mkdir -p .private/signing
keytool -genkeypair -keystore .private/signing/release.p12 \
  -storetype PKCS12 -alias attendance -keyalg RSA -keysize 3072 \
  -validity 10000 -dname "CN=Class Attendance Personal Build"
chmod 600 .private/signing/release.p12
./build.sh
```

输出为 `dist/class-attendance.apk`。构建使用 AAPT2 → javac（Java 8 字节码）→ D8 → zipalign → apksigner，默认生成 v1/v2/v3 签名，随后验签和检查对齐。没有内置共享密钥或默认口令。保留自己的密钥，后续更新须使用同一签名；已安装其他签名的私人版本时不可直接用新的密钥覆盖安装。

可用 `ATTENDANCE_KEYSTORE` 和 `ATTENDANCE_KEY_ALIAS` 指定已有私人密钥。自动化构建可通过受保护的环境变量 `ATTENDANCE_STORE_PASSWORD`、`ATTENDANCE_KEY_PASSWORD` 提供口令；不要将它们写入命令日志、脚本、`.env` 的提交版本或公开 CI。只检查编译时，可运行 `ATTENDANCE_UNSIGNED_ONLY=1 ./build.sh`，其输出未经签名，不能安装。

此脚本面向 macOS/Linux Bash 环境。Windows 用户可自行移植或使用合适的构建环境；未提供桌面界面适配。

## 防止私人名单被误提交

首次使用本仓库先启用本地检查：

```sh
git config core.hooksPath .githooks
```

`.gitignore` 排除真实 `assets/names.txt`、签名目录、截图、数据库、考勤导出、SDK、构建产物与 APK。**忽略规则不保护已被跟踪的文件。** 因此公开仓库使用未跟踪的本地名单和独立的合成示例；不要对私人名单使用 `git add -f`。

提交或推送前检查 `git diff --cached` 并运行 `python3 scripts/check_public_commit.py`。钩子会拒绝强制加入的名单文件、常见私人数据与二进制产物、个人路径和内联签名口令。还需人工检查源码、测试、README、提交消息和历史，避免把姓名移到其他文本中；钩子可被绕过，不能保证任意文字无隐私信息。如果私人信息已经进入历史，应停止公开推送并在新的干净副本中重新准备，不仅是删除工作区文件。

## 测试与已知范围

源码附带合成数据的 JVM 导出测试及 Python 标准库 ZIP/XML/SQLite 检查：

```sh
./tests/run.sh
```

先执行一次构建。测试需要同样的 SDK/JDK 环境；Excel 测试数据为明确虚构姓名，输出只在被忽略的 `build/` 目录。检查覆盖 XLSX 中文和扩展 Unicode、特殊字符、同名条目、状态与汇总、公式字符作为文本，以及 SQLite 草稿重新打开、回滚、历史快照和级联删除。SQLite 检查是 SQL 级测试，不替代 Android 生命周期和 UI 测试。

公开源码准备阶段完成原生构建、签名与空名单资产检查，以及上述自动化测试。未在模拟器/真机运行公开空名单版本，未验证系统文件选择器和 Excel/WPS 的实际显示。修改版本后应按用户设备进行实测，不声称未执行的检查已经通过。

## 许可证

本项目使用 MIT 许可证，版权署名 Asterracy，允许复用和修改，需保留版权与许可文本（见 `LICENSE`）。[第三方工具和许可说明](THIRD_PARTY.md) 说明单独安装的 Android SDK/JDK 等；这些组件不包含在项目许可证内。源代码许可不包含用户私人名单或考勤数据的公开授权。
