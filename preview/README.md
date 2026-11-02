# 符卡预览站

把**真实 runtime 算出来的客户端渲染投影**导成一个静态站，用来在不开游戏的情况下看符卡的整体表现。

```
Java 跑一遍卡（真 runtime + 真 wire + 真 replica）
        ↓  逐 tick 读 renderView
preview/data/<owner>_<index>.json   ← 入库基准
        ↓
preview/  静态站（three.js，零构建）
```

## 它是什么，不是什么

- **是**：同一份客户端渲染投影。位置、朝向、明暗、粗细、隐藏态、预警、激光长度，
  全部来自 `ProgramRenderView.Snapshot`，前端只画字段，不重算任何语义。
- **不是**躲避训练器：没有碰撞、没有命中、没有擦弹。
- **不是**难度依据：BOSS 与目标都是**静止**的，`aimed` 弹会全部收束到木桩一点，
  看起来比实机更密。判断难度请回游戏实测。
- **不是**观感验收替代：bloom/shader/实体光照不在这里。

## 用

```powershell
# 1. 导出（19 张正式卡约 1 分钟；单卡重导约 10 秒）
.\tools\gradle_task.ps1 exportDanmakuPreview

# 只重导一张
.\tools\gradle_task.ps1 exportDanmakuPreview --project-prop preview.only=big_fairy:3

# 2. 起站（仓库根为根目录，贴图直接读 assets，不复制）
python tools\danmaku_preview.py
# → http://127.0.0.1:8787/preview/index.html
```

操作：鼠标拖拽转视角，`WASD` 平移，`Q/E` 升降，`Shift` 加速，滚轮前后推拉；
`1/2/3/4` 切换预设机位（玩家眼位 / 正俯视 / 侧视 / BOSS 后方）；空格播放暂停，方向键单步。

底部读数：`存活 N · 峰值 M`。N 是当前 tick 真正画出来的弹数，M 是全卡峰值；
时间轴底色是逐 tick 存活曲线，粉色竖线是播放头——一眼能看出哪一段最密。
两个数都由成员的首末采样算出（那就是它的存活区间），不经过任何前端推算。

## 改了卡之后

**必须重导，否则基准是旧的。** 拦截用：

```powershell
.\tools\gradle_task.ps1 checkDanmakuPreview
```

它只比对不写盘；内容与 `preview/data/` 不一致就以非零退出码报告是哪几张卡。
建议和 `lang_audit.py` 等审计一起跑。

## 新增一张卡

不需要登记任何东西：覆盖面由 `BossProgramCards` 决定，导出器读注册表。
把卡加进正式牌组 → 跑一次 `exportDanmakuPreview` → 多出来的 `<owner>_<index>.json`
和 `index.json` 一起提交即可。

## 基准体积（2026-11-02 实测）

| | |
|---|---|
| 卡数 | 19（大妖精 3 / 山女 4 / 蓝 8 / 露米娅 4） |
| 采样 | stride 2 tick（出生 tick 必采），折线简化后共 367,193 个采样 |
| 文本体积 | **17.86 MB**（最大单卡 `yamame_3` 6.35 MB） |
| 导出耗时 | 全量约 62 秒（一次性 `testClasses` 之后） |

省体积靠**折线简化**而不是降采样：程序弹大多直线飞行，一条直线只留两个端点；
简化后仍满足 0.05 格 / 0.02 明暗 / 0.02 朝向的误差界，导出时会逐点自检，超界直接失败。

## 数据形状

```json
{
  "card": "spellcard.gensokyou.big_fairy.1",
  "ticks": 882, "stride": 2, "cycleTicks": 240,
  "scene": { "boss": [0,80,0], "stub": [0,64,10], "groundTop": 64, "fov": 70, "ticksPerSecond": 20 },
  "renderer": { "bullet": "...", "laser": "...", "cap": "...", "circle": "...", "warning": "..." },
  "styles": [ { "mesh": "quad|star", "tint": "#RRGGBB", "radius": 0.22, "scale": 1, "texture": "..." } ],
  "members": [ { "g":3,"b":0,"i":7,"born":41,"life":120,"style":0,"laser":false,
                 "s": [[tick,x,y,z,alpha,brightness,size], ...] } ],
  "warnings": [ { "src":[...], "dir":[...], "from":61, "until":81, "laser":false } ],
  "billboards": [], "guides": [],
  "events": [ { "t":20, "kind":"REJECT", "reason":"clearance", "g":4 } ]
}
```

激光成员的每个采样多三个数（`beamLength, dirX, dirY, dirZ`），因为激光会旋转。

## 加进来之前它是一张白纸

导出器在 `src/test/java/com/bitsson/gensokyou/danmaku/program/preview/PreviewExport.java`，
只跑在 test classpath，**不进 mod jar**（`verifyNoTestContent` 也会拦）。
它消费的正是客户端那一份投影，所以导出能跑通本身就说明网络/投影链路没断。
