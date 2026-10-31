# Folia Android App（TWA）

把 https://music.dreamobserver.top 打包成 Android App。基于 Google 的 androidbrowserhelper（TWA），
走系统 Chrome 内核，全屏、无地址栏、支持后台播放与通知栏控制。

## 结构

```
app/                     Android 应用模块（LauncherActivity + 图标 + 清单）
.github/workflows/       云端构建（GitHub Actions）
gradlew / gradle/        Gradle wrapper（8.9）
```

## 关键参数

| 项 | 值 |
| --- | --- |
| applicationId | top.dreamobserver.folia |
| 站点 | https://music.dreamobserver.top/ |
| minSdk / targetSdk | 21 / 34 |
| 签名 | 由 CI 从 secret 还原 android.keystore，别名 folia |

## 构建

仓库 → Actions → build-folia-apk → Run workflow，约 3–5 分钟，产物在 artifact `folia-apk`。

需要的 secrets（已配置）：

| Secret | 说明 |
| --- | --- |
| KEYSTORE_BASE64 | android.keystore 的 base64 |
| KEYSTORE_PASSWORD | keystore 密码 |
| KEY_PASSWORD | key 密码 |

## 全屏条件

域名下必须有 https://music.dreamobserver.top/.well-known/assetlinks.json，
且 package_name 与签名指纹匹配（已配置）。不匹配时退回带地址栏的 Custom Tab。

## 升级

改 app/build.gradle 里的 versionCode / versionName，重新跑 workflow。
versionCode 必须递增，否则无法覆盖安装。
