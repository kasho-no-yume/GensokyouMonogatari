# 验证记录

## 环境与命令

- OpenSpec 2.2.0 与 Node 22.23.5 安装在 `/home/node/.local/share/gensokyou-tools/`。`bash tools/openspec_task.sh --version` 返回 `2.2.0`，change 严格校验通过。
- 当前 Linux 容器内存上限为 1,677,721,600 bytes。默认 Minecraft 全量反编译失败；限制到 768 MB 后日志明确出现 `OutOfMemoryError: Java heap space`。
- 使用 NeoForge ModDev 的 `disableRecompilation` 二进制开发依赖模式，并关闭源码依赖产出，已通过 `compileJava` 和 `compileTestJava`。此设置仅在被忽略的 `build/agent-logs/resident-build.init.gradle`，未改变仓库构建配置。

```bash
_JAVA_OPTIONS='-Xmx512m -XX:ActiveProcessorCount=2' \
  bash tools/gradle_task.sh test \
  --tests 'com.bitsson.gensokyou.client.resident.*' \
  --no-daemon --max-workers=1 \
  --init-script build/agent-logs/resident-build.init.gradle
```

## 居民回归

专项命令成功完成：14 项测试，0 失败、0 错误、0 跳过。

- `ResidentDataTest`：5 项，覆盖外观确定性/组合、资源重载身份、非法/越界输入、数量与距离预算、内置场景。
- `ResidentGroundTest`：3 项，覆盖普通地面/半砖、正式活动拒绝无保护地面、水/低顶/坑洞/卸载以及墙和半格台阶。
- `ResidentMotionTest`：3 项，覆盖拐角停留、往返、阻挡后不积攒路线时间、支持丢失与帧插值。
- `ResidentModelTest`：3 项，实际调用模型输出顶点，检查有限坐标/UV、分部件颜色与环境光参数、300 个外观共用模型不泄露姿态/显隐，以及远处细节减少。

## 语言审计

运行了 `python3 tools/lang_audit.py`。现有全库审计失败项为 4 个英文有/中文无的引导书键和 32 个中文缺失的造化配方名；逐项对比 `git show HEAD:<lang>` 后确认本次没有新增缺失。居民新增 4 条反馈均有中英文值，既有翻译内容未修改。

## 图形验收边界

本容器没有 `DISPLAY`、Wayland 或 Xvfb，未运行图形客户端。因此不宣称已实测游戏内美术、日夜光照、墙体遮挡、资源重载、联机本地性或实际帧耗。可执行入口和检查步骤见 `docs/client-village-residents.md`；创造模式 `/gs_residents preview 24` 可在不生成建筑的情况下开始验收。
