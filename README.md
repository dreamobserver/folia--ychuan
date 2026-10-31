# Folia TWA · GitHub Actions 构建说明

把 https://music.dreamobserver.top 打包成 Android App（TWA，走系统 Chrome 内核）。

## 仓库里有什么

```
twa-manifest.json              Bubblewrap 打包配置（包名/图标/版本/签名路径）
.github/workflows/build.yml    云端构建工作流
.gitignore                     别把 keystore 提交上去
```

签名密钥不在仓库里，自己保存：

```
android.keystore     签名文件
别名                 folia
密码                 见 KEYSTORE_SECRET.txt
SHA-256             95:A5:81:4E:C2:C1:B1:55:A4:59:A3:EC:03:D4:78:6B:6D:FE:3D:42:2D:B6:03:FF:41:A7:AA:17:35:1A:3A:EB
```

## 一次性配置

1. GitHub 新建仓库（private 也行），把本目录的 twa-manifest.json、.github/、.gitignore 推上去
2. 仓库 → Settings → Secrets and variables → Actions，加三个 secret：

| Secret | 值 |
| --- | --- |
| KEYSTORE_BASE64 | main 分支下 android.keystore 的 base64（命令：base64 -w0 android.keystore） |
| KEYSTORE_PASSWORD | keystore 密码 |
| KEY_PASSWORD | key 密码（与上面相同） |

3. 仓库 → Actions → build-folia-twa → Run workflow

## 构建产物

跑完在该次运行页面底部下载 folia-twa artifact：

```
app-release-signed.apk    直接安装
assetlinks.json           和服务端那份一致（校验用）
```

## 全屏（去掉地址栏）的前提

https://music.dreamobserver.top/.well-known/assetlinks.json 必须存在，且 package_name 与
sha256_cert_fingerprints 和 APK 完全一致。已部署。

换签名密钥后要同步更新这个文件，否则会退回带地址栏的模式。

## 改版本

改 twa-manifest.json 里的 appVersionName / appVersionCode，重跑 workflow。
Android 要求 versionCode 递增，否则不能覆盖安装。
