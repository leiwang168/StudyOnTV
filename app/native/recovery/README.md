# 个人版电视后台恢复

本目录从此前本地辅助程序的编译产物恢复，保留通知监听、亮屏恢复、无障碍条目监测和有限次数重绑定逻辑。辅助包名仍为 `com.studyontv.recovery`，目标为 `com.lion.studyontv/com.studyontv.tv.WatchAccessibilityService`。与主应用分开运行，避免主应用被强停时恢复逻辑一同退出。

2026-11-08 在 MiTV MFTR0（Android 14）上观察到系统强停个人版并移除无障碍条目。安装本辅助程序后，切回主页及显式强停后的后台恢复测试通过，`/api/hello` 返回 170 道题。此次尚未验证完整重启或待机唤醒；不能保证其他固件下的恢复行为。

## 构建

需要 JDK 17 或以上、Android SDK 平台和 build-tools，以及原辅助程序的本机调试签名密钥。主应用使用独立的正式签名，勿与此处混用。密钥及个人数据均不应提交。

```powershell
.\app\native\recovery\build.ps1 -SdkRoot "$env:LOCALAPPDATA\StudyOnTVAndroidSdk" -JavaHome 'C:\Program Files\JetBrains\IntelliJ IDEA 2024.3.6\jbr'
```

产物在被 Git 忽略的 `release-artifacts/recovery-personal` 中。

## 安装和启用

```text
adb install -r release-artifacts/recovery-personal/StudyOnTV-Recovery-personal.apk
adb shell pm enable com.studyontv.recovery
adb shell pm grant com.studyontv.recovery android.permission.WRITE_SECURE_SETTINGS
adb shell am broadcast -n com.studyontv.recovery/.ConfigReceiver --ez enabled true --ez keep_adb true
adb shell cmd notification allow_listener com.studyontv.recovery/.RecoveryListener
adb shell dumpsys activity service com.studyontv.recovery/.RecoveryListener
```

`keep_adb` 是明确可选的配置；本电视用户已要求保持 ADB 常开。通知监听用于让系统绑定恢复服务，程序未实现通知内容处理。配置接收器要求发送方有 `DUMP` 权限。恢复无障碍时保留其他应用的条目。

## 停用

```text
adb shell am broadcast -n com.studyontv.recovery/.ConfigReceiver --ez enabled false --ez keep_adb false
adb shell cmd notification disallow_listener com.studyontv.recovery/.RecoveryListener
adb shell pm disable-user --user 0 com.studyontv.recovery
```

停用不会停用个人版自身的无障碍服务，也不会主动关闭 ADB。
