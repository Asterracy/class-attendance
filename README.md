# 班级考勤 / Class Attendance

一个轻量、离线、手机竖屏使用的 Android 考勤工具。Android 6.0（API 23）及以上，无账号、网络权限、后台服务或第三方运行库。

有名单时，首页显示学号、姓名和“开始新考勤”。填写活动名称后开始；取消不创建草稿，空白名称会提示。每人可一键选择已到、未到或请假，三态互斥；草稿即时保存在本机，退出后可继续。正式保存生成独立记录，不覆盖历史；可查看历史并导出真正的 Excel `.xlsx`，包含活动名称、逐人学号（文本）、姓名、三态状态、记录时间和人数汇总。状态仅表示本次手动选择结果。删除、清空、替换名单和取消正在进行的考勤均需明确确认。

**公开源码默认不含任何真实名单。** 默认构建会生成空的名单资产；可在应用中编辑名单，或由 Agent 按以下流程为用户制作首次打开就有学号和姓名的私人 APK。名单编辑是备用入口，定制交付不应要求用户临时手动录入。

## Agent 为用户预置自己的名单

1. **确认授权与范围。** 只读取用户明确提供的名单文件、截图或文本，确认所选行属于该班。不要补入被用户截掉的其他班学生。不要查看无关文件，也不要将原图或名单发送给第三方 OCR 服务。
2. **只提取授权的学号和姓名。** 学号保留为原始字符串，不推测连续号码。排除性别、学校、宿舍、房间、床位、电话等无关字段。图片必须实际查看像素，不能凭摘要猜姓名。模糊字逐字询问用户，保留确实存在的同名条目，并与用户核对总人数及边界。
3. **制作本地私人名单。** 在自己的工作副本中新建 `assets/roster.local.txt`，UTF-8、无 BOM，每行 `学号<TAB>姓名`，不含标题、序号、空行或其他信息。它受 `.gitignore` 保护，不能提交。无学号的旧 `assets/names.txt` 仍支持，但不能冒充完整定制名单。`assets/names.example.txt` 和 `assets/roster.example.txt` 仅含虚构示例，可用于练习，构建不会自动加载示例。公开仓库不跟踪私人名单。
4. **验证再构建。** 运行 `python3 scripts/validate_roster.py assets/roster.local.txt`；旧名单存在时也运行 `python3 scripts/validate_names.py assets/names.txt`。核对人数、重复姓名和学号，避免在日志中输出真实名单。构建脚本只复制验证过的私人文件，公开默认资产为空。完整私人交付设置 `ATTENDANCE_REQUIRE_COMPLETE_ROSTER=1`，防止漏装名单。按下一节自建签名并运行 `./build.sh`。
5. **验证实际 APK。** 用本地 ZIP 工具或脚本读取 APK 内 `assets/roster.txt` 和 `assets/names.txt`，与本地名单逐字比较、核对人数；用 `apksigner verify --verbose` 验签。在可用的安卓设备或模拟器上确认首次打开直接显示学号和姓名，填写活动名称、弹窗取消/空白提示、三态互斥、草稿重开、保存历史及 Excel 字段与人数均正确；旧版更新还需验证覆盖安装和旧记录查看/导出。没有设备时如实注明未实测。
6. **私人交付。** 含真实名单的 APK 仅交付该用户，不上传到本公开仓库、Release、Issue、CI artifact 或公开网站。交付内容包括 APK、人数、SHA256、签名/版本和测试范围。不要自动安装到用户手机或改动设备安全设置。

名单在 SQLite 数据库首次创建时优先从完整学号资产加载。仅姓名的旧资产仍只用于初次创建。完整学号资产在其指纹首次出现或改变时，会在单个事务内替换当前名单；此名单用于以后的新考勤，不改动历史快照。重复打开不会重新覆盖用户随后编辑的名单。已有草稿保留原人数、姓名和状态，仅补充能安全匹配的空白学号；同名条目只有整个草稿顺序一致时才按位置匹配，否则保持空白。减少当前班级人数不会删去旧草稿或历史中的人。

数据库版本 1 升级到 2 时保留原表、记录编号和时间，旧已到/未到状态原样迁移。历史中的学号留空，界面显示“学号未提供”；无活动名称显示“未命名活动（旧记录）”。Excel 中学号仍留空，并附兼容说明，不用新名单改写历史。升级失败不会自动重置数据库。更新使用相同包名、原私人签名和递增版本号；无需卸载。卸载或系统清除应用数据会删除本机记录，应先导出需要保留的历史。

## 私人学号名单格式

`assets/roster.example.txt` 是明确虚构的格式示例，每行两列，用一个制表符分隔。学号当作字符串保存，保留前导零和长数字。私人导入示例：

```sh
# 将用户核验后的两列名单保存为 assets/roster.local.txt；不提交此文件。
python3 scripts/validate_roster.py assets/roster.local.txt
ATTENDANCE_REQUIRE_COMPLETE_ROSTER=1 ./build.sh
```

不要照抄示例作为用户名单，也不要从未授权来源补全缺少的学号。名单编辑入口支持姓名修改，能安全匹配的学号会保留；新增或无法确定的学号留空。为用户制作完整私人包时，由 Agent 在本地完成核验、预置和构建。

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

可用 `ATTENDANCE_KEYSTORE` 和 `ATTENDANCE_KEY_ALIAS` 指定已有私人密钥。自动化构建可通过受保护的环境变量 `ATTENDANCE_STORE_PASSWORD`、`ATTENDANCE_KEY_PASSWORD` 提供口令；不要将它们写入命令日志、脚本、`.env` 的提交版本或公开 CI。只检查编译时，可运行 `ATTENDANCE_COMPILE_ONLY=1 ./build.sh`，只产生中间文件，不生成最终安装包。`ATTENDANCE_UNSIGNED_ONLY=1 ./build.sh` 可生成不能安装的未签名验证包。

此脚本面向 macOS/Linux Bash 环境。Windows 用户可自行移植或使用合适的构建环境；未提供桌面界面适配。

## 防止私人名单被误提交

首次使用本仓库先启用本地检查：

```sh
git config core.hooksPath .githooks
```

`.gitignore` 排除私人 `assets/names.txt`、`assets/roster.local.txt`、生成的 `assets/roster.txt`、签名目录、截图、数据库、考勤导出、SDK、构建产物与 APK。**忽略规则不保护已被跟踪的文件。** 因此公开仓库使用未跟踪的本地名单和独立的合成示例；不要对私人名单使用 `git add -f`。

提交或推送前检查 `git diff --cached` 并运行 `python3 scripts/check_public_commit.py`。钩子会拒绝强制加入的名单文件、常见私人数据与二进制产物、个人路径和内联签名口令。还需人工检查源码、测试、README、提交消息和历史，避免把姓名移到其他文本中；钩子可被绕过，不能保证任意文字无隐私信息。如果私人信息已经进入历史，应停止公开推送并在新的干净副本中重新准备，不仅是删除工作区文件。

## 测试与已知范围

源码附带合成数据的 JVM 导出测试及 Python 标准库 ZIP/XML/SQLite 检查：

```sh
./tests/run.sh
```

先执行一次构建。测试需要同样的 SDK/JDK 环境；Excel 测试数据为明确虚构姓名，输出只在被忽略的 `build/` 目录。检查覆盖 XLSX 活动名称、学号文本与前导零、三态与汇总、中文和扩展 Unicode、特殊字符、同名条目、公式字符安全、旧记录空字段；另检查活动名称空白校验、名单匹配，以及 SQLite 新建、v1→v2 迁移/回滚、全部九种状态切换、草稿重新打开、当前名单减少、历史快照和级联删除。弹窗取消/空白的接线有源码检查，仍须设备交互实测。SQLite 检查是 SQL 级测试，不替代 Android 生命周期和 UI 测试。

1.1.0 公开源码在不含私人资产的独立快照中完成原生未签名构建、空名单资产检查，以及上述自动化测试。签名密钥由使用者自行保管，仓库不提供签名 APK 或私人产物。未在模拟器/真机运行公开空名单版本，未验证系统文件选择器和 Excel/WPS 的实际显示。修改版本后应按用户设备进行实测，不声称未执行的检查已经通过。

## 许可证

本项目使用 MIT 许可证，版权署名 Asterracy，允许复用和修改，需保留版权与许可文本（见 `LICENSE`）。[第三方工具和许可说明](THIRD_PARTY.md) 说明单独安装的 Android SDK/JDK 等；这些组件不包含在项目许可证内。源代码许可不包含用户私人名单或考勤数据的公开授权。
