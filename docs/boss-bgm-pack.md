# BOSS 战 BGM 资源包制作说明

本模组的 BOSS 战 BGM 默认由 5 首内置曲目按 BOSS 阶级播放。你**不需要**安装任何东西
就能听到；如果你想换成自己的音乐，用**普通 Minecraft 资源包**即可——模组没有自定义
加载器，也不认自己的压缩包格式。

> 一句话：**覆盖同名 `.ogg` 换整阶；`sounds.json` 加 `"replace": true` 换单只。**

---

## 1. 事件 id 表

| 事件 id | 谁在用 | 默认音频 |
|---|---|---|
| `gensokyou:bgm/tier1` | 1 阶 BOSS（名册外回落） | `bgm/tier1.ogg` |
| `gensokyou:bgm/tier2` | 2 阶 | `bgm/tier2.ogg` |
| `gensokyou:bgm/tier3` | 3 阶 | `bgm/tier3.ogg` |
| `gensokyou:bgm/tier4` | 4 阶 | `bgm/tier4.ogg` |
| `gensokyou:bgm/tier5` | 5 阶 | `bgm/tier5.ogg` |
| `gensokyou:bgm/boss/big_fairy` | 大妖精 | `bgm/tier1.ogg` |
| `gensokyou:bgm/boss/rumia` | 露米娅 | `bgm/tier1.ogg` |
| `gensokyou:bgm/boss/yamame` | 黑谷山女 | `bgm/tier1.ogg` |
| `gensokyou:bgm/boss/yakumo_ran` | 八云蓝 | `bgm/tier2.ogg` |
| `gensokyou:bgm/boss/flandre` | 芙兰朵露 | `bgm/tier5.ogg` |
| `gensokyou:bgm/boss/fake_flandre` | 伪芙兰 | `bgm/tier5.ogg` |

多个事件指向同一个音频文件，所以**换掉一个阶文件会同时换掉该阶所有 BOSS**，
而换一只 BOSS 的事件不会影响别人。

---

## 2. 路径 A：换掉整个阶级（最省事）

把你的音频放进资源包的同名路径：

```
MyBgmPack/
├── pack.mcmeta
└── assets/
    └── gensokyou/
        └── sounds/
            └── bgm/
                ├── tier1.ogg
                └── tier3.ogg          ← 只放你想换的那几阶
```

`pack.mcmeta`：

```json
{
  "pack": {
    "pack_format": 34,
    "description": "My Boss BGM"
  }
}
```

**就这样，不需要写 `sounds.json`。** 同名文件会遮蔽内置文件（资源包栈顶优先），
所有引用该阶的 BOSS 都会改用你的音乐。

> `pack_format` 对 1.21.1 是 **34**；数字不对只会显示一个警告，包仍然生效。

---

## 3. 路径 B：只换一只 BOSS

在资源包里放一个 `assets/gensokyou/sounds.json`，把那只 BOSS 的事件**整体替换**：

```json
{
  "bgm/boss/flandre": {
    "replace": true,
    "sounds": [
      { "name": "mypack:bgm/flandre", "stream": true }
    ]
  }
}
```

以及音频文件 `assets/mypack/sounds/bgm/flandre.ogg`（命名空间可以随便取，只要和
`name` 里的前缀一致）。

### ⚠️ 最容易踩的坑：`"replace": true` 不是可选的

Minecraft 合并 `sounds.json` 时，**不带 `"replace": true` 的条目是「追加」而不是「替换」**：
原曲和你的曲子会同时挂在这条事件上，游戏每次随机挑一个。它不会报错，也不会警告，
表现为「有时是原曲，有时是我的」——这是本仓库实测确认的行为，不是玄学。

所以：

| 你想要 | 必须写 |
|---|---|
| 整个事件换成我的文件 | `"replace": true` |
| 在原曲基础上再加一个随机变体 | 省略（一般不是你想要的） |

---

## 4. 音频要求

| 项 | 要求 | 原因 |
|---|---|---|
| 容器/编码 | **Ogg Vorbis**（`.ogg`） | Minecraft 只认这个 |
| 采样率 | 44.1 kHz | 与内置曲目一致，避免重采样差异 |
| 声道 | 立体声或单声道均可 | 非定位播放，不强制单声道 |
| `stream` | **必须 `true`** | 否则整首会被解码进静态声道池，占内存且可能被挤掉 |
| **循环** | **整首首尾可衔接** | MC 只会"播完从头再来"，**没有** LOOPSTART/LOOPEND，不能跳前奏/尾奏 |
| 首尾 | 不要静音开头、不要淡出结尾 | 否则每轮循环会插一段空白或渐弱 |
| 响度 | 与内置曲目接近（约 −16 LUFS） | 换曲后音量突然变大/变小会很突兀 |

**关于循环**：如果你的曲子有 4 秒前奏、结尾还有淡出，直接放进游戏的效果是
「前奏 → 主段 → 淡出 → 前奏 → …」。这不能靠资源包解决，只能由你或作者提供
一个**循环编辑版**（去掉前奏/淡出，让首尾和声能接上）。

---

## 5. 排查：装好了但没声音

按顺序检查这四件事（覆盖了绝大多数情况）：

1. **路径/大小写**：必须是 `assets/gensokyou/sounds/bgm/tierN.ogg`（全小写）。
   路径错一个字母就会静默失效。
2. **不是 Ogg Vorbis**：改扩展名不算。用 `ffprobe` 看 `codec_name` 是否为 `vorbis`。
3. **`"replace": true` 漏写**（路径 B）：表现为"有时能听到我的"。
4. **音量滑条**：BGM 走 `MUSIC` 声源，受原版「音乐」滑条控制——把它调到 0
   就什么也听不到，音效不受影响。

仍然没声音时看日志（`logs/latest.log`）里的 `[audio/bgm]`：
事件未注册、文件缺失、`Maximum sound pool size reached` 都会留在那里。

---

## 6. 我不会碰的东西

- 资源包不会改变 BOSS 的阶级，也不会改变"哪只 BOSS 用哪条事件"——那由模组的
  `BossBgms` 名册决定。
- 资源包不能新增 BOSS、不能改判定、不能影响战斗数值。
