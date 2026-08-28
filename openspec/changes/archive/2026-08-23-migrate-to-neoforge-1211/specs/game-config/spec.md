## ADDED Requirements

### Requirement: 统一配置文件
模组 SHALL 提供单一 ModConfigSpec 配置文件（COMMON 类型），包含 boss、danmaku、effects、spellcards 四个分节；游戏代码中的可调数值（Boss 属性、弹幕伤害/速度/间隔、效果倍率系数、符卡时长/半径/冷却等）MUST 从配置读取，禁止硬编码魔法数字。

#### Scenario: 配置节齐全
- **WHEN** 首次启动生成 `config/gensokyou-common.toml`
- **THEN** 文件中存在 boss/danmaku/effects/spellcards 四节且各条目带默认值与注释

### Requirement: 默认值溯源
除设计文档明确修正的项（如 Boss 移速归一到合理量级）外，配置默认值 SHALL 与旧 1.12.2 代码中的硬编码值一致（如 Boss 500 血/60 攻/20 护甲、八向弹幕伤害 10、无想封印 10 秒等）。

#### Scenario: 关键默认值核对
- **WHEN** 审阅生成的默认配置
- **THEN** boss.maxHealth=500、boss.attackDamage=60、spellcards.musouFuuinDuration=10s 等关键值与旧实现一致

### Requirement: 配置变更生效
修改配置后 SHALL 在下次世界加载/服务器重启时对新创建的对象与新触发的逻辑生效，无需修改代码。

#### Scenario: 调整弹幕伤害
- **WHEN** 将 danmaku.baseDamage 改为 20 后重启世界
- **THEN** 新发射的弹幕命中造成 20 点基础伤害
