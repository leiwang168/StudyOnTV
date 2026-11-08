# 个人版内置后台恢复

`0.3.21-local.2` 参考 Aerial 的通知监听重绑定机制：系统重新绑定 `RevivalListener` 后，只恢复本应用的无障碍服务，再由无障碍启动已有的 `GateService`。恢复尝试有次数限制；不读取通知、不创建同步账户、不维持无限轮询。借鉴来源和 MIT 许可保存在 APK 的 `assets/notices/aerial-tv.txt`。

应用需已完成设置、无障碍服务至少连接过一次，并由用户开启“后台自动恢复”的通知访问权限。还需用 ADB 授予 `WRITE_SECURE_SETTINGS`。不满足条件时不会自行修改无障碍列表。用户关闭本应用的通知访问即可停止自动恢复。恢复时保留 Aerial、小爱等其他服务；重绑定的第二次写入重新读取设置，以保留期间其他应用的修改。

```text
adb shell pm grant com.lion.studyontv android.permission.WRITE_SECURE_SETTINGS
adb shell cmd notification allow_listener com.lion.studyontv/com.studyontv.tv.RevivalListener
adb shell dumpsys activity service com.lion.studyontv/com.studyontv.tv.RevivalListener
```

按 `applicationId` 生成服务组件，代码 namespace 保持 `com.studyontv.tv`。冷启动尚未解锁时只等待，不访问管理密码、题库或配置；未绑定服务会在解锁、亮屏和通知监听连接时重试。若无障碍设置已有条目但服务未绑定，移除自己的条目后等待 1.5 秒，再读取最新列表并补回；重绑定中的标记持久化，异常中断后可以补回。

这是针对电视固件行为的恢复机制，不保证所有系统均会在强停后重新绑定通知监听。Aerial 为低内存设备另有同步适配器兜底，本实现暂未引入。旧独立辅助程序保留为回退方案，验证内置路径时必须停用它，避免误判。完整断电开机与待机唤醒需在目标电视单独验收。

## 验证

用 JDK 编译并运行 `recovery-tests/AccessibilityEntriesTest.java` 和主源码中的 `AccessibilityEntries.java`，验证个人包与官方包区分、短组件名、重复条目和并发设置修改的保留。

设备验收需记录强停前后 PID，在旧辅助程序停用时强停个人版；不得手动启动个人版。检查内置 `RevivalListener` 的 `dumpsys` 输出应为 `accessibility=true gateService=true`，`/api/hello` 应恢复可访问且题数不变，再验证主页、待机唤醒、完整重启。

### 2026-11-08 真机记录

- MiTV MFTR0 / Android 14，个人版 `com.lion.studyontv`，versionCode 28；覆盖安装成功，沿用原签名。
- 旧辅助程序 `com.studyontv.recovery` 已关闭配置、撤销通知监听并停用；测试中无辅助程序进程。
- `am force-stop com.lion.studyontv` 前 PID 5734，强停后立即无进程，12 秒后检查 PID 17372；未手动启动应用。内置监听显示 `connected=true accessibility=true gateService=true pending=false`。
- 仅移除个人版无障碍条目，8 秒后自动补回并绑定；Aerial 和小爱条目原样保留。
- 上述恢复后 `/api/hello` 返回 `0.3.21-local.2`、170 道题，管理密码仍为必需。
- 按主页键后管理接口仍在线，Aerial 进程及其通知监听仍运行；该次焦点仍在视频应用，未据此认定 Aerial 主页切换通过。
- Java 设置列表测试 9 项通过，`assembleTvRelease` 和 APK 签名验证通过，APK 含 Aerial MIT 许可。
- 待机唤醒、完整重启尚待单独实测，不能以强停结果代替。
