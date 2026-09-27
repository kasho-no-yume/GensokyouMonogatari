"""校验 gensokyou 指导书的页型/组件类型与正文口吻。

页型部分：任何一个拼错的页型都会让 Patchouli 在 BookContentsBuilder 里抛
"Template <id> does not exist"，并进而让整本书退化为空内容
("Error loading and compiling book ..., using empty contents")，
属于静默且影响全书的严重故障，故用本脚本做静态守卫。

口吻部分：落实 guide-book 能力「书内文案面向玩家」——正文不得出现面向
开发者/技术人员的实现细节或工程占位措辞。

同步口径：落实 guide-book 能力「新增书内文案仅维护 zh_cn」——书内正文键在
zh_cn.json 中必须齐备，en_us.json 有则同检口吻、缺失不视为缺陷。

合法页型来源：
- Patchouli 内置页型：ClientBookRegistry#addPageTypes
- 本书模板：en_us/templates/<name>.json -> gensokyou:<name>
- Patchouli 内置组件：BookTemplate#componentTypes
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOK = ROOT / "src/main/resources/assets/gensokyou/patchouli_books/gensokyou_book/en_us"
ENTRIES = BOOK / "entries"
TEMPLATES = BOOK / "templates"
LANG = ROOT / "src/main/resources/assets/gensokyou/lang"

# Patchouli ClientBookRegistry#addPageTypes 注册的内置页型
PATCHOULI_PAGE_TYPES = {
    "patchouli:text",
    "patchouli:crafting",
    "patchouli:smelting",
    "patchouli:blasting",
    "patchouli:smoking",
    "patchouli:campfire",
    "patchouli:smithing",
    "patchouli:stonecutting",
    "patchouli:image",
    "patchouli:spotlight",
    "patchouli:empty",
    "patchouli:multiblock",
    "patchouli:link",
    "patchouli:relations",
    "patchouli:entity",
    "patchouli:quest",
}

# Patchouli BookTemplate#componentTypes 注册的内置组件
PATCHOULI_COMPONENT_TYPES = {
    "patchouli:text",
    "patchouli:item",
    "patchouli:image",
    "patchouli:header",
    "patchouli:separator",
    "patchouli:frame",
    "patchouli:entity",
    "patchouli:tooltip",
    "patchouli:custom",
}

template_ids = {f"gensokyou:{path.stem}" for path in TEMPLATES.glob("*.json")}
assert template_ids, "en_us/templates 下没有任何模板"
valid_page_types = PATCHOULI_PAGE_TYPES | template_ids

entry_files = sorted(ENTRIES.glob("*.json"))
assert entry_files, "en_us/entries 下没有任何词条"

for path in entry_files:
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    rel = path.relative_to(BOOK).as_posix()
    for field in ("name", "category", "icon", "pages"):
        assert field in data, f"{rel} 缺少字段 {field}"
    assert data["pages"], f"{rel} pages 为空"
    for index, page in enumerate(data["pages"]):
        if isinstance(page, str):
            continue
        assert "type" in page, f"{rel} 第 {index} 页缺少 type"
        assert page["type"] in valid_page_types, (
            f"{rel} 第 {index} 页页型无法解析: {page['type']}\n"
            f"合法页型: {sorted(valid_page_types)}"
        )

for path in sorted(TEMPLATES.glob("*.json")):
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    rel = path.relative_to(BOOK).as_posix()
    for inclusion in data.get("include", []):
        if isinstance(inclusion, str):
            target = inclusion
        else:
            target = inclusion.get("template")
        assert target in valid_page_types, f"{rel} include 了不存在的模板: {target}"
    components = data.get("components", [])
    assert components, f"{rel} 没有任何 components"
    for index, component in enumerate(components):
        assert "type" in component, f"{rel} 第 {index} 个组件缺少 type"
        assert component["type"] in PATCHOULI_COMPONENT_TYPES, (
            f"{rel} 第 {index} 个组件类型无法解析: {component['type']}"
        )

# ---- 正文口吻：面向玩家，不写给开发者 ----

TEXT_MACRO = re.compile(r"\$\([^)]*\)")
FORBIDDEN_TEXT = re.compile(
    r"minTier|spCost|recipe_index|show_recipes|crafting_shapeless|crafting_shaped"
    r"|use_resource_pack|blockstate|DataComponent|\.json\b|\.java\b|com\.bitsson"
    r"|TODO|FIXME|待补充|暂无来源|暂未实现|本次更新|已修复|占位",
    re.IGNORECASE,
)

text_keys = set()


def collect_text_keys(node):
    if isinstance(node, dict):
        for field, value in node.items():
            if field in ("text", "title") and isinstance(value, str) and value.startswith("gensokyou."):
                text_keys.add(value)
            collect_text_keys(value)
    elif isinstance(node, list):
        for value in node:
            collect_text_keys(value)


for path in entry_files + sorted(TEMPLATES.glob("*.json")):
    collect_text_keys(json.loads(path.read_text(encoding="utf-8-sig")))

lang_tables = {}
for lang_name in ("zh_cn.json", "en_us.json"):
    lang_tables[lang_name] = json.loads((LANG / lang_name).read_text(encoding="utf-8"))

# guide-book 规定新增文案仅维护 zh_cn，故 zh 必须齐备，en 有则同检口吻
for key in sorted(text_keys):
    assert key in lang_tables["zh_cn.json"], f"zh_cn.json 缺少书中引用的文本键 {key}"
    for lang_name, table in lang_tables.items():
        if key not in table:
            continue
        text = TEXT_MACRO.sub("", table[key])
        found = FORBIDDEN_TEXT.search(text)
        assert found is None, (
            f"{lang_name} {key} 面向开发者/含工程占位: «{found.group(0)}»\n"
            f"    {table[key]}"
        )

print(f"patchouli book page types ok ({len(entry_files)} entries, "
      f"{len(template_ids)} templates, {len(valid_page_types)} valid page types)")
print(f"patchouli book text tone ok ({len(text_keys)} referenced text keys)")

