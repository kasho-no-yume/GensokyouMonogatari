# -*- coding: utf-8 -*-
"""Java/数据文件 引用的语言键 ↔ lang JSON 差集审计（grace-ux-and-jei-tabs 4.3）。

用法: python tools/lang_audit.py            # 有缺失时退出码 1
      python tools/lang_audit.py --quiet   # 只报结果
覆盖三路来源：
  1) Java 字面量键（gui/msg/jei/hud/key/attribute/death/config/ritual 前缀）
  2) 动态拼接前缀（Component.translatable("x.y" + ...) 只列清单，人工过目）
  3) 数据文件展开：rituals/<pattern>.json -> jei.gensokyou.ritual.<pattern>（页签/候选行标题，
     动态拼 key 的重灾区）；ritual_recipes 每条 name -> jei.<ns>.recipe.<path>，
     effect -> jei.<ns>.effect.<path>；damage_type message_id -> death.attack.<id>
"""
import glob
import io
import json
import os
import re
import sys

LANG_DIR = "src/main/resources/assets/gensokyou/lang"
KEY_RE = re.compile(
    r'"((?:gui|msg|hud|key|config|attribute|item|block|entity|ritual)\.gensokyou'
    r'\.[A-Za-z0-9_.]+|jei\.[A-Za-z0-9_]+\.[A-Za-z0-9_.]+|death\.attack\.[A-Za-z0-9_.]+)"')
DYN_RE = re.compile(r'(?:translatable|translatableWithFallback)\(\s*"([a-z_][a-z0-9_.]*)"\s*\+')


def read_json(fp):
    return json.load(io.open(fp, encoding="utf-8-sig"))

fail = False


def load(name):
    return json.load(io.open(os.path.join(LANG_DIR, name), encoding="utf-8"))


def report(title, missing):
    global fail
    if missing:
        fail = True
        print("MISSING (%d) %s" % (len(missing), title))
        for k in sorted(missing):
            print("  ", k)
    else:
        print("ok: %s" % title)


en, zh = load("en_us.json"), load("zh_cn.json")
# 中文优先：zh_cn 为第一语言源，必须完整（en 有的键 zh 必须有）；
# zh-only（英文尚未同步）允许，仅计数提示——见 project.md「中文优先」。
only_en = set(en) - set(zh)
only_zh = set(zh) - set(en)
if only_en:
    fail = True
    print("MISSING (zh has no counterpart for en key) en-only=%s" % sorted(only_en))
else:
    print("ok: en/zh zh-first aligned (en=%d, zh=%d, zh-only=%d)"
          % (len(en), len(zh), len(only_zh)))

java_keys = set()
dyn_prefixes = set()
for root, _dirs, files in os.walk("src/main/java"):
    for f in files:
        if not f.endswith(".java"):
            continue
        text = io.open(os.path.join(root, f), encoding="utf-8", errors="replace").read()
        java_keys.update(k for k in KEY_RE.findall(text) if not k.endswith("."))
        dyn_prefixes.update(DYN_RE.findall(text))

report("java literal keys not in lang",
       {k for k in java_keys if k not in en and k not in zh})
print("dynamic prefixes (manual review):")
for p in sorted(dyn_prefixes):
    print("  ", p + "*")

expanded = set()
for fp in glob.glob("src/main/resources/data/gensokyou/rituals/*.json"):
    path = os.path.basename(fp)[:-len(".json")]
    data = read_json(fp)
    ns = data.get("id", path).split(":")[0]
    expanded.add("jei.%s.ritual.%s" % (ns, path))
for fp in glob.glob("src/main/resources/data/gensokyou/ritual_recipes/*.json"):
    data = read_json(fp)
    for r in data.get("recipes", []):
        expanded.add("jei.gensokyou.recipe.%s" % r["name"])
        if r.get("effect"):
            ens, path = r["effect"].split(":")
            expanded.add("jei.%s.effect.%s" % (ens, path))
for fp in glob.glob("src/main/resources/data/gensokyou/damage_type/*.json"):
    data = read_json(fp)
    if data.get("message_id"):
        expanded.add("death.attack.%s" % data["message_id"])
report("data-derived keys not in zh", {k for k in expanded if k not in zh})

sys.exit(1 if fail else 0)
