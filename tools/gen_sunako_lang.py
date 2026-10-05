"""生成少名仪式所需的 lang 条目（zh_cn 优先 + en_us 同步）。

- GUI 键：手写（语义与原版用词对齐）。
- 药水自定义名已改为「基药水显示名 + II / III」后缀（`PotionTierTransform.basePotionName`），
  不再生成 `item.gensokyou.potion.t{2,3}.*` 键。

跑法：python tools/gen_sunako_lang.py
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZH = os.path.join(ROOT, 'src/main/resources/assets/gensokyou/lang/zh_cn.json')
EN = os.path.join(ROOT, 'src/main/resources/assets/gensokyou/lang/en_us.json')

# 原版 Potion 注册表条目 -> 中文名（缺省复用 en_us 的 Title Case 译法即可，
# 但中文需要人工词表，故此处只覆盖有专门译名的条目）。
ZH_NAMES = {
    'strength': '力量',
    'slow_falling': '缓降',
    'slowness': '缓慢',
    'leaping': '跳跃',
    'swiftness': '迅捷',
    'fire_resistance': '抗火',
    'water_breathing': '水下呼吸',
    'night_vision': '夜视',
    'invisibility': '隐身',
    'poison': '中毒',
    'regeneration': '生命恢复',
    'weakness': '虚弱',
    'healing': '治疗',
    'harming': '伤害',
    'turtle_master': '神龟',
    'wind_charged': '风充能',
    'weaving': '盘绕',
    'oozing': '渗浆',
    'infested': '寄生',
    'luck': '幸运',
}

# 有 LONG_ / STRONG_ 兄弟的原版条目（即 2/3 阶会真正变化的条目）
BREWABLE = [
    'strength', 'slow_falling', 'slowness', 'leaping', 'swiftness',
    'fire_resistance', 'water_breathing', 'night_vision', 'invisibility',
    'poison', 'regeneration', 'weakness', 'healing', 'harming', 'turtle_master',
    # 两兄弟皆无 → 升阶无效，但仍给出名字以免出现 "Uncraftable Potion"
    'wind_charged', 'weaving', 'oozing', 'infested', 'luck',
]

TIER_PREFIX = {2: '二阶', 3: '三阶'}


def potion_keys(tier):
    return {p: 'item.gensokyou.potion.t%d.%s' % (tier, p) for p in BREWABLE}


def build_entries():
    """返回 {lang: {key: value}}，只含需要新增的键。"""
    zh, en = {}, {}

    zh['item.gensokyou.sanzu_flask'] = '瓶装三途川水'
    en['item.gensokyou.sanzu_flask'] = 'Bottled Sanzu River Water'

    zh['gui.gensokyou.ritual.extra_slot'] = '放入物品'
    en['gui.gensokyou.ritual.extra_slot'] = 'Slot'

    zh['gui.gensokyou.ritual.sunako.start'] = '开始炼药'
    en['gui.gensokyou.ritual.sunako.start'] = 'Brew'

    zh['gui.gensokyou.ritual.sunako.reagent_slot'] = '炼药试剂'
    en['gui.gensokyou.ritual.sunako.reagent_slot'] = 'Reagent'

    zh['gui.gensokyou.ritual.sunako.pedestals'] = '三途川水 %s/%s'
    en['gui.gensokyou.ritual.sunako.pedestals'] = 'Water %s/%s'
    zh['gui.gensokyou.ritual.sunako.pedestals_tip'] = \
        '放了瓶装三途川水的祭品台：%s（共 %s 台）；放了别的东西：%s（完全忽略，不消耗）'
    en['gui.gensokyou.ritual.sunako.pedestals_tip'] = \
        'Pedestals with bottled water: %s (of %s); with something else: %s (ignored)'

    zh['gui.gensokyou.ritual.sunako.cache'] = '灵力 %s/%s'
    en['gui.gensokyou.ritual.sunako.cache'] = 'Spirit %s/%s'
    zh['gui.gensokyou.ritual.sunako.cache_tip'] = '缓存 %s / 上限 %s'
    en['gui.gensokyou.ritual.sunako.cache_tip'] = 'Stored %s / capacity %s'

    zh['gui.gensokyou.ritual.sunako.in_rate'] = '受灵 %s/s'
    en['gui.gensokyou.ritual.sunako.in_rate'] = 'Intake %s/s'

    zh['gui.gensokyou.ritual.sunako.cost'] = '单瓶 %s · 全台 %s'
    en['gui.gensokyou.ritual.sunako.cost'] = 'Each %s · full %s'
    zh['gui.gensokyou.ritual.sunako.cost_tip'] = \
        '单瓶耗灵 %s；台位全满时本批总耗灵 %s'
    en['gui.gensokyou.ritual.sunako.cost_tip'] = \
        'Spirit per bottle %s; total for every pedestal %s'

    zh['gui.gensokyou.ritual.sunako.ready'] = '可炼 %s/%s 瓶'
    en['gui.gensokyou.ritual.sunako.ready'] = 'Brewable %s/%s'
    zh['gui.gensokyou.ritual.sunako.no_power'] = '灵力不足一瓶'
    en['gui.gensokyou.ritual.sunako.no_power'] = 'Not enough spirit'
    zh['gui.gensokyou.ritual.sunako.no_water'] = '待酿·无三途川水'
    en['gui.gensokyou.ritual.sunako.no_water'] = 'Idle · no water'
    zh['gui.gensokyou.ritual.sunako.no_reagent'] = '待酿·试剂不可用'
    en['gui.gensokyou.ritual.sunako.no_reagent'] = 'Idle · unusable reagent'
    zh['gui.gensokyou.ritual.sunako.reagent_missing'] = '未放炼药试剂'
    en['gui.gensokyou.ritual.sunako.reagent_missing'] = 'No reagent'
    zh['gui.gensokyou.ritual.sunako.preview_tip'] = '%s'
    en['gui.gensokyou.ritual.sunako.preview_tip'] = '%s'

    en_names = {
        'strength': 'Strength', 'slow_falling': 'Slow Falling', 'slowness': 'Slowness',
        'leaping': 'Leaping', 'swiftness': 'Swiftness',
        'fire_resistance': 'Fire Resistance', 'water_breathing': 'Water Breathing',
        'night_vision': 'Night Vision', 'invisibility': 'Invisibility',
        'poison': 'Poison', 'regeneration': 'Regeneration', 'weakness': 'Weakness',
        'healing': 'Healing', 'harming': 'Harming', 'turtle_master': 'Turtle Master',
        'wind_charged': 'Wind Charged', 'weaving': 'Weaving', 'oozing': 'Oozing',
        'infested': 'Infested', 'luck': 'Luck',
    }
# 旧版按药水生成的 t2/t3 名称键已不再使用（见模块 docstring），
# 保留 ZH_NAMES / en_names / BREWABLE / TIER_PREFIX 不过于此脚本继续生成。
    return zh, en


def merge(path, additions, overrides):
    with io.open(path, encoding='utf-8') as fh:
        data = json.load(fh)
    added = 0
    for key, value in overrides.items():
        if key in data and data[key] == value:
            raise SystemExit('override %s in %s already has the target value %r — '
                             'nothing to rename' % (key, os.path.basename(path), value))
        data[key] = value
    for key, value in additions.items():
        if key in data and data[key] != value:
            raise SystemExit('key %s already exists in %s with a different value: %r'
                             % (key, os.path.basename(path), data[key]))
        if key not in data:
            data[key] = value
            added += 1
    ordered = {k: data[k] for k in sorted(data)}
    with io.open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(ordered, fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    return added


def main():
    zh_add, en_add = build_entries()
    # 已有键的改名（冥河瓶 → 瓶装三途川水）
    zh_rename = {'item.gensokyou.sanzu_flask': '瓶装三途川水'}
    en_rename = {'item.gensokyou.sanzu_flask': 'Bottled Sanzu River Water'}
    zh_add.pop('item.gensokyou.sanzu_flask', None)
    en_add.pop('item.gensokyou.sanzu_flask', None)
    print('zh_cn +%d (renamed 1), en_us +%d (renamed 1)'
          % (merge(ZH, zh_add, zh_rename), merge(EN, en_add, en_rename)))


if __name__ == '__main__':
    main()