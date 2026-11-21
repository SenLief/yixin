# 一心 · Android

一心是一款离线待办与专注计时应用。支持 Android 8.0（API 26）及以上，应用 ID 为 `cn.yixin.todo`，当前版本为 `0.0.2-beta.1`。

- 左右滑动切换待办卡片，或打开日历，点日期翻看当天的任务。
- 用「新安排」添加任务，在卡片叠调整顺序，在「收好」查看完成记录。
- 从任务进入专注，支持正计时、倒计时、横竖屏和多种风格。暂停进度按任务保留。
- 任务、设置与专注进度存放在应用私有的 Room SQLite 数据库中，可以备份和恢复。

## 本地构建和验证

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。用 Android Studio 打开本目录，或设置好 `JAVA_HOME` 和 `ANDROID_HOME` 后运行以下命令。

```sh
./gradlew :core:jvmTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

数据库与界面测试需要独立的安卓模拟器或测试设备，测试会写入验证任务。

```sh
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

## GitHub 发布

将 `yixin-android/` 的内容作为 GitHub 仓库根目录推送，包含 `.github/` 和 Gradle Wrapper。向一个新建的空仓库推送时，先替换下列命令中的远程地址，再从本目录执行。

```sh
git init -b main
git add .
git commit -m "Initial Android import"
git remote add origin https://github.com/OWNER/REPO.git
git push -u origin main
```

[Android 工作流](.github/workflows/android.yml)在 Pull Request、`main` 分支推送和 `v*` 标签推送时运行核心测试、Lint 并构建 APK。版本标签会额外触发 APK 签名校验和发布。

已有发布密钥时，沿用同一密钥签署后续版本。首次发布可以用 JDK 的 `keytool` 创建一个发布密钥库。

```sh
keytool -genkeypair -keystore yixin-release.jks -storetype JKS \
  -alias yixin -keyalg RSA -keysize 4096 -validity 10000
```

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 添加以下 Secrets。密钥文件和密码保存在仓库之外。

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 密钥库文件的单行 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名，例如 `yixin` |
| `ANDROID_KEY_PASSWORD` | 密钥密码。与密钥库密码相同时可省略 |

可通过已登录的 GitHub CLI 从文件设置 Base64 Secret。

```sh
base64 < /path/to/yixin-release.jks | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64
```

每次发布，在 `app/build.gradle.kts` 更新 `versionName` 并递增 `versionCode`。提交并推送版本改动后，为同一提交添加标签。当前 Beta 对应的标签是 `v0.0.2-beta.1`。

```sh
git tag -a v0.0.2-beta.1 -m "Android 0.0.2 Beta 1"
git push origin v0.0.2-beta.1
```

Beta 标签会创建 GitHub Pre-release。签名 Secrets 全部为空时，Beta 发布 `yixin-android-0.0.2-beta.1-debug.apk`，使用 Actions 运行器的调试签名。配置签名 Secrets 后，Beta 和正式发布都使用你的发布密钥，APK 文件名末尾省略 `-debug`。

Android 覆盖安装要求应用 ID 和签名匹配。安装不同签名的 APK 前，先导出备份，再卸载旧包、安装新包并导入备份。计划持续覆盖安装多个 Beta 时，使用同一发布密钥签名。

## 备份与迁移

打开「设置 → 备份与迁移」。安卓导出为 `yixin-android` 第 1 版 JSON，包含任务和类别 ID、完成时间、手动顺序、洗牌种子、偏好和暂停的专注进度。备份为明文，可用系统文件选择器保存或分享至选定应用。

可导入网页端 `schemaVersion: 1` 的导出文件。应用先校验备份和预览冲突，再事务性写入。同 ID 的本机记录默认保留，也可选择使用备份中的记录。排序与偏好可单独采用备份版本，网页备份中缺少的安卓专属偏好和专注进度沿用本机已有值。

专注计时每 4 秒保存检查点，应用退到后台或锁屏时保存暂停进度，异常退出后可从最后一次成功保存的记录继续。应用关闭了系统自动备份与设备数据迁移，换设备时可手动导出并导入 JSON。

## 工程结构

- `app/` 提供 Jetpack Compose 界面、生命周期管理、文件选择和分享功能。
- `core/` 包含任务模型、排序、计时规则和备份映射。
- `data/` 实现 Room 仓储与事务。整个应用进程共用一个 Room 实例，写入事务提交后才更新界面状态。数据库基线位于 `data/schemas/`。
