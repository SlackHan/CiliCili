# CiliCili 本地构建与 MuMu 模拟器部署规则

## 目标测试设备（MuMu 模拟器 `#0` 安卓机）

- **ADB 路径**：优先使用 PATH 中的 `adb`（`D:\Android\Sdk\platform-tools\adb.exe`，与 `local.properties` 中 `sdk.dir` 一致）。
- **设备串口**：`127.0.0.1:16384`（MuMu 模拟器 `#0` 实例）。
- 若 `adb devices` 未列出 `127.0.0.1:16384`，先执行 `adb connect 127.0.0.1:16384` 再继续。

## 标准构建、安装与拉起命令

当用户要求“构建 / 安装 / 部署到 MuMu `#0`”或修改代码后需要上机验证时，直接使用以下命令，无需重新探测包名、Activity 或端口：

1. **单测与构建 Debug APK**（工作目录 `D:\lanhanCODE\projects\CiliCili`）：
   ```powershell
   .\gradlew.bat testDebugUnitTest assembleDebug
   ```
2. **覆盖安装 Debug APK**：
   ```powershell
   adb -s 127.0.0.1:16384 install -r "D:\lanhanCODE\projects\CiliCili\app\build\outputs\apk\debug\app-debug.apk"
   ```
3. **冷重启并拉起主界面**（包名 `com.lanhan.cilicili`，入口 `.MainActivity`）：
   ```powershell
   adb -s 127.0.0.1:16384 shell am start -S -n com.lanhan.cilicili/.MainActivity
   ```

## 约束

- 始终通过 `-s 127.0.0.1:16384` 显式指定目标设备，避免多设备在线时误装到其他模拟器或真机。
- 若仅需验证已构建好的 APK，可直接执行第 2、3 步；若改动了源码，先执行第 1 步。
