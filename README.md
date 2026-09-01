# 谷歌框架 Caju

面向 **缺 GMS / GMS 残缺** 的 Android 真机：一键检测本机 Google 服务，按方案补装，异常组件可卸载重装。

**可爱桔（Caju）** 出品 · 免费 · 无广告 · 不打包、不破解 Google 安装包

[下载最新 APK](https://github.com/Cute-Satsuma/gms_installer/releases/latest/download/gms-installer-android.apk) · [Releases](https://github.com/Cute-Satsuma/gms_installer/releases) · [Cute-Satsuma](https://github.com/Cute-Satsuma)

---

## 适合谁用

- 海外版 ROM、刷机后、或厂商裁掉了 Google 服务的手机
- 装过框架但登录不了、商店打不开、服务被停用的机子
- 想按 **正规未修改** 组件侧载，而不是找来路不明「一键包」的人

> 华为 / 荣耀等机型可能在系统层拦截 GMS。即使用本应用装上组件，仍可能无法稳定登录——这是系统策略，不是本应用能绕过的。

---

## 能做什么

| 能力 | 说明 |
|---|---|
| **本机检测** | 识别服务框架、Play 服务、Play 商店等是否已装、是否系统预装 |
| **健康检查** | 发现停用、挂起、认证器失效、商店无法启动等异常 |
| **必要方案** | 只列出还缺或需要修复的项；健康预装自动跳过，减少冲突 |
| **直链安装** | 应用内按方案下载并唤起系统安装（非改包、非破解） |
| **卸载重装** | 对可卸载的异常组件：先卸再装；系统纯预装卸不掉则引导清数据 / 启用 |
| **导入 APK** | 也可自行从可信来源取包，导入后按推荐顺序安装 |

核心四件套优先保障：

1. Google 服务框架（GSF）  
2. Google Play 服务  
3. Google Play 商店  
4. 账号登录能力（现代机通常由 Play 服务提供，不必强行侧载旧版账号管理）

---

## 为什么选 Caju

- **正规路径**：不内置、不镜像 Google 应用本体；安装包来自你有权使用的公开来源（如 APKPure 直链 / 镜像页）。
- **少踩坑**：系统已预装的框架会跳过，避免三星等机型上的权限 / 签名冲突。
- **能修才修**：装了但起不来的，标成「修复」而不是假装一切正常。
- **品牌一致**：与 [分贝仪 Caju](https://github.com/Cute-Satsuma/decibel_meter)、[数独 Caju](https://github.com/Cute-Satsuma/game_sudoku) 等同一工作室 **可爱桔 / Caju**。

---

## 快速开始

1. 下载 [最新 APK](https://github.com/Cute-Satsuma/gms_installer/releases/latest/download/gms-installer-android.apk) 并安装  
2. 允许本应用「安装未知应用」  
3. 打开后查看总览方案 → **按方案处理** / 单项安装或修复  
4. 全部成功后 **重启手机**，再添加 Google 账号并用 Play 商店补齐其它应用  

指引页里还有小米、华为、OPPO、vivo、三星等厂商的注意点。

---

## 下载

| | |
|---|---|
| 稳定直链 | https://github.com/Cute-Satsuma/gms_installer/releases/latest/download/gms-installer-android.apk |
| 版本页 | https://github.com/Cute-Satsuma/gms_installer/releases |
| 包名 | `com.bytemyth.gms_installer` |

当前 GitHub 包为侧载签名，便于安装测试；上架应用商店前会换正式上传密钥。

---

## 说明与边界

- Google 应用受版权保护。**Caju 不会在仓库或安装包里附带 Google APK。**
- 请使用与本机 Android 版本、CPU 架构匹配的组件；乱装极易失败。
- 无法保证所有厂商 ROM 都能完整恢复 GMS；拦截、签名冲突、权限重复属于系统限制。
- 本应用不提供破解、改签名、仿站或绕过厂商安全策略的方法。

---

## 开发

```bash
# 需要 JDK 17
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

---

## English

**GMS Framework Caju** helps Android devices that are missing or broken Google Mobile Services: detect what’s installed, check health, install what’s still needed, and repair broken components by uninstalling and reinstalling when possible.

- Free, no ads  
- Does **not** ship or crack Google APKs — you install unmodified packages from sources you’re allowed to use  
- Latest APK: [gms-installer-android.apk](https://github.com/Cute-Satsuma/gms_installer/releases/latest/download/gms-installer-android.apk)

Made by **Cute-Satsuma (Caju)**.

---

## License

源码许可见仓库内声明（若暂无 LICENSE，默认保留所有权利，欢迎提 Issue / PR）。使用本工具安装的第三方 Google 组件，版权归各自权利人所有。
