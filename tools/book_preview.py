#!/usr/bin/env python3
"""指导书离线预览 / 编辑台。

把 `assets/gensokyou/patchouli_books/gensokyou_book/en_us/` 下的条目页结构，与
`assets/gensokyou/lang/zh_cn.json` 下的正文，**拼成一本能翻的书**在浏览器里打开：

  python tools/book_preview.py            # 起本地服务并开浏览器（默认 127.0.0.1:8777）
  python tools/book_preview.py --check    # 只做体检：页数、溢出、缺键、孤儿键
  python tools/book_preview.py --static   # 导出一个 build/bookpreview/preview.html（只读）

为什么要有它：书的页面顺序/页型在 entries/*.json，正文在 lang 里，两边只靠
`gensokyou.book.entry.xxx.pN` 这种字符串对应；Patchouli 的文本页又是 116x156 的
固定版面、页列表静态、塞不下时默认整页缩字（RESIZE）却没有任何提示。于是
「改一句话」要先猜键、改完还要进游戏翻页确认。本工具把这些拼到一屏：

  左栏   章节 / 条目树（sortnum、门槛、是否生成器产出）
  中间   双页书（116x156 ×3 放大），按 Patchouli 的「铺」翻页：(1,2)(3,4)(5,6)…
         文本页按真实折行渲染，超出页底画红线
         「整条纵览」一次铺开该条目全部页；页条可拖动改页序
  右栏   当前页来源：页型、文本键、原始 JSON、上移/下移/删除/插入页
         重排工具：按段拆页 / 与本页合并 / 整条自动合并
  正文就地编辑 → 保存直接写回 zh_cn.json（保 CRLF、保键序、原子替换、先备份）

每页右上角的 ✎ 可以只改那一页（左右页都行）；「体检」面板列出全部超页底、
偏空、缺键、孤儿键的页，点「去看」直达。

生成器产出的条目（ritual_* / item_*，见 tools/gen_ritual_book_entries.py）在
UI 里标为只读，正文仍可改，但结构改动会被下次生成覆盖，所以拒绝写回。

折行模型（页面 116px 宽、行高 9px）与游戏内 Font.split 同口径：ASCII 约 6px、
中日韩全角 9px、空格 4px。是估算，用来抓「明显塞不下」，不是像素级复刻；
真要看像素级版式仍以游戏内 F3+T 重开书为准。超页底时 Patchouli 默认 RESIZE
会整页缩字，所以 UI 里标红的是「会被压小/压花」而不是「内容丢失」。

只依赖标准库。物品图标从 mod 贴图目录与 vanilla client jar 直接读。
"""

import argparse
import json
import os
import re
import shutil
import sys
import threading
import time
import webbrowser
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gensokyou"
BOOK_ROOT = ASSETS / "patchouli_books/gensokyou_book"
ENTRY_DIR = BOOK_ROOT / "en_us/entries"
CATEGORY_DIR = BOOK_ROOT / "en_us/categories"
TEMPLATE_DIR = BOOK_ROOT / "en_us/templates"
BOOK_JSON = RES / "data/gensokyou/patchouli_books/gensokyou_book/book.json"
LANG_DIR = ASSETS / "lang"
RITUAL_RECIPES = RES / "data/gensokyou/ritual_recipes"
CRAFT_RECIPES = RES / "data/gensokyou/recipe"
BUILD = ROOT / "build/bookpreview"

# Patchouli 页面几何（Patchouli 1.21.1-93：TextLayouter 的 pageWidth/lineHeight/
# PAGE_HEIGHT，以及 PageText.getTextHeight / PageSpotlight.getTextHeight）。
PAGE_W = 116
PAGE_H = 156
LINE_H = 9
SPOTLIGHT_TOP = 40

# Patchouli 内置页型 + 本书模板（en_us/templates/<name>.json -> gensokyou:<name>）。
PATCHOULI_PAGE_TYPES = {
    "patchouli:text", "patchouli:crafting", "patchouli:smelting", "patchouli:blasting",
    "patchouli:smoking", "patchouli:campfire", "patchouli:smithing", "patchouli:stonecutting",
    "patchouli:image", "patchouli:spotlight", "patchouli:empty", "patchouli:multiblock",
    "patchouli:link", "patchouli:relations", "patchouli:entity", "patchouli:quest",
}

# 由 Java 组件在运行期取用的正文键前缀：不写在条目 JSON 里，体检时不算孤儿。
JAVA_RENDERED_PREFIXES = (
    "gensokyou.book.ritual.",
    "gensokyou.book.loot.",
    "gensokyou.book.smelt.",
)


# 插入页时可选的默认骨架（只放最常用的几种，其余手写 JSON）。
NEW_PAGE_DEFAULTS = {
    "patchouli:text": {"type": "patchouli:text", "text": "gensokyou.book.entry.NEW.p1"},
    "patchouli:spotlight": {"type": "patchouli:spotlight", "item": "minecraft:stone",
                            "text": "gensokyou.book.entry.NEW.p1"},
    "patchouli:empty": {"type": "patchouli:empty"},
    "patchouli:entity": {"type": "patchouli:entity", "entity": "minecraft:pig"},
    "patchouli:crafting": {"type": "patchouli:crafting", "recipe": "gensokyou:ritual_pedestal"},
    "gensokyou:ritual_tier_page": {"type": "gensokyou:ritual_tier_page",
                                   "ritual": "gensokyou:zaohua_circle", "tier": 0},
    "gensokyou:ritual_page": {"type": "gensokyou:ritual_page",
                              "ritual": "gensokyou:zaohua_circle", "recipe_index": 0},
    "gensokyou:loot_page": {"type": "gensokyou:loot_page", "tool_item": "", "tier": "0",
                            "mode": "tool", "entries": ""},
    "gensokyou:smelt_page": {"type": "gensokyou:smelt_page", "ritual": "", "result": ""},
    "gensokyou:boss_arrival": {"type": "gensokyou:boss_arrival", "anchor": "arrival",
                               "ritual": "", "recipe": "", "token": ""},
    "patchouli:multiblock": {"type": "patchouli:multiblock", "name": "gensokyou.book.ritual.structure.0",
                             "multiblock": {"pattern": [["0"]],
                                            "mapping": {"0": "gensokyou:ritual_core"},
                                            "symmetrical": True},
                             "enable_visualize": True},
}

# ---------------------------------------------------------------- 文本折行模型

# 与页面里 wrapText() 一一对应，改一处必须改另一处。
_WIDTH_RE = re.compile(
    r"[\u1100-\u115f\u2e80-\ua4cf\uac00-\ud7a3\uf900-\ufaff\ufe30-\ufe4f"
    r"\uff00-\uff60\uffe0-\uffe6\u3000-\u303f]")
_NARROW = set("il.,:;'!|")
_HALF = set('[]()"*<>{}')


def char_width(ch):
    if not _WIDTH_RE.match(ch):
        if ch == " ":
            return 4
        if ch in _NARROW:
            return 2
        if ch in _HALF:
            return 4
        return 6
    return 9


def flatten_macros(text):
    """把 Patchouli 行内宏摊平成纯文本（折行用）。"""
    out = (text.replace("$(br2)", "\n\n").replace("$(p)", "\n\n")
           .replace("$(br)", "\n")
           .replace("$(li2)", "\n  • ").replace("$(li)", "\n• "))
    out = re.sub(r"\$\([^)]*\)", "", out)
    out = out.replace("\\n", "\n")
    return out.lstrip("\n") if not text.lstrip().startswith("$(") else out


def wrap_text(text, width=PAGE_W):
    """按 Minecraft 字宽贪心折行，返回行列表。"""
    flat = flatten_macros(text or "")
    lines = []
    for para in flat.split("\n"):
        if not para:
            lines.append("")
            continue
        cur, cur_w, last_space = [], 0, -1
        for ch in para:
            w = char_width(ch)
            if cur_w + w > width and cur:
                cut = len(cur)
                # 英文优先在空格处断行，但不要断得太靠前。
                if last_space > 0 and last_space >= len(cur) // 2:
                    cut = last_space
                lines.append("".join(cur[:cut]).rstrip())
                rest = "".join(cur[cut:]).lstrip()
                cur = list(rest)
                cur_w = sum(char_width(c) for c in cur)
                last_space = -1
            if ch == " ":
                last_space = len(cur)
            cur.append(ch)
            cur_w += w
        lines.append("".join(cur).rstrip())
    return lines


def page_top(page, index):
    kind = page.get("type")
    if kind == "patchouli:text":
        if index == 0:
            return 22
        return 12 if page.get("title") else 0
    if kind == "patchouli:spotlight":
        return SPOTLIGHT_TOP
    return None


def page_budget(page, index):
    top = page_top(page, index)
    if top is None:
        return None
    return max(0, (PAGE_H - top) // LINE_H)


# ---------------------------------------------------------------- 文件读写

def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def dump_crlf(data):
    return json.dumps(data, ensure_ascii=False, indent=2).replace("\n", "\r\n") + "\r\n"


_BACKED_UP = set()


def backup_once(path):
    path = Path(path)
    key = str(path)
    if key in _BACKED_UP or not path.exists():
        return
    BUILD.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    shutil.copy2(path, BUILD / f"{path.stem}.{stamp}.json.bak")
    _BACKED_UP.add(key)


def write_json_crlf(path, data):
    path = Path(path)
    backup_once(path)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_bytes(dump_crlf(data).encode("utf-8"))
    os.replace(tmp, path)


# ---------------------------------------------------------------- 数据装载

_GENERATED = None


def generated_entry_files():
    """从生成器里读「它会覆盖哪些条目文件」，避免 UI 改完被下次生成冲掉。"""
    global _GENERATED
    if _GENERATED is not None:
        return _GENERATED
    names = set()
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location(
            "_gensokyou_book_gen", ROOT / "tools/gen_ritual_book_entries.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        names = {f"ritual_{path}.json" for path, _icon, _nr in module.ENTRIES}
        names |= {f"item_{item.split(':', 1)[1]}.json"
                  for item, _src, _recipe, _cat, _sn in module.ITEM_RECIPE_ENTRIES}
    except Exception as exc:  # 生成器改签名时不要让预览器整块挂掉
        print(f"[bookpreview] 读不到生成器条目清单，按空处理：{exc}", file=sys.stderr)
    _GENERATED = names
    return names


def load_ritual_recipes():
    by_pattern, by_id = {}, {}
    for path in sorted(RITUAL_RECIPES.glob("*.json")):
        data = read_json(path)
        pattern = data.get("pattern")
        recipes = []
        for recipe in data.get("recipes", []):
            recipe = dict(recipe)
            rid = recipe.get("id") or (
                f"gensokyou:{recipe['name']}" if recipe.get("name") else None)
            recipe["_id"] = rid
            recipes.append(recipe)
            if rid:
                by_id[rid] = recipe
        by_pattern[pattern] = recipes
    return by_pattern, by_id


def load_crafting_recipes():
    out = {}
    if not CRAFT_RECIPES.is_dir():
        return out
    for path in sorted(CRAFT_RECIPES.glob("*.json")):
        try:
            out[f"gensokyou:{path.stem}"] = read_json(path)
        except Exception:
            continue
    return out


def icon_paths(item_id):
    """返回可供 /tex 直接读取的候选（mod 贴图优先，其次 vanilla jar）。"""
    if not item_id or item_id.startswith("#"):
        return []
    ns, _, path = item_id.partition(":")
    if not path:
        return []
    if ns == "gensokyou":
        return [p for p in (ASSETS / f"textures/item/{path}.png",
                            ASSETS / f"textures/block/{path}.png") if p.is_file()]
    return []


_VANILLA_CACHE = {}


def vanilla_jar():
    base = Path(os.path.expanduser("~")) / ".gradle/caches/neoformruntime/artifacts"
    if "jar" in _VANILLA_CACHE:
        return _VANILLA_CACHE["jar"]
    found = sorted(base.glob("minecraft_*_client.jar")) if base.is_dir() else []
    _VANILLA_CACHE["jar"] = found[-1] if found else None
    return _VANILLA_CACHE["jar"]


def _read_asset(ns, rel):
    """读 assets/<ns>/<rel>（mod 目录优先，其次 vanilla jar）。"""
    if ns == "gensokyou":
        path = ASSETS / rel
        if path.is_file():
            return path.read_bytes()
    jar = vanilla_jar()
    if jar is None:
        return None
    try:
        with zipfile.ZipFile(jar) as zf:
            return zf.read(f"assets/{ns}/{rel}")
    except Exception:
        return None


def _model_texture_ref(ns, path):
    """沿 item model 的 parent 链找一个能用的贴图引用（跳过 entity/ 贴图）。

    parent 是相对 `models/` 的资源位置（如 `gensokyou:block/treasure_basin`），
    所以入队的是 "item/xxx" / "block/xxx" 这种带目录的路径。
    """
    seen, queue = set(), [(ns, f"item/{path}")]
    while queue and len(seen) < 8:
        cns, cpath = queue.pop(0)
        if (cns, cpath) in seen:
            continue
        seen.add((cns, cpath))
        raw = _read_asset(cns, f"models/{cpath}.json")
        if not raw:
            continue
        try:
            model = json.loads(raw.decode("utf-8-sig"))
        except Exception:
            continue
        textures = model.get("textures") or {}
        for want in ("layer0", "all", "texture", "front", "side", "top", "particle", "part0"):
            ref = textures.get(want)
            if not isinstance(ref, str):
                continue
            if ref.startswith("#"):
                ref = textures.get(ref[1:], "")
            if ref and not ref.startswith("entity/"):
                return ref
        parent = model.get("parent")
        if isinstance(parent, str):
            if ":" in parent:
                pns, _, ppath = parent.partition(":")
                queue.append((pns, ppath))
            else:
                queue.append((cns, parent))
    return None


def _texture_by_ref(ref, default_ns="minecraft"):
    ns, _, rel = ref.partition(":")
    if not rel:
        ns, rel = default_ns, ref
    raw = _read_asset(ns, f"textures/{rel}.png")
    return raw


def texture_bytes(item_id):
    if item_id in _VANILLA_CACHE:
        return _VANILLA_CACHE[item_id]
    data = None
    for path in icon_paths(item_id):
        data = path.read_bytes()
        break
    if data is None and ":" in item_id and not item_id.startswith("#"):
        ns, _, path = item_id.partition(":")
        for sub in ("item", "block"):
            data = _read_asset(ns, f"textures/{sub}/{path}.png")
            if data:
                break
        if data is None:
            ref = _model_texture_ref(ns, path)
            if ref:
                data = _texture_by_ref(ref, ns)
    _VANILLA_CACHE[item_id] = data
    return data


def book_stats(entries, categories, zh, en, book):
    text_keys = set()
    pages = 0
    text_pages = 0
    overflow = []
    missing = []
    types = {}
    for entry in entries:
        for index, page in enumerate(entry["raw"].get("pages", [])):
            page = page if isinstance(page, dict) else {"type": "patchouli:text", "text": page}
            pages += 1
            kind = page.get("type", "?")
            types[kind] = types.get(kind, 0) + 1
            for field in ("text", "title", "name"):
                value = page.get(field)
                if field == "name" and kind == "patchouli:multiblock" and isinstance(value, str):
                    text_keys.add(value)
                elif isinstance(value, str) and value.startswith("gensokyou."):
                    text_keys.add(value)
            if kind in ("patchouli:text", "patchouli:spotlight"):
                text_pages += 1
                key = page.get("text") or ""
                if key:
                    text_keys.add(key)
                if key and key not in zh and key not in en:
                    missing.append((entry["file"], f"第 {index + 1} 页", key))
                info = page_text_info(page, index, zh, en)
                if info["overflow"]:
                    overflow.append((entry["file"], index, len(info["lines"]), info["budget"]))
        value = entry["raw"].get("name")
        if isinstance(value, str) and value.startswith(("gensokyou.", "item.", "entity.", "jei.")):
            text_keys.add(value)
            if value not in zh and value not in en:
                missing.append((entry["file"], "条目名", value))
    for category in categories:
        for field in ("name", "description"):
            value = category["raw"].get(field)
            if isinstance(value, str):
                text_keys.add(value)
                if value not in zh and value not in en:
                    missing.append((category["file"], "章节名" if field == "name" else "章节说明", value))
    for field in ("name", "landing_text", "subtitle"):
        value = book.get(field)
        if isinstance(value, str):
            text_keys.add(value)

    book_keys = {k for k in zh if k.startswith("gensokyou.book.")}
    unreferenced = book_keys - text_keys
    # 这些前缀的键由 Java 组件在运行期取用（RitualPage/Tier/Loot/Smelt），
    # 不写在条目 JSON 里，所以不该算孤儿。
    component = sorted(k for k in unreferenced if k.startswith(JAVA_RENDERED_PREFIXES))
    orphans = sorted(unreferenced - set(component))
    return {
        "entries": len(entries), "pages": pages, "textPages": text_pages,
        "types": dict(sorted(types.items(), key=lambda kv: -kv[1])),
        "overflow": overflow, "missing": missing, "orphans": orphans,
        "componentKeys": component,
        "referencedKeys": sorted(text_keys), "bookKeys": len(book_keys),
    }


def page_text_info(page, index, zh, en):
    key = page.get("text") or ""
    text = zh.get(key)
    if text is None:
        text = en.get(key)
    missing = text is None
    lines = wrap_text(text or (f"⟨缺少文本键：{key}⟩" if key else ""))
    budget = page_budget(page, index)
    return {
        "key": key,
        "text": text if text is not None else "",
        "missing": missing,
        "lines": lines,
        "budget": budget,
        "overflow": bool(budget) and len(lines) > budget,
    }


def load_state():
    zh = read_json(LANG_DIR / "zh_cn.json")
    en = read_json(LANG_DIR / "en_us.json")
    book = read_json(BOOK_JSON)
    generated = generated_entry_files()
    by_pattern, by_id = load_ritual_recipes()
    crafting = load_crafting_recipes()

    categories = []
    for path in sorted(CATEGORY_DIR.glob("*.json")):
        raw = read_json(path)
        cid = raw.get("id") or f"gensokyou:{path.stem}"
        categories.append({"id": cid, "file": path.name, "raw": raw})
    category_ids = {c["id"] for c in categories}

    entries = []
    for path in sorted(ENTRY_DIR.glob("*.json")):
        raw = read_json(path)
        entries.append({
            "file": path.name,
            "path": f"en_us/entries/{path.name}",
            "raw": raw,
            "generated": path.name in generated,
        })
    entries.sort(key=lambda e: (e["raw"].get("category", ""), e["raw"].get("sortnum", 0), e["file"]))

    templates = {}
    for path in sorted(TEMPLATE_DIR.glob("*.json")):
        raw = read_json(path)
        variables = sorted(set(re.findall(
            r"#([A-Za-z0-9_]+)#", json.dumps(raw, ensure_ascii=False))))
        templates[path.stem] = {"raw": raw, "vars": variables}
    valid_types = sorted(PATCHOULI_PAGE_TYPES | {f"gensokyou:{t}" for t in templates})

    # 模板页也能从「插入页」下拉里直接建：变量先填空串，字段与模板里的 #var# 对齐。
    defaults = dict(NEW_PAGE_DEFAULTS)
    for name, info in templates.items():
        key = f"gensokyou:{name}"
        if key in defaults:
            continue
        defaults[key] = dict({"type": key}, **{v: "" for v in info["vars"]})

    return {
        "root": str(ROOT),
        "book": book,
        "lang": {"zh_cn": zh, "en_us": en},
        "categories": [{"id": c["id"], "file": c["file"], "raw": c["raw"]} for c in categories],
        "categoryIds": sorted(category_ids),
        "entries": entries,
        "templates": templates,
        "validPageTypes": valid_types,
        "newPageDefaults": defaults,
        "recipesByPattern": by_pattern,
        "recipeById": by_id,
        "crafting": crafting,
        "stats": book_stats(entries, categories, zh, en, book),
        "rev": compute_rev(),
    }


def compute_rev():
    parts = []
    for path in sorted(ENTRY_DIR.glob("*.json")):
        st = path.stat()
        parts.append(f"{path.name}:{st.st_mtime_ns}:{st.st_size}")
    for path in (LANG_DIR / "zh_cn.json", LANG_DIR / "en_us.json", BOOK_JSON):
        if path.is_file():
            st = path.stat()
            parts.append(f"{path.name}:{st.st_mtime_ns}:{st.st_size}")
    return str(abs(hash("|".join(parts))))


# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "BookPreview/1.0"

    def log_message(self, fmt, *args):
        pass

    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        if isinstance(body, str):
            body = body.encode("utf-8")
        try:
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
        except (ConnectionAbortedError, ConnectionResetError, BrokenPipeError):
            pass

    def _json(self, data, code=200):
        self._send(code, json.dumps(data, ensure_ascii=False))

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(length).decode("utf-8") or "{}")

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path in ("/", "/index.html"):
            self._send(200, PAGE_HTML, "text/html; charset=utf-8")
        elif parsed.path == "/api/state":
            self._json(load_state())
        elif parsed.path == "/api/rev":
            self._json({"rev": compute_rev()})
        elif parsed.path == "/tex":
            item = (parse_qs(parsed.query).get("item") or [""])[0]
            data = texture_bytes(unquote(item))
            if data is None:
                self._send(404, b"", "image/png")
            else:
                self._send(200, data, "image/png")
        else:
            self._send(404, b"not found", "text/plain; charset=utf-8")

    def do_POST(self):
        parsed = urlparse(self.path)
        try:
            body = self._body()
            if parsed.path == "/api/save-lang":
                updates = body.get("updates") or {}
                deletes = body.get("deletes") or []
                if not isinstance(updates, dict) or not isinstance(deletes, list):
                    return self._json({"ok": False, "error": "updates/deletes 类型不对"}, 400)
                if not updates and not deletes:
                    return self._json({"ok": False, "error": "没有要保存的正文"}, 400)
                path = LANG_DIR / "zh_cn.json"
                lang = read_json(path)
                removed = 0
                for key in deletes:
                    if isinstance(key, str) and lang.pop(key, None) is not None:
                        removed += 1
                for key, value in updates.items():
                    if not isinstance(key, str) or not isinstance(value, str):
                        return self._json({"ok": False, "error": "键与正文都必须是字符串"}, 400)
                    lang[key] = value
                write_json_crlf(path, lang)
                return self._json({"ok": True, "saved": len(updates), "removed": removed,
                                   "rev": compute_rev()})
            if parsed.path == "/api/save-entry":
                name = body.get("file") or ""
                entry = body.get("entry")
                if not name.endswith(".json") or "/" in name or "\\" in name:
                    return self._json({"ok": False, "error": "非法文件名"}, 400)
                if name in generated_entry_files():
                    return self._json({
                        "ok": False,
                        "error": f"{name} 由 tools/gen_ritual_book_entries.py 生成，"
                                 "直接改会被下次生成覆盖；请改生成器或先取消其生成清单。"}, 409)
                error = validate_entry(entry)
                if error:
                    return self._json({"ok": False, "error": error}, 400)
                write_json_crlf(ENTRY_DIR / name, entry)
                return self._json({"ok": True, "file": name, "rev": compute_rev()})
            if parsed.path == "/api/regenerate":
                import subprocess
                script = ROOT / "tools/gen_ritual_book_entries.py"
                only = (body or {}).get("only") or None
                cmd = [sys.executable, str(script)]
                if only:
                    cmd += ["--only", str(only)]
                try:
                    proc = subprocess.run(
                        cmd, cwd=str(ROOT), capture_output=True, text=True,
                        encoding="utf-8", errors="replace", timeout=180)
                except subprocess.TimeoutExpired:
                    return self._json({"ok": False, "error": "生成器超时（180s）"}, 500)
                return self._json({
                    "ok": proc.returncode == 0,
                    "code": proc.returncode,
                    "output": (proc.stdout or "") + (proc.stderr or ""),
                    "rev": compute_rev(),
                })
            return self._json({"ok": False, "error": "unknown endpoint"}, 404)
        except Exception as exc:
            return self._json({"ok": False, "error": f"{type(exc).__name__}: {exc}"}, 500)


class QuietServer(ThreadingHTTPServer):
    """浏览器翻页/刷新会中断进行中的图标请求，10053 之类的噪音不必打印。"""
    daemon_threads = True

    def handle_error(self, request, client_address):
        exc = sys.exc_info()[1]
        if isinstance(exc, (ConnectionAbortedError, ConnectionResetError, BrokenPipeError)):
            return
        super().handle_error(request, client_address)


def validate_entry(entry):
    if not isinstance(entry, dict):
        return "条目不是对象"
    for field in ("name", "category", "icon", "pages"):
        if field not in entry:
            return f"缺少字段 {field}"
    if not isinstance(entry["pages"], list) or not entry["pages"]:
        return "pages 必须是非空数组"
    templates = {f"gensokyou:{p.stem}" for p in TEMPLATE_DIR.glob("*.json")}
    valid = PATCHOULI_PAGE_TYPES | templates
    for index, page in enumerate(entry["pages"]):
        if isinstance(page, str):
            continue
        if not isinstance(page, dict) or "type" not in page:
            return f"第 {index + 1} 页缺少 type"
        if page["type"] not in valid:
            return f"第 {index + 1} 页页型无法解析：{page['type']}"
    return None


# ---------------------------------------------------------------- 体检

def run_check(strict=False):
    state = load_state()
    stats = state["stats"]
    print(f"条目 {stats['entries']} 个，页面 {stats['pages']} 页"
          f"（文本/聚焦页 {stats['textPages']} 页）")
    print("页型分布：")
    for kind, count in stats["types"].items():
        print(f"  {count:>4}  {kind}")
    print(f"\n正文键：引用 {len(stats['referencedKeys'])} 个，"
          f"lang 里书相关键 {stats['bookKeys']} 个")

    problems = 0
    if stats["missing"]:
        problems += len(stats["missing"])
        print("\n[缺键] 游戏内会显示裸键：")
        for file, where, key in stats["missing"]:
            print(f"  {file}  {where}  {key}")
    if stats["overflow"]:
        problems += len(stats["overflow"])
        print("\n[超页底] 游戏内默认 RESIZE 会把整页字缩小（越超越糊），建议拆页：")
        for file, index, lines, budget in stats["overflow"]:
            print(f"  {file} 第 {index + 1} 页  {lines} 行 > {budget} 行")
    if stats["orphans"]:
        print("\n[孤儿键] lang 里有、但书里没人引用（多为改写后残留）：")
        for key in stats["orphans"]:
            print(f"  {key}")
    if stats["componentKeys"]:
        print(f"\n（另有 {len(stats['componentKeys'])} 个键由 Java 组件直接取用，忽略）")
    if not problems:
        print("\n没有缺键、没有溢出。")
    if strict and problems:
        raise SystemExit(1)


# ---------------------------------------------------------------- 前端

PAGE_HTML = r"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<link rel="icon" href="data:,">
<title>幻想乡物语 · 指导书预览</title>
<style>
:root{
  --bg:#1d1a22; --panel:#262230; --panel2:#2f2a3b; --line:#3d3549;
  --ink:#e9e3f2; --dim:#a99fbb; --gold:#c9a227; --purple:#8a4dbf;
  --paper:#f3ecd8; --paper2:#e8dfc6; --parch:#2a2430;
}
*{box-sizing:border-box}
html,body{height:100%}
body{margin:0;background:var(--bg);color:var(--ink);
  font:13px/1.5 "Microsoft YaHei UI","Segoe UI",system-ui,sans-serif;overflow:hidden}
button,select,input,textarea{font:inherit;color:inherit}
button{background:var(--panel2);border:1px solid var(--line);border-radius:6px;
  padding:4px 9px;cursor:pointer}
button:hover{border-color:var(--purple)}
button:disabled{opacity:.45;cursor:not-allowed}
button.primary{background:var(--purple);border-color:var(--purple);color:#fff}
header{display:flex;align-items:center;gap:12px;padding:8px 14px;
  background:linear-gradient(180deg,#2c2536,#241f2d);border-bottom:1px solid var(--line)}
header h1{font-size:14px;margin:0;font-weight:600;letter-spacing:.5px}
header h1 small{color:var(--dim);font-weight:400;margin-left:8px}
#dirty{color:var(--gold);font-size:12px}
#banner{display:none;background:#4a3a12;color:#ffd77a;padding:5px 14px;font-size:12px;
  border-bottom:1px solid #6b5417;cursor:pointer}
#banner.show{display:block}
#app{display:grid;grid-template-columns:250px 1fr 330px;height:calc(100% - 49px)}
#tree{border-right:1px solid var(--line);overflow:auto;background:var(--panel)}
#search{margin:8px;width:calc(100% - 16px);background:#1b1822;border:1px solid var(--line);
  border-radius:6px;padding:5px 8px}
.cat{margin:2px 0}
.cat>summary{cursor:pointer;padding:5px 10px;color:var(--gold);font-weight:600;
  list-style:none;display:flex;justify-content:space-between}
.cat>summary::-webkit-details-marker{display:none}
.cat>summary span{color:var(--dim);font-weight:400}
.entry{display:flex;align-items:center;gap:7px;padding:4px 10px 4px 18px;cursor:pointer;
  border-left:2px solid transparent}
.entry:hover{background:#312b3d}
.entry.active{background:#3a2f4e;border-left-color:var(--gold)}
.entry img{width:16px;height:16px;image-rendering:pixelated;flex:none}
.entry .nm{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.entry .tag{font-size:10px;color:var(--dim);border:1px solid var(--line);
  border-radius:4px;padding:0 3px;flex:none}
.entry .tag.gen{color:#e0a94a;border-color:#5b4520}
main{overflow:auto;padding:14px 18px}
.entryhead{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:10px}
.entryhead .title{font-size:16px;font-weight:600}
.chip{font-size:11px;border:1px solid var(--line);border-radius:999px;padding:1px 8px;
  color:var(--dim)}
.chip.gate{color:#8fd18f;border-color:#33552f}
.chip.secret{color:#e0a94a;border-color:#5b4520}
.chip.gen{color:#e0a94a;border-color:#5b4520}
.strip{display:flex;gap:3px;flex-wrap:wrap;margin:8px 0 12px}
.strip .p{width:26px;height:22px;border:1px solid var(--line);border-radius:4px;
  background:var(--panel2);font-size:10px;color:var(--dim);cursor:pointer;
  display:flex;align-items:center;justify-content:center;position:relative}
.strip .p.active{border-color:var(--gold);color:var(--ink);background:#3a2f4e}
.strip .p.ovf{border-color:#8c3b3b}
.strip .p.ovf::after{content:"";position:absolute;top:2px;right:2px;width:4px;height:4px;
  border-radius:50%;background:#e05252}
.spread{display:flex;gap:10px;justify-content:center;align-items:flex-start}
.overview{display:flex;flex-wrap:wrap;gap:14px;justify-content:center}
.overview .page{cursor:pointer}
.page.sel{outline:2px solid var(--gold);outline-offset:2px}
.page{width:348px;height:468px;background:linear-gradient(160deg,var(--paper),var(--paper2));
  color:var(--parch);border-radius:4px;padding:14px 14px 10px;position:relative;
  box-shadow:0 8px 26px #0007,inset 0 0 0 1px #d8cca9;overflow:hidden;
  display:flex;flex-direction:column}
.page.empty{background:#221e29;box-shadow:none;border:1px dashed var(--line)}
.page.editing{box-shadow:0 8px 26px #0007,inset 0 0 0 2px var(--purple)}
.page .edit{position:absolute;top:3px;right:5px;font:11px/1 sans-serif;color:#7a6f56;
  border:1px solid #cbbf9d;border-radius:4px;padding:2px 4px;background:#fff8e6;
  cursor:pointer;opacity:.35}
.page:hover .edit{opacity:1}
.page.editing .edit{background:var(--purple);border-color:var(--purple);color:#fff;opacity:1}
.strip .p[draggable]{cursor:grab}
.strip .p.low{border-color:#8a6d1f}
.strip .p.low::before{content:"";position:absolute;top:2px;right:2px;width:4px;height:4px;
  border-radius:50%;background:#c9a227}
.page .pi{position:absolute;bottom:4px;right:8px;font-size:10px;color:#9b8f76}
.page .pt{position:absolute;top:4px;left:8px;font-size:9px;color:#a1957a;letter-spacing:.3px}
.page .pofv{position:absolute;bottom:4px;left:8px;font-size:10px;color:#a33}
.page .body{flex:1;min-height:0;overflow:hidden}
.lines{font:22px/27px "Sarasa Mono SC","Microsoft YaHei",monospace;white-space:pre-wrap}
.lines .ln{min-height:27px}
.lines .ln.over{color:#a33;background:#f3d7d7}
.cutline{border-top:1px dashed #c0392b;position:relative;margin:2px 0}
.cutline span{position:absolute;right:0;top:-9px;font:10px/1 sans-serif;color:#c0392b;
  background:var(--paper);padding:0 3px}
.pgtitle{font-weight:700;font-size:15px;color:#4A2B6B;margin-bottom:6px}
textarea.editor{width:100%;height:100%;resize:none;background:#fffdf4;color:var(--parch);
  border:1px solid #c9bd9c;border-radius:3px;padding:10px;font:18px/26px
  "Sarasa Mono SC","Microsoft YaHei",monospace}
.pgnote{display:flex;justify-content:space-between;gap:6px;font-size:10px;color:#8a7f68;
  border-top:1px solid #d8cca9;padding-top:3px;margin-top:4px}
.pgnote .bad{color:#a33;font-weight:700}
.icon{width:32px;height:32px;image-rendering:pixelated;vertical-align:middle}
.iconx{display:inline-flex;position:relative;margin:2px}
.iconx b{position:absolute;right:0;bottom:-2px;font:10px/1 sans-serif;color:#fff;
  text-shadow:0 1px 2px #000,0 0 3px #000}
.icontile{width:32px;height:32px;border-radius:3px;display:inline-flex;align-items:center;
  justify-content:center;font-size:10px;color:#fff;text-shadow:0 1px 2px #000}
.card{background:#fffdf4;border:1px solid #d8cca9;border-radius:4px;padding:8px;margin:4px 0}
.card h4{margin:0 0 6px;font-size:13px;color:#4A2B6B}
.card .muted{color:#8a7f68;font-size:11px}
.row{display:flex;flex-wrap:wrap;gap:2px;align-items:center}
.arrow{color:#8a7f68;margin:0 4px}
.mbgrid{display:grid;gap:1px;justify-content:center;margin:4px auto}
.mbcell{width:12px;height:12px;border-radius:1px;background:#00000010}
.mblegend{display:flex;flex-wrap:wrap;gap:4px;font-size:9px;color:#6c6250;margin-top:6px}
.mblegend i{display:inline-block;width:8px;height:8px;border-radius:1px;margin-right:3px}
.mblegend b{font-weight:400}
input[type=range]{width:100%}
.pager{display:flex;align-items:center;justify-content:center;gap:10px;margin-top:12px;
  color:var(--dim)}
aside#inspector{border-left:1px solid var(--line);background:var(--panel);overflow:auto;
  padding:10px 12px}
aside h3{font-size:11px;text-transform:uppercase;letter-spacing:1px;color:var(--dim);
  margin:12px 0 6px;border-bottom:1px solid var(--line);padding-bottom:4px}
.kv{display:grid;grid-template-columns:82px 1fr;gap:4px 8px;align-items:center;font-size:12px}
.kv label{color:var(--dim)}
.kv input,.kv select{background:#1b1822;border:1px solid var(--line);border-radius:4px;
  padding:3px 6px;width:100%}
.kv input[type=checkbox]{width:auto}
.mono{font-family:Consolas,"Sarasa Mono SC",monospace;font-size:11px}
code.key{display:block;background:#1b1822;border:1px solid var(--line);border-radius:4px;
  padding:4px 6px;word-break:break-all;color:#c9b6e8;font-size:11px}
textarea.json{width:100%;height:220px;background:#1b1822;border:1px solid var(--line);
  border-radius:4px;padding:6px;font:11px/1.45 Consolas,monospace;resize:vertical}
.btnrow{display:flex;gap:5px;flex-wrap:wrap;margin:6px 0}
.warn{background:#4a1f26;border:1px solid #7d3a44;color:#ffb3bd;border-radius:4px;
  padding:6px 8px;font-size:11px;margin:6px 0}
.ok{color:#8fd18f}
#toast{position:fixed;bottom:16px;left:50%;transform:translateX(-50%);background:#3a2f4e;
  border:1px solid var(--purple);color:#fff;padding:7px 14px;border-radius:8px;opacity:0;
  transition:opacity .2s;pointer-events:none;font-size:12px}
#toast.show{opacity:1}
#report{display:none;position:fixed;inset:40px;background:var(--panel);border:1px solid var(--line);
  border-radius:10px;padding:16px;overflow:auto;box-shadow:0 20px 60px #000a;z-index:20}
#report h2{margin:0 0 10px;font-size:15px}
#report table{border-collapse:collapse;width:100%;font-size:12px}
#report td,#report th{border-bottom:1px solid var(--line);padding:4px 6px;text-align:left}
#report .close{position:absolute;top:10px;right:14px}
</style></head>
<body>
<header>
  <h1>幻想乡物语 · 指导书预览<small id="boot">载入中…</small></h1>
  <button id="save" class="primary">保存全部</button>
  <button id="regen" title="跑 tools/gen_ritual_book_entries.py，把正文里的 p1…pN 变成真实页面">重跑生成器</button>
  <button id="reload">重新载入</button>
  <button id="check">体检</button>
  <span id="dirty"></span>
</header>
<div id="banner">磁盘上的书文件已变化 —— 点这里重新载入（未保存的编辑会丢）。</div>
<div id="app">
  <div id="tree"><input id="search" placeholder="搜索条目 / 键 / 正文…"><div id="catlist"></div></div>
  <main id="main"></main>
  <aside id="inspector"></aside>
</div>
<div id="report"></div>
<div id="toast"></div>
<script>
"use strict";
const $ = (sel, root=document) => root.querySelector(sel);
const esc = (s) => String(s==null?"":s).replace(/[&<>"']/g, c => (
  {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));

/* ---------- 折行模型：与 tools/book_preview.py 的 wrap_text() 一一对应 ---------- */
const WIDE = /[\u1100-\u115f\u2e80-\ua4cf\uac00-\ud7a3\uf900-\ufaff\ufe30-\ufe4f\uff00-\uff60\uffe0-\uffe6\u3000-\u303f]/;
const NARROW = "il.,:;'!|";
const HALF = '[]()"*<>{}';
function charWidth(ch){
  if(!WIDE.test(ch)) return ch===' '?4:(NARROW.includes(ch)?2:(HALF.includes(ch)?4:6));
  return 9;
}
function flattenMacros(text){
  let out = String(text||"")
    .replace(/\$\(br2\)|\$\(p\)/g,"\n\n").replace(/\$\(br\)/g,"\n")
    .replace(/\$\(li2\)/g,"\n  \u2022 ").replace(/\$\(li\)/g,"\n\u2022 ")
    .replace(/\$\([^)]*\)/g,"").replace(/\\n/g,"\n");
  if(!/^\s*\$\(/.test(String(text||""))) out = out.replace(/^\n+/,"");
  return out;
}
function wrapText(text,width){
  width = width || 116;
  const lines = [];
  for(const para of flattenMacros(text).split("\n")){
    if(!para){ lines.push(""); continue; }
    let cur = [], curW = 0, lastSpace = -1;
    for(const ch of Array.from(para)){
      const w = charWidth(ch);
      if(curW + w > width && cur.length){
        let cut = cur.length;
        if(lastSpace > 0 && lastSpace >= Math.floor(cur.length/2)) cut = lastSpace;
        lines.push(cur.slice(0,cut).join("").replace(/\s+$/,""));
        const rest = cur.slice(cut).join("").replace(/^\s+/,"");
        cur = Array.from(rest);
        curW = cur.reduce((a,c)=>a+charWidth(c),0);
        lastSpace = -1;
      }
      if(ch === " ") lastSpace = cur.length;
      cur.push(ch); curW += w;
    }
    lines.push(cur.join("").replace(/\s+$/,""));
  }
  return lines;
}
function pageTop(page, index){
  const t = page.type;
  if(t === "patchouli:text") return index===0 ? 22 : (page.title ? 12 : 0);
  if(t === "patchouli:spotlight") return 40;
  return null;
}
function pageBudget(page, index){
  const top = pageTop(page, index);
  return top===null ? null : Math.max(0, Math.floor((156-top)/9));
}

/* ---------- 状态 ---------- */
let S = window.__STATIC_STATE__ || null;
const STATIC = !!window.__STATIC_STATE__;
let pending = {lang:{}, entries:{}};
let deletedKeys = new Set();
let cur = {file:null, page:0, edit:null, spread:0};
let mbLayer = {};
let query = "";
let overview = false;

function zh(k){ return S.lang.zh_cn[k]; }
function en(k){ return S.lang.en_us[k]; }
function langText(key){
  if(key==null || key==="") return "";
  if(deletedKeys.has(key)) return "⟨该键已被删除，保存后生效⟩";
  if(Object.prototype.hasOwnProperty.call(pending.lang,key)) return pending.lang[key];
  const t = zh(key); if(t!==undefined) return t;
  const e = en(key); if(e!==undefined) return e;
  return "⟨缺少文本键："+key+"⟩";
}
function rawEntry(file){
  if(pending.entries[file]) return pending.entries[file];
  const e = S.entries.find(x=>x.file===file);
  return e ? e.raw : null;
}
function entryMeta(file){ return S.entries.find(x=>x.file===file); }
function touch(file){
  if(!pending.entries[file]) pending.entries[file] = JSON.parse(JSON.stringify(rawEntry(file)));
  return pending.entries[file];
}
function dirtyCount(){
  const edited = Object.keys(pending.lang).filter(k=>!deletedKeys.has(k)).length;
  return edited + deletedKeys.size + Object.keys(pending.entries).length;
}
function markDirty(){
  const n = dirtyCount();
  $("#dirty").textContent = STATIC ? "只读模式" : (n ? ("● 未保存 "+n+" 处") : "");
  $("#save").disabled = STATIC || !n;
  $("#save").classList.toggle("primary", !STATIC && !!n);
}
function toast(msg, ms){
  const el = $("#toast"); el.textContent = msg; el.classList.add("show");
  clearTimeout(toast._t); toast._t = setTimeout(()=>el.classList.remove("show"), ms||1800);
}

/* ---------- 图标 ---------- */
function iconHTML(item, size){
  size = size || 32;
  if(!item) return `<span class="icontile" style="width:${size}px;height:${size}px;background:#5552">?</span>`;
  if(String(item).startsWith("#")){
    return `<span class="icontile" title="${esc(item)}" style="width:${size}px;height:${size}px;background:#7a5ea8">#</span>`;
  }
  const short = String(item).split(":").pop();
  return `<img class="icon" style="width:${size}px;height:${size}px" src="/tex?item=${encodeURIComponent(item)}"
    title="${esc(item)}" alt="" onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'icontile',textContent:this.title.split(':').pop().slice(0,3),style:'width:${size}px;height:${size}px;background:#7a5ea8',title:this.title}))">`;
}
function stackHTML(item, count, size){
  return `<span class="iconx" title="${esc(item)}${count>1?(" ×"+count):""}">${iconHTML(item,size||32)}${count>1?`<b>${count}</b>`:""}</span>`;
}

/* ---------- 页 ---------- */
function pageObj(page, index, entryRaw){
  const p = (typeof page === "string") ? {type:"patchouli:text", text:page} : page;
  return p;
}
function recipeFor(page){
  const list = S.recipesByPattern[page.ritual] || [];
  if(page.recipe){
    return S.recipeById[page.recipe] || list.find(r=>r._id===page.recipe) || null;
  }
  const idx = Number(page.recipe_index||0);
  return list[idx] || null;
}
function langOr(key, fallback){
  const t = langText(key);
  return (t && !t.startsWith("⟨")) ? t : fallback;
}
function recipeCard(page){
  if(!page.ritual) return `<div class="card"><div class="muted">未填写 ritual</div></div>`;
  const r = recipeFor(page);
  if(!r){
    return `<div class="card"><h4>配方卡</h4><div class="muted">找不到配方：
      ${esc(page.recipe||("#"+page.recipe_index))} @ ${esc(page.ritual)}</div></div>`;
  }
  const title = page.recipe ? (zh("jei.gensokyou.recipe."+String(r._id||"").split(":").pop()) || r.name || r._id)
                            : (r.name||r._id);
  const rows = [];
  const ing = r.ingredients||[];
  const perRow = 7;
  for(let i=0;i<Math.min(ing.length, perRow*2);i+=perRow){
    rows.push(`<div class="row">${ing.slice(i,i+perRow).map(g=>stackHTML(g.item||g.tag, g.count||1,24)).join("")}</div>`);
  }
  const out = r.result
    ? stackHTML(r.result.item, r.result.count||1, 32)
    : `<span class="muted">效果：${esc(r.effect||"—")}</span>`;
  return `<div class="card">
    <h4>${esc(title)}</h4>
    ${rows.join("")}
    <div class="row" style="margin-top:6px"><span class="muted">→</span>${out}</div>
    <div class="muted" style="margin-top:6px">灵力消耗 ${Number(r.spCost||0).toLocaleString()}
      · ${Number(r.minTier||0)} 阶仪式${r.minPlayerTier?(" · 玩家 "+r.minPlayerTier+" 阶"):""}</div>
  </div>`;
}
function tierCard(page){
  const list = S.recipesByPattern[page.ritual]||[];
  return `<div class="card">
    <h4>${Number(page.tier||0)} 阶 · 阶级参数</h4>
    <div class="muted">由 RitualTierComponent 在游戏内按 config 现算，这里只列来源。</div>
    <div class="muted" style="margin-top:6px">仪式：${esc(page.ritual||"—")}</div>
    <div class="muted">配方：${page.show_recipes===false?"不列出":"列出同阶配方（本仪式共 "+list.length+" 条）"}</div>
    <div class="muted">门槛：${esc(page.advancement||"无")}</div>
  </div>`;
}
function lootCard(page){
  const groups = {0:[],1:[],2:[],3:[],4:[]};
  String(page.entries||"").split(";").forEach(part=>{
    const f = part.split(",");
    if(f.length<3) return;
    const sec = Math.max(0, Math.min(4, parseInt(f[2])||0));
    groups[sec].push({item:f[0], pct:f[1]});
  });
  const labels = {0:"本池",1:"下界",2:"末地",3:"幻想乡·低",4:"幻想乡·高"};
  const single = page.mode==="watatsumi";
  let html = `<div class="card"><h4>${single?"海产 · 等级 "+esc(page.tier||0):"献祭产出"}</h4>`;
  if(page.tool_item) html += `<div class="row">${stackHTML(page.tool_item,1,24)}
    <span class="muted">作为献祭工具</span></div>`;
  const sections = single ? [0,1,2,3,4].filter(s=>groups[s].length) : [0,1,2,3,4].filter(s=>groups[s].length);
  if(!sections.length) html += `<div class="muted">entries 为空</div>`;
  sections.forEach(s=>{
    if(!single) html += `<div class="muted" style="margin-top:5px">${labels[s]}</div>`;
    html += `<div class="row">` + groups[s].map(e=>
      `<span title="${esc(e.item)} · ${esc(e.pct)}%">${iconHTML(e.item,24)}</span>`).join("") + `</div>`;
  });
  if(String(page.entries||"").includes(";")){
    html += `<div class="muted" style="margin-top:6px">共 ${Object.values(groups).reduce((a,g)=>a+g.length,0)} 项产物（悬停看概率）</div>`;
  }
  return html + `</div>`;
}
function multiblockCard(page, key){
  const mb = page.multiblock||{};
  const layers = mb.pattern||[];
  const mapping = mb.mapping||{};
  const layer = Math.min(layers.length-1, Math.max(0, mbLayer[key]||0));
  const grid = layers[layer]||[];
  const cols = grid.reduce((a,row)=>Math.max(a,row.length),0);
  const colorOf = (ch) => {
    if(ch===" "||ch===undefined) return "transparent";
    if(ch==="_") return "#00000018";
    if(ch==="0") return "#c9a227";
    let h = 0; const s = mapping[ch]||ch;
    for(let i=0;i<s.length;i++) h = (h*31 + s.charCodeAt(i))%360;
    return `hsl(${h} 45% ${ch===ch.toUpperCase()?58:48}%)`;
  };
  const cells = grid.map((row,r)=>Array.from({length:cols},(_,c)=>{
    const ch = row[c]||" ";
    return `<div class="mbcell" title="${esc(mapping[ch]||(ch===' '?'空':ch))}" style="background:${colorOf(ch)}"></div>`;
  }).join("")).join("");
  const legend = Object.entries(mapping).map(([ch,id])=>
    `<span><i style="background:${colorOf(ch)}"></i><b>${esc(ch)}</b> ${esc(id)}</span>`).join("");
  return `<div class="card"><h4>${esc(langOr(page.name, page.name||"结构"))}</h4>
    <div class="muted">${layers.length} 层 · 第 ${layer+1} 层（底→顶）
      ${mb.symmetrical?" · 对称":""}</div>
    <input type="range" min="0" max="${Math.max(0,layers.length-1)}" value="${layer}"
      oninput="mbLayer['${esc(key)}']=Number(this.value);render()">
    <div class="mbgrid" style="grid-template-columns:repeat(${cols},12px)">${cells}</div>
    <div class="mblegend">${legend}</div>
  </div>`;
}
function craftingCard(page){
  const r = S.crafting[page.recipe];
  if(!r) return `<div class="card"><h4>合成配方</h4><div class="muted">找不到 ${esc(page.recipe||"—")}</div></div>`;
  let inner = "";
  if(r.type === "minecraft:crafting_shaped" || r.type === "crafting_shaped"){
    const pattern = r.pattern||[];
    const key = r.key||{};
    inner = `<div style="display:inline-grid;grid-template-columns:repeat(${Math.max(...pattern.map(p=>p.length))},30px);gap:2px;background:#a99;padding:2px">` +
      pattern.map(row=>Array.from(row).map(ch=>{
        const cell = key[ch];
        const item = cell ? (cell.item || (Array.isArray(cell)?cell[0].item:"") ) : "";
        return `<div style="width:30px;height:30px;background:#8b7b7b;display:flex;align-items:center;justify-content:center">${item?iconHTML(item,24):""}</div>`;
      }).join("")).join("") + `</div>`;
    if(r.result) inner += `<div class="row" style="margin-top:6px"><span class="muted">→</span>${stackHTML(r.result.item, r.result.count||1, 32)}</div>`;
  } else {
    const ings = r.ingredients||[];
    inner = `<div class="row">${ings.map(g=>stackHTML(g.item||g.tag||"?", g.count||1, 24)).join("")}</div>`;
    if(r.result) inner += `<div class="row" style="margin-top:6px"><span class="muted">→</span>${stackHTML(r.result.item, r.result.count||1, 32)}</div>`;
  }
  return `<div class="card"><h4>合成配方</h4>${inner}
    <div class="muted" style="margin-top:4px">${esc(page.recipe)}</div></div>`;
}
function smeltCard(page){
  return `<div class="card"><h4>熔炼产物</h4>
    <div class="row">${stackHTML(page.result||"?",1,32)}</div>
    <div class="muted" style="margin-top:6px">仪式：${esc(page.ritual||"—")}</div></div>`;
}
function bossArrivalCard(page){
  return `<div class="card"><h4>抵达与信物</h4>
    ${page.token?`<div class="row">${stackHTML(page.token,1,32)}<span class="muted">主题信物</span></div>`:""}
    <div class="muted">召唤仪式：${esc(page.ritual||"—")} · 锚点：${esc(page.anchor||"—")}</div>
    ${page.recipe?recipeCard({ritual:page.ritual, recipe:page.recipe}):""}
  </div>`;
}
function genericCard(page){
  const bits = Object.entries(page).filter(([k])=>k!=="type")
    .map(([k,v])=>`<div class="muted">${esc(k)}：${esc(typeof v==="string"?v:JSON.stringify(v))}</div>`).join("");
  return `<div class="card"><h4>${esc(page.type)}</h4>${bits}</div>`;
}

/* ---------- 模板页：一页里摆多个组件 ---------- */
function tplVars(page){
  const out = {};
  Object.entries(page).forEach(([k,v])=>{
    if(k!=="type" && (typeof v==="string" || typeof v==="number")) out[k] = String(v);
  });
  return out;
}
function subVars(value, vars){
  if(typeof value !== "string") return value;
  return value.replace(/#([A-Za-z0-9_]+)#/g, (whole,key)=> vars[key]!==undefined ? vars[key] : whole);
}
function templateCard(page){
  const name = page.type.split(":")[1];
  const tpl = (S.templates||{})[name];
  if(!tpl) return genericCard(page);
  const vars = tplVars(page);
  const comps = (tpl.raw.components||[]).map(c=>{
    const type = subVars(c.type, vars);
    const x = Number(subVars(c.x===undefined?0:c.x, vars)) || 0;
    const y = Number(subVars(c.y===undefined?0:c.y, vars)) || 0;
    const style = `position:absolute;left:${x*3}px;top:${y*3}px`;
    if(type==="patchouli:item"){
      const item = subVars(c.item, vars);
      const size = c.scale ? Math.round(16*3*Number(subVars(c.scale,vars))) : 48;
      return `<div style="${style}">${iconHTML(item, size)}</div>`;
    }
    if(type==="patchouli:text" || type==="patchouli:header" || type==="patchouli:tooltip"){
      const text = esc(langText(subVars(c.text||"", vars)));
      const width = (Number(subVars(c.width===undefined?116:c.width, vars)) || 116) * 3;
      const header = type==="patchouli:header";
      return `<div style="${style};width:${width}px;font-size:${header?15:13}px;line-height:1.45;
        color:${header?"#4A2B6B":"#2A2430"};font-weight:${header?700:400}">${text}</div>`;
    }
    if(type==="patchouli:separator"){
      return `<div style="${style};width:348px;border-top:1px solid #b9ae91"></div>`;
    }
    if(type==="patchouli:custom"){
      const cls = subVars(c.class||"", vars);
      const inner = cls.endsWith("RitualTierComponent") ? tierCard(page)
        : cls.endsWith("RitualLootComponent") ? lootCard(page)
        : cls.endsWith("RitualSmeltComponent") ? smeltCard(page)
        : recipeCard(page);
      return `<div style="${style};width:348px">${inner}</div>`;
    }
    if(type==="patchouli:image"){
      const img = subVars(c.image||"", vars);
      return `<div style="${style}" class="muted">图片：${esc(img)}</div>`;
    }
    return `<div style="${style}" class="muted">${esc(type)}</div>`;
  }).join("");
  return `<div style="position:relative;height:442px;overflow:hidden">${comps}
    <div class="muted" style="position:absolute;bottom:0;left:0">模板 ${esc(page.type)}</div></div>`;
}

function linesHTML(text, budget, small){
  const lines = wrapText(text);
  const style = small ? ' style="font-size:16px;line-height:21px"' : "";
  const minh = small ? ' style="min-height:21px"' : "";
  return `<div class="lines"${style}>` + lines.map((ln,i)=>{
    const prev = (budget!==null && i===budget && lines.length>budget)
      ? `<div class="cutline"><span>页底 ${budget} 行</span></div>` : "";
    return prev + `<div class="ln${(budget!==null&&i>=budget)?" over":""}"${minh}>${esc(ln)||"&nbsp;"}</div>`;
  }).join("") + `</div>`;
}

function pageInner(page, index, entryRaw){
  const p = pageObj(page, index, entryRaw);
  const t = p.type;
  switch(t){
    case "patchouli:text": {
      const budget = pageBudget(p, index);
      const key = p.text||"";
      const editing = cur.edit === index;
      let html = p.title ? `<div class="pgtitle">${esc(langText(p.title))}</div>` : "";
      if(editing){
        html += `<textarea class="editor" id="txt" spellcheck="false">${esc(langText(key))}</textarea>`;
        html += `<div class="pgnote"><span class="mono">${esc(key||"（无 text 键）")}</span>
          <span id="live"></span></div>`;
      } else {
        const lines = wrapText(langText(key));
        const over = budget!==null && lines.length>budget;
        html += linesHTML(langText(key), budget, false);
        html += `<div class="pgnote"><span class="mono">${esc(key||"")}</span>
          <span class="${over?"bad":""}">${lines.length} / ${budget===null?"—":budget} 行</span></div>`;
      }
      return html;
    }
    case "patchouli:spotlight": {
      const budget = pageBudget(p, index);
      const editing = cur.edit === index;
      if(editing){
        return `<div class="card"><h4>聚焦</h4>
          <div class="row">${stackHTML(p.item,1,40)}</div>
          <textarea class="editor" id="txt" spellcheck="false" style="height:300px">${esc(langText(p.text))}</textarea>
          <div class="pgnote"><span class="mono">${esc(p.text||"")}</span><span id="live"></span></div>
        </div>`;
      }
      return `<div class="card"><h4>聚焦</h4>
        <div class="row">${stackHTML(p.item,1,48)}</div>
        ${linesHTML(langText(p.text), budget, true)}
        <div class="pgnote"><span class="mono">${esc(p.text||"")}</span>
          <span class="${(budget!==null&&wrapText(langText(p.text)).length>budget)?"bad":""}">
          ${wrapText(langText(p.text)).length} / ${budget===null?"—":budget} 行</span></div>
      </div>`;
    }
    case "patchouli:multiblock":
      return multiblockCard(p, entryRaw.file+":"+index);
    case "gensokyou:ritual_page": return recipeCard(p);
    case "gensokyou:ritual_tier_page": return tierCard(p);
    case "gensokyou:loot_page": return lootCard(p);
    case "gensokyou:smelt_page": return smeltCard(p);
    case "gensokyou:boss_arrival": return bossArrivalCard(p);
    case "patchouli:crafting": return craftingCard(p);
    case "patchouli:entity":
      return `<div class="card"><h4>实体</h4>
        <div class="row">${iconHTML("gensokyou:spawn_egg",40)}</div>
        <div class="muted" style="margin-top:6px">${esc(p.entity||"")} · 页内为可旋转 3D 模型</div></div>`;
    case "patchouli:empty":
      return `<div class="muted" style="text-align:center;margin-top:60px">（空白页）</div>`;
    default:
      if(page.type.startsWith("gensokyou:") && (S.templates||{})[page.type.split(":")[1]]) return templateCard(p);
      return genericCard(p);
  }
}

/* ---------- 书 ---------- */
function renderBook(){
  const meta = entryMeta(cur.file);
  if(!meta){ $("#main").innerHTML = `<div class="muted">左侧选一个条目</div>`; return; }
  const raw = rawEntry(cur.file);
  const pages = raw.pages||[];
  cur.page = Math.max(0, Math.min(pages.length-1, cur.page));
  cur.spread = Math.max(0, Math.min(pages.length-1, cur.spread));
  cur.spread -= cur.spread % 2;
  const nameKey = raw.name||"";
  const name = langText(nameKey);
  const gate = raw.advancement||"";
  const strip = pages.map((p,i)=>{
    const o = pageObj(p,i);
    const isText = o.type==="patchouli:text" || o.type==="patchouli:spotlight";
    const b = isText ? pageBudget(o,i) : null;
    const n = isText ? wrapText(langText(o.text)).length : null;
    const ovf = isText && b!==null && n>b;
    const low = isText && b!==null && n*2 < b;
    const short = {text:"文",spotlight:"焦",multiblock:"构",ritual_page:"方",
      ritual_tier_page:"阶",loot_page:"产",smelt_page:"炼",boss_arrival:"临",
      crafting:"合",entity:"体",empty:"空"}[o.type.split(":").pop()]||"页";
    return `<div class="p ${i===cur.page?"active":""} ${ovf?"ovf":""} ${low?"low":""}"
      draggable="true" ondragstart="dragStart(event,${i})" ondragover="dragOver(event)"
      ondrop="dropAt(event,${i})"
      title="第 ${i+1} 页 · ${esc(o.type)}${isText?(" · "+n+" / "+b+" 行"):""}${low?"（偏空）":""}${ovf?"（超页底）":""}"
      onclick="gotoPage(${i})">${short}</div>`;
  }).join("");
  const leftIndex = cur.spread, rightIndex = cur.spread+1;
  const htmlPage = (index) => {
    if(index>=pages.length) return overview ? "" : `<div class="page empty"></div>`;
    const p = pageObj(pages[index], index);
    const editable = p.type==="patchouli:text" || p.type==="patchouli:spotlight";
    return `<div class="page ${index===cur.page?"sel":""} ${cur.edit===index?"editing":""}"
      ${overview?`onclick="pickPage(${index})"`:""}>
      <div class="pt">${esc(p.type)}${p.advancement?(" · "+esc(p.advancement.split("/").pop())):""}</div>
      ${editable?`<div class="edit" title="编辑这一页的正文"
        onclick="event.stopPropagation();editPage(${index})">✎</div>`:""}
      <div class="body">${pageInner(pages[index], index, raw)}</div>
      <div class="pi">${index+1} / ${pages.length}</div>
    </div>`;
  };
  const body = overview
    ? `<div class="overview">${pages.map((_,i)=>htmlPage(i)).join("")}</div>`
    : `<div class="spread">${htmlPage(leftIndex)}${htmlPage(rightIndex)}</div>`;
  $("#main").innerHTML = `
    <div class="entryhead">
      <span class="title">${esc(name)}</span>
      <span class="chip">${esc(raw.category||"")}</span>
      <span class="chip">sortnum ${esc(raw.sortnum==null?"—":raw.sortnum)}</span>
      ${gate?`<span class="chip gate">🔓 ${esc(gate.split("/").pop())}</span>`:""}
      ${raw.secret?`<span class="chip secret">secret</span>`:""}
      ${meta.generated?`<span class="chip gen">生成器产出</span>`:""}
      <span class="chip">${pages.length} 页</span>
    </div>
    <div class="strip">${strip}</div>
    ${body}
    <div class="pager">
      <button onclick="step(-1)" ${overview || cur.spread<=0?"disabled":""}>← 上一铺</button>
      <span>第 ${cur.spread+1}–${Math.min(cur.spread+2, pages.length)} / ${pages.length} 页
        · 铺 ${Math.floor(cur.spread/2)+1}/${Math.ceil(pages.length/2)}</span>
      <button onclick="step(1)" ${overview || cur.spread+2>=pages.length?"disabled":""}>下一铺 →</button>
      <button onclick="toggleOverview()">${overview?"回到翻页":"整条纵览"}</button>
      <button onclick="toggleEdit()" ${overview?"disabled":""}>${cur.edit===cur.page?"完成编辑":"✎ 改本页"}</button>
    </div>`;
  if(cur.edit!==null){
    const ta = $("#txt");
    if(ta){
      ta.focus();
      ta.selectionStart = ta.value.length;
      ta.oninput = () => {
        const idx = cur.edit;
        const p = pageObj(pages[idx], idx);
        if(!p.text) return;
        pending.lang[p.text] = ta.value;
        const b = pageBudget(p, idx);
        const n = wrapText(ta.value).length;
        const live = $("#live");
        if(live) live.innerHTML = `<span class="${n>(b||999)?"bad":""}">${n} / ${b===null?"—":b} 行</span>`;
        markDirty();
      };
    }
  }
}

function gotoPage(i){
  i = Math.max(0, Math.min((rawEntry(cur.file).pages||[]).length-1, i));
  cur.page = i;
  cur.spread = i - (i % 2);   // Patchouli 的页铺：left = spread*2, right = spread*2+1
  cur.edit = null;
  render();
}
function pickPage(i){
  cur.page = i; cur.spread = i - (i % 2);
  render();
}
function toggleOverview(){ overview = !overview; cur.edit = null; render(); }
function editPage(i){
  cur.page = i;
  cur.edit = (cur.edit === i) ? null : i;
  // 编辑右页时不重排翻页位置，免得版面突然左右跳
  if(i < cur.spread) cur.spread = i - (i % 2);
  if(i > cur.spread+1) cur.spread = i - (i % 2);
  render();
}
function step(d){
  const last = (rawEntry(cur.file).pages||[]).length - 1;
  const target = cur.spread + 2*d;
  gotoPage(Math.max(0, Math.min(last, target)));
}
function toggleEdit(){
  const p = pageObj(rawEntry(cur.file).pages[cur.page], cur.page);
  if(p.type!=="patchouli:text" && p.type!=="patchouli:spotlight"){
    toast("只有文本页能就地改正文"); return;
  }
  cur.edit = (cur.edit === cur.page) ? null : cur.page;
  render();
}

/* ---------- 重排：合并 / 拖动排序 ---------- */
let dragFrom = null;
function dragStart(ev, i){
  dragFrom = i;
  ev.dataTransfer.effectAllowed = "move";
  try{ ev.dataTransfer.setData("text/plain", String(i)); }catch(e){}
}
function dragOver(ev){ ev.preventDefault(); }
function dropAt(ev, i){
  ev.preventDefault();
  if(dragFrom===null || dragFrom===i){ dragFrom = null; return; }
  const e = touch(cur.file);
  const moved = e.pages.splice(dragFrom, 1)[0];
  e.pages.splice(i, 0, moved);
  cur.page = i; cur.spread = i - (i % 2); cur.edit = null; dragFrom = null;
  markDirty(); render(); toast("已移动页面");
}
function hasText(key){
  if(!key || deletedKeys.has(key)) return false;
  return pending.lang[key]!==undefined || zh(key)!==undefined || en(key)!==undefined;
}

/* 掉落卡的页高估算，与 RitualLootComponent 的排版同口径（列 7、行 16、段标题 10） */
function lootHeight(page){
  let h = 6;
  const wat = page.mode === "watatsumi";
  if(wat) h += 10;
  if(page.tool_item) h += 18;
  const groups = {};
  String(page.entries||"").split(";").filter(Boolean).forEach(part=>{
    const f = part.split(",");
    if(f.length < 3) return;
    const sec = Math.max(0, Math.min(4, parseInt(f[2],10) || 0));
    (groups[sec] = groups[sec] || []).push(f[0]);
  });
  if(wat){
    const total = Object.values(groups).reduce((a,g)=>a+g.length,0);
    h += Math.ceil(total/7)*16;
  } else {
    [0,1,2,3,4].forEach(sec=>{
      const n = (groups[sec]||[]).length;
      if(!n) return;
      h += 10 + Math.ceil(n/7)*16 + 3;
    });
  }
  return h;
}

/* 检查相邻两页能否合并；能合则返回方案，否则返回 {reason} */
function mergePlan(i){
  const e = {pages: rawEntry(cur.file).pages || []};
  if(i < 0 || i+1 >= e.pages.length) return {reason:"没有相邻页"};
  const a = pageObj(e.pages[i], i), b = pageObj(e.pages[i+1], i+1);
  if((a.advancement||"") !== (b.advancement||"")) return {reason:"两页门槛不同"};
  if(a.type === "patchouli:text" && b.type === "patchouli:text"){
    if(!hasText(a.text) || !hasText(b.text)) return {reason:"有一页没有正文"};
    const budget = pageBudget(a, i);
    if(budget === null) return {reason:"这页没有文本区"};
    const merged = langText(a.text) + "$(br2)" + langText(b.text);
    const lines = wrapText(merged).length;
    if(lines > budget) return {reason:`合起来 ${lines} 行 > 上限 ${budget} 行`};
    const shared = e.pages.some((pg,k)=> k!==i && k!==i+1 && pageObj(pg,k).text === b.text);
    return {kind:"text", a, b, merged, lines, budget, dropKey: !shared};
  }
  if(a.type === "gensokyou:loot_page" && b.type === "gensokyou:loot_page"){
    if(a.mode !== b.mode || String(a.tier) !== String(b.tier) || (a.tool_item||"") !== (b.tool_item||""))
      return {reason:"两页的献祭工具/等级不同"};
    const ea = String(a.entries||"").split(";").filter(Boolean);
    const eb = String(b.entries||"").split(";").filter(Boolean);
    const plan = Object.assign({}, a, {entries: ea.concat(eb).join(";")});
    const height = lootHeight(plan);
    if(height > 150) return {reason:`合并后约 ${height}px 高 > 150px，会爬出页底`};
    return {kind:"loot", a, b, entries: plan.entries, height};
  }
  return {reason:"这两页不是可合并的类型（目前支持正文页与掉落页）"};
}

function applyMerge(i){
  const plan = mergePlan(i);
  if(!plan || plan.reason){ toast("合并不了：" + (plan ? plan.reason : "没有相邻页"), 3600); return false; }
  const e = touch(cur.file);
  if(plan.kind === "text"){
    pending.lang[plan.a.text] = plan.merged;
    if(plan.dropKey) deletedKeys.add(plan.b.text);
  } else if(plan.kind === "loot"){
    e.pages[i].entries = plan.entries;
  }
  e.pages.splice(i+1, 1);
  if(cur.page > i) cur.page -= 1;
  cur.spread = cur.page - (cur.page % 2);
  cur.edit = null;
  markDirty(); render();
  toast(plan.kind === "loot"
    ? `已合并成一页掉落卡（约 ${plan.height}px 高）`
    : `已合并（${plan.lines}/${plan.budget} 行）`);
  return true;
}
function mergeWithNext(){ applyMerge(cur.page); }
function mergeWithPrev(){ applyMerge(cur.page - 1); }
function mergeAllTextPages(){
  const e = touch(cur.file);
  let n = 0;
  for(let i=e.pages.length-2;i>=0;i--){
    const plan = mergePlan(i);
    if(plan && !plan.reason){ applyMerge(i); n++; }
  }
  if(!n){ toast("没有可合并的相邻页"); return; }
  cur.edit = null; markDirty(); render(); toast("合并了 "+n+" 处");
}

function renderTree(){
  const cats = {};
  S.entries.forEach(e=>{ (cats[e.raw.category] = cats[e.raw.category]||[]).push(e); });
  const q = query.trim().toLowerCase();
  let html = "";
  S.categories.forEach(cat=>{
    const list = (cats[cat.id]||[]).filter(e=>{
      if(!q) return true;
      const name = langText(nameOf(e)).toLowerCase();
      const raw = JSON.stringify(e.raw).toLowerCase();
      return name.includes(q) || e.file.toLowerCase().includes(q) || raw.includes(q);
    }).sort((a,b)=>sortnumOf(a)-sortnumOf(b));
    if(!list.length) return;
    const open = q || (cats[cat.id]||[]).some(e=>e.file===cur.file);
    html += `<details class="cat" ${open?"open":""}><summary>${esc(langText(cat.raw.name))}
      <span>${list.length}</span></summary>`;
    for(const e of list){
      html += `<div class="entry ${e.file===cur.file?"active":""}" onclick="selectEntry('${esc(e.file)}')">
        ${iconHTML(e.raw.icon,16)}
        <span class="nm">${esc(langText(nameOf(e)))}</span>
        ${advOf(e)?`<span class="tag">🔓</span>`:""}
        ${e.generated?`<span class="tag gen">G</span>`:""}
      </div>`;
    }
    html += `</details>`;
  });
  $("#catlist").innerHTML = html;
}
function nameOf(entry){ const p = pending.entries[entry.file]; return (p&&p.name!==undefined)?p.name:entry.raw.name; }
function sortnumOf(entry){ const p = pending.entries[entry.file]; return (p&&p.sortnum!==undefined)?Number(p.sortnum):Number(entry.raw.sortnum||0); }
function advOf(entry){ const p = pending.entries[entry.file]; return (p&&p.advancement!==undefined)?p.advancement:entry.raw.advancement; }

/* ---------- 右栏 ---------- */
function renderInspector(){
  const meta = entryMeta(cur.file);
  if(!meta){ $("#inspector").innerHTML = ""; return; }
  const raw = rawEntry(cur.file);
  const p = pageObj(raw.pages[cur.page], cur.page);
  const key = p.text || "";
  const isText = p.type==="patchouli:text" || p.type==="patchouli:spotlight";
  const budget = pageBudget(p, cur.page);
  const nLines = isText ? wrapText(langText(key)).length : null;
  const typeOptions = S.validPageTypes.map(t=>`<option ${t===p.type?"selected":""}>${t}</option>`).join("");
  const insertOptions = Object.keys(S.newPageDefaults).map(t=>`<option>${t}</option>`).join("");

  $("#inspector").innerHTML = `
    <h3>条目</h3>
    <div class="kv">
      <label>名称键</label><input value="${esc(raw.name||"")}" onchange="setEntryField('name',this.value)">
      <label>显示</label><span class="muted">${esc(langText(raw.name))}</span>
      <label>分类</label>
      <select onchange="setEntryField('category',this.value)">
        ${S.categoryIds.map(c=>`<option ${c===raw.category?"selected":""}>${esc(c)}</option>`).join("")}
      </select>
      <label>sortnum</label><input type="number" value="${esc(raw.sortnum==null?"0":raw.sortnum)}"
        onchange="setEntryField('sortnum',Number(this.value))">
      <label>advancement</label><input value="${esc(raw.advancement||"")}"
        placeholder="gensokyou:guide/nether_unlock" onchange="setEntryField('advancement',this.value||undefined)">
      <label>secret</label><input type="checkbox" ${raw.secret?"checked":""}
        onchange="setEntryField('secret',this.checked||undefined)">
      <label>图标</label><input value="${esc(raw.icon||"")}" onchange="setEntryField('icon',this.value)">
    </div>
    ${meta.generated?`<div class="warn">这个条目由 <b>tools/gen_ritual_book_entries.py</b> 生成：
      正文可以改，结构改动不会写回（会被下次生成覆盖）。</div>`:""}
    <div class="btnrow">
      <button onclick="saveAll()" ${meta.generated?"disabled":""}>保存本条目结构</button>
    </div>

    <h3>当前页 · 第 ${cur.page+1} / ${(raw.pages||[]).length} 页</h3>
    <div class="kv">
      <label>页型</label>
      <select onchange="setPageType(this.value)">${typeOptions}</select>
      <label>门槛</label><input value="${esc(p.advancement||"")}"
        onchange="setPageField('advancement',this.value||undefined)">
    </div>
    ${isText?`
      <h3>正文</h3>
      <code class="key">${esc(key||"（该页没有 text 键）")}</code>
      <div class="btnrow">
        <button onclick="copyKey()">复制键</button>
        <button onclick="focusKey()">在 lang 里新建/定位</button>
      </div>
      <div class="muted">${nLines===null?"—":nLines} 行 / 上限 ${budget===null?"—":budget} 行
        ${(nLines!==null&&budget!==null&&nLines>budget)
          ?'<span style="color:#e05252">· 超页底（游戏内会整页缩字）</span>':""}
        ${(nLines!==null&&budget!==null&&nLines*2<budget)
          ?'<span style="color:#c9a227">· 偏空（可考虑与邻页合并）</span>':""}</div>
    `:""}
    <h3>重排</h3>
    <div class="btnrow">
      <button onclick="splitAtCaret()">在光标处分页</button>
      <button onclick="splitPage()">自动拆到不溢出</button>
    </div>
    <div class="btnrow">
      <button onclick="mergeWithPrev()">与上一页合并</button>
      <button onclick="mergeWithNext()">与下一页合并</button>
      <button onclick="mergeAllTextPages()">整条自动合并</button>
    </div>
    <p class="muted">合并按<b>页序</b>做，不看屏幕左右——第 2 页和第 3 页跨铺也能合：<br>
      点第 2 页（页条或 ✎）→「与下一页合并」；或点第 3 页 →「与上一页合并」。<br>
      正文页要求同门槛且合起来不超页；掉落卡要求同工具/等级且合并后不超 150px。<br>
      「光标处分页」：先点 ✎ 进编辑，把光标放到要断开的位置。</p>
    <h3>页 JSON</h3>
    <textarea class="json" id="pagejson" spellcheck="false">${esc(JSON.stringify(p,null,2))}</textarea>
    <div class="btnrow">
      <button onclick="applyPageJson()">应用 JSON</button>
      <button onclick="movePage(-1)">上移</button>
      <button onclick="movePage(1)">下移</button>
      <button onclick="duplicatePage()">复制</button>
      <button onclick="deletePage()">删除</button>
    </div>
    <h3>插入页</h3>
    <div class="btnrow">
      <select id="newtype">${insertOptions}</select>
      <button onclick="insertPage()">插到当前页后</button>
    </div>
    <p class="muted">快捷键：← / → 翻页，Ctrl+S 保存全部。</p>`;
}

function setEntryField(field, value){
  const e = touch(cur.file);
  if(value===undefined) delete e[field]; else e[field] = value;
  markDirty(); render();
}
function setPageField(field, value){
  const e = touch(cur.file);
  const p = e.pages[cur.page];
  if(value===undefined) delete p[field]; else p[field] = value;
  markDirty(); render();
}
function setPageType(type){
  const e = touch(cur.file);
  const old = e.pages[cur.page] || {};
  const fresh = JSON.parse(JSON.stringify(S.newPageDefaults[type] || {type}));
  if(old.advancement) fresh.advancement = old.advancement;
  e.pages[cur.page] = fresh;
  markDirty(); render();
}
function applyPageJson(){
  try{
    const parsed = JSON.parse($("#pagejson").value);
    const e = touch(cur.file);
    const old = e.pages[cur.page];
    if(parsed.advancement===undefined && old && old.advancement) parsed.advancement = old.advancement;
    e.pages[cur.page] = parsed;
    markDirty(); render(); toast("已应用到内存，记得保存");
  }catch(err){ toast("JSON 解析失败："+err.message, 3000); }
}
function movePage(delta){
  const e = touch(cur.file);
  const i = cur.page, j = i+delta;
  if(j<0||j>=e.pages.length) return;
  const tmp = e.pages[i]; e.pages[i] = e.pages[j]; e.pages[j] = tmp;
  cur.page = j; cur.spread = j - (j % 2); markDirty(); render();
}
function duplicatePage(){
  const e = touch(cur.file);
  const copy = JSON.parse(JSON.stringify(e.pages[cur.page]));
  e.pages.splice(cur.page+1, 0, copy);
  cur.page += 1; cur.spread = cur.page - (cur.page % 2); markDirty(); render();
}
function deletePage(){
  const e = touch(cur.file);
  if(e.pages.length<=1){ toast("至少留一页"); return; }
  e.pages.splice(cur.page,1);
  cur.page = Math.max(0, cur.page-1); cur.spread = cur.page - (cur.page % 2); markDirty(); render();
}
function insertPage(){
  const type = $("#newtype").value;
  const e = touch(cur.file);
  const page = JSON.parse(JSON.stringify(S.newPageDefaults[type] || {type}));
  e.pages.splice(cur.page+1, 0, page);
  cur.page += 1; cur.spread = cur.page - (cur.page % 2); markDirty(); render();
}
function copyKey(){
  const p = pageObj(rawEntry(cur.file).pages[cur.page], cur.page);
  navigator.clipboard.writeText(p.text||"").then(()=>toast("已复制"));
}
function focusKey(){
  const p = pageObj(rawEntry(cur.file).pages[cur.page], cur.page);
  if(!p.text){ toast("该页没有 text 键"); return; }
  deletedKeys.delete(p.text);
  if(!(p.text in pending.lang)) pending.lang[p.text] = langText(p.text);
  markDirty(); cur.edit = cur.page; render();
}

/* 把一页超页底的正文按 $(br2) 段落贪心装箱，拆成 .p1/.p2… 多页 */
/* 把正文装进若干页：段落 → 句子 → 逐字回退，尽量塞满每页。
   返回 {chunks: [[atom...]], join(arr)}，join 负责还原 $(br2) 分段。 */
function packText(text, budget){
  const paras = String(text||"").split(/\$\(br2\)|\$\(p\)/).map(s=>s.trim()).filter(Boolean);
  const atoms = [];
  for(const para of paras){
    const sentences = para.split(/(?<=[。！？；!?;])/).map(s=>s.trim()).filter(Boolean);
    const list = sentences.length ? sentences : [para];
    list.forEach((seg,i)=>{
      if(wrapText(seg).length <= budget){ atoms.push({t:seg, brk:i===0}); return; }
      let rest = seg;
      while(rest){
        let lo = 1, hi = rest.length, best = 1;
        while(lo <= hi){
          const mid = (lo+hi) >> 1;
          if(wrapText(rest.slice(0,mid)).length <= budget){ best = mid; lo = mid+1; }
          else hi = mid-1;
        }
        atoms.push({t:rest.slice(0,best), brk:false});
        rest = rest.slice(best);
      }
    });
  }
  const join = (arr)=>arr.map((a,i)=> ((a.brk && i>0) ? "$(br2)" : "") + a.t).join("");
  const chunks = []; let acc = [];
  for(const atom of atoms){
    const test = acc.concat([atom]);
    if(acc.length && wrapText(join(test)).length > budget){ chunks.push(acc); acc = [atom]; }
    else acc = test;
  }
  if(acc.length) chunks.push(acc);
  return {chunks, atoms, join};
}

/* 把贪心结果在同等页数内重新均分，避免末页只剩两三行 */
function repackBalanced(atoms, join, budget, pages){
  if(pages < 2) return null;
  const total = wrapText(join(atoms)).length;
  const lower = Math.max(1, Math.ceil(total / pages) - 2);
  for(let cap = lower; cap <= budget; cap++){
    const chunks = []; let acc = [];
    for(const atom of atoms){
      const test = acc.concat([atom]);
      if(acc.length && wrapText(join(test)).length > cap){ chunks.push(acc); acc = [atom]; }
      else acc = test;
    }
    if(acc.length) chunks.push(acc);
    if(chunks.length <= pages) return chunks;
  }
  return null;
}

/* 为一组新页分配键：`.text` → p1..pN（原键删除）；`.pK` → 保留原键，后续从 K+1 起。 */
function allocKeys(e, key, count){
  const base = key.replace(/\.(text|p\d+)$/, "");
  const numbered = key.match(/\.p(\d+)$/);
  const used = new Set();
  e.pages.forEach(pg=>{ const q = pageObj(pg,0); if(q.text) used.add(q.text); });
  const names = [];
  let next = 1;
  if(numbered){ names.push(key); next = Number(numbered[1]) + 1; }
  for(let i=names.length;i<count;i++){
    let cand;
    do { cand = base + ".p" + next++; } while(used.has(cand));
    used.add(cand); names.push(cand);
  }
  return {names, dropOriginal: !numbered};
}

/* 在编辑器光标处把当前页切成两页（长段落也能拆） */
function splitAtCaret(){
  const ta = document.querySelector('#txt');
  if(!ta || cur.edit===null){ toast("先点 ✎ 进入编辑，再放光标"); return; }
  const idx = cur.edit, e = touch(cur.file), p = pageObj(e.pages[idx], idx);
  if(!p.text){ toast("这页没有 text 键"); return; }
  const value = ta.value;
  const caret = ta.selectionStart;
  const head = value.slice(0, caret).replace(/\s+$/,"");
  const tail = value.slice(caret).replace(/^\s+/,"");
  if(!head || !tail){ toast("把光标放在要断开的位置（两侧都要有内容）", 2800); return; }
  const {names, dropOriginal} = allocKeys(e, p.text, 2);
  pending.lang[names[0]] = head;
  pending.lang[names[1]] = tail;
  if(dropOriginal) deletedKeys.add(p.text);
  const np = JSON.parse(JSON.stringify(e.pages[idx]));
  np.text = names[1];
  if(names[0] !== p.text) e.pages[idx].text = names[0];
  e.pages.splice(idx+1, 0, np);
  cur.page = idx; cur.spread = idx - (idx % 2); cur.edit = idx;
  const hb = pageBudget(pageObj(e.pages[idx], idx), idx);
  const tb = pageBudget(pageObj(e.pages[idx+1], idx+1), idx+1);
  const hl = wrapText(head).length, tl = wrapText(tail).length;
  const warn = (hb && hl > hb) || (tb && tl > tb);
  markDirty(); render();
  const genHint = (entryMeta(cur.file)||{}).generated
    ? "　⚠ 这是生成器条目：保存后要点「重跑生成器」才会产出新页" : "";
  toast(warn
    ? `已拆开，但两半仍超页底（${hl}/${hb}、${tl}/${tb} 行），继续拆或删字` + genHint
    : `已从光标处拆成两页（${names[0]} / ${names[1]}）` + genHint, warn ? 5200 : 4600);
}

function splitPage(){
  const e = touch(cur.file);
  const p = pageObj(e.pages[cur.page], cur.page);
  const key = p.text||"";
  const text = langText(key);
  const budget = pageBudget(p, cur.page);
  if(!key || !budget){ toast("这页没有可拆的正文"); return; }
  const {chunks: greedy, atoms, join} = packText(text, budget);
  const chunks = repackBalanced(atoms, join, budget, greedy.length) || greedy;
  if(chunks.length < 2){ toast("这页没超，不用拆"); return; }
  const {names, dropOriginal} = allocKeys(e, key, chunks.length);
  if(!confirm("拆成 "+chunks.length+" 页：\n"+names.join("\n")+
    (dropOriginal ? "\n\n原键 "+key+" 会被删除（几段全部改名为 .p1…）" : "\n\n第一页沿用原键 "+key))) return;
  chunks.forEach((c,i)=>{ pending.lang[names[i]] = join(c); });
  if(dropOriginal) deletedKeys.add(key);
  e.pages[cur.page].text = names[0];
  for(let i=1;i<chunks.length;i++){
    const np = JSON.parse(JSON.stringify(e.pages[cur.page]));
    np.text = names[i];
    e.pages.splice(cur.page+i, 0, np);
  }
  cur.edit = null; cur.spread = cur.page - (cur.page % 2);
  markDirty(); render();
  const genHint = (entryMeta(cur.file)||{}).generated
    ? "　⚠ 生成器条目：保存后点「重跑生成器」才会产出新页" : "";
  toast("已拆成 "+chunks.length+" 页，检查后保存" + genHint, genHint ? 6000 : 2600);
}

/* ---------- 保存 / 载入 ---------- */
async function regenerate(){
  if(STATIC){ toast("只读预览"); return; }
  if(dirtyCount()){
    if(!confirm("还有未保存的编辑。先保存再重跑生成器？\n（取消 = 不重跑）")) return;
    await saveAll();
  }
  // 只重跑当前仪式条目，避免生成器把其它条目的手工改动冲掉
  const ritual = cur.file.startsWith("ritual_") ? cur.file.slice(7, -5) : null;
  const scope = ritual ? `仪式 ${ritual}` : "全部条目（会重写所有生成条目，含手工改过的！）";
  if(!confirm(`重跑生成器：${scope}\n继续？`)) return;
  $("#regen").disabled = true;
  try{
    const r = await fetch("/api/regenerate", {method:"POST",
      headers:{"Content-Type":"application/json"},
      body:JSON.stringify({only: ritual})}).then(r=>r.json());
    const tail = (r.output||"").trim().split(/\r?\n/).slice(-6).join(" / ");
    if(!r.ok){ toast("生成器失败：" + tail, 6000); return; }
    await reload(true);
    toast("已重跑生成器并重新载入", 2600);
  }catch(err){ toast("重跑失败："+err.message, 4000); }
  finally{ $("#regen").disabled = false; }
}

async function saveAll(){
  if(STATIC){ toast("只读预览，不能保存"); return; }
  $("#save").disabled = true;
  const skipped = [];
  try{
    const updates = {};
    Object.keys(pending.lang).forEach(k=>{ if(!deletedKeys.has(k)) updates[k] = pending.lang[k]; });
    const deletes = Array.from(deletedKeys);
    if(Object.keys(updates).length || deletes.length){
      const r = await fetch("/api/save-lang",{method:"POST",
        headers:{"Content-Type":"application/json"},
        body:JSON.stringify({updates, deletes})}).then(r=>r.json());
      if(!r.ok) throw new Error(r.error||"保存正文失败");
    }
    for(const [file, entry] of Object.entries(pending.entries)){
      const meta = entryMeta(file);
      if(meta && meta.generated){ skipped.push(file); continue; }
      const r = await fetch("/api/save-entry",{method:"POST",
        headers:{"Content-Type":"application/json"},
        body:JSON.stringify({file, entry})}).then(r=>r.json());
      if(!r.ok) throw new Error(r.error||("保存 "+file+" 失败"));
    }
    pending = {lang:{}, entries:{}};
    deletedKeys = new Set();
    await reload(true);
    if(!skipped.length){ toast("已保存"); return; }

    // 生成器条目的页结构只能由生成器产出：正文存完后主动问要不要立刻重跑
    const rituals = skipped.filter(f=>f.startsWith("ritual_")).map(f=>f.slice(7,-5));
    const rest = skipped.filter(f=>!f.startsWith("ritual_"));
    let note = "";
    if(rituals.length){
      const go = confirm(`正文已保存。\n${rituals.length} 个生成器条目的页结构还没跟上新正文（${rituals.join("、")}）。\n\n现在重跑生成器？`);
      if(go){
        try{
          for(const name of rituals){
            const r = await fetch("/api/regenerate",{method:"POST",
              headers:{"Content-Type":"application/json"},
              body:JSON.stringify({only:name})}).then(r=>r.json());
            if(!r.ok) throw new Error((r.output||"").trim().split(/\r?\n/).slice(-3).join(" / "));
          }
          await reload(true);
          note = `已保存，并按新正文重排了 ${rituals.length} 个仪式条目`;
        }catch(err){
          note = "正文已保存，但重跑生成器失败：" + err.message;
        }
      }else{
        note = "正文已保存；页结构未更新（可点顶部「重跑生成器」补上）";
      }
    }else{
      note = `正文已保存；${skipped.length} 个生成条目的结构未写回`;
    }
    if(rest.length) note += `；另有 ${rest.length} 个非仪式生成条目需手动处理：${rest.join("、")}`;
    toast(note, 7000);
  }catch(err){ toast(err.message, 4000); markDirty(); }
}
async function reload(silent){
  if(STATIC){ toast("只读预览"); return; }
  const keep = {file:cur.file, page:cur.page};
  S = await fetch("/api/state").then(r=>r.json());
  cur.file = (S.entries.find(e=>e.file===keep.file)||S.entries[0]||{}).file;
  cur.page = keep.page; cur.spread = keep.page - (keep.page % 2); cur.edit = null;
  pending = {lang:{}, entries:{}};
  deletedKeys = new Set();
  if(!silent) toast("已重新载入");
  render();
}
function selectEntry(file){ cur = {file, page:0, edit:null, spread:0}; render(); }
function jumpTo(file, page){
  selectEntry(file);
  gotoPage(page);
  $("#report").style.display = "none";
}
function render(){ renderTree(); renderBook(); renderInspector(); markDirty(); }

/* ---------- 体检 ---------- */
function sparsePages(){
  const out = [];
  S.entries.forEach(e=>{
    const raw = rawEntry(e.file);
    (raw.pages||[]).forEach((pg,i)=>{
      const p = pageObj(pg,i);
      if(p.type!=="patchouli:text" || !p.text) return;
      const b = pageBudget(p,i); if(!b) return;
      const n = wrapText(langText(p.text)).length;
      if(n*2 < b) out.push([e.file,i,n,b]);
    });
  });
  return out.sort((a,b)=>(a[2]/a[3])-(b[2]/b[3]));
}
function showReport(){
  const st = S.stats;
  const rows = [];
  st.types && Object.entries(st.types).forEach(([k,v])=>rows.push(`<tr><td class="mono">${esc(k)}</td><td>${v}</td></tr>`));
  const overflow = st.overflow.map(([f,i,n,b])=>
    `<tr><td class="mono">${esc(f)}</td><td>第 ${i+1} 页</td><td>${n} 行</td><td>${b} 行</td>
     <td><button onclick="jumpTo('${esc(f)}',${i})">去看</button></td></tr>`).join("");
  const missing = st.missing.map(([f,w,k])=>
    `<tr><td class="mono">${esc(f)}</td><td>${esc(w)}</td><td class="mono">${esc(k)}</td></tr>`).join("");
  const orphans = st.orphans.map(k=>`<tr><td class="mono">${esc(k)}</td><td>
    <button onclick="newKey('${esc(k)}')">新建页引用它</button></td></tr>`).join("");
  const sparse = sparsePages();
  const sparseRows = sparse.slice(0,80).map(([f,i,n,b])=>
    `<tr><td class="mono">${esc(f)}</td><td>第 ${i+1} 页</td><td>${n} / ${b} 行</td>
     <td><button onclick="jumpTo('${esc(f)}',${i})">去看</button></td></tr>`).join("");
  $("#report").innerHTML = `<button class="close" onclick="\$('#report').style.display='none'">关闭</button>
    <h2>体检</h2>
    <p>条目 ${st.entries} · 页面 ${st.pages} · 文本页 ${st.textPages} · 引用正文键 ${st.referencedKeys.length} /
      lang 书相关键 ${st.bookKeys}</p>
    <h3>页型分布</h3><table>${rows.join("")}</table>
    <h3 style="color:#ff9aa6">溢出页 ${st.overflow.length}</h3>
    <table>${overflow || "<tr><td>没有</td></tr>"}</table>
    <h3 style="color:#c9a227">偏空页 ${sparse.length}（不到半页，可与邻页合并）</h3>
    <table>${sparseRows || "<tr><td>没有</td></tr>"}</table>
    <h3 style="color:#ff9aa6">缺键 ${st.missing.length}</h3>
    <table>${missing || "<tr><td>没有</td></tr>"}</table>
    <h3 style="color:#c9a227">孤儿键 ${st.orphans.length}</h3>
    <table>${orphans || "<tr><td>没有</td></tr>"}</table>
    <h3>Java 组件键 ${st.componentKeys.length}（由代码直接取用，不算孤儿）</h3>
    <div class="muted mono">${st.componentKeys.map(esc).join("　")}</div>`;
  $("#report").style.display = "block";
}
function newKey(key){
  $("#report").style.display = "none";
  const e = touch(cur.file);
  e.pages.splice(cur.page+1, 0, {type:"patchouli:text", text:key});
  deletedKeys.delete(key);
  pending.lang[key] = "";
  cur.page += 1; cur.spread = cur.page - (cur.page % 2); cur.edit = cur.page;
  markDirty(); render(); toast("已插入引用该键的文本页，写正文后保存");
}

/* ---------- 事件 ---------- */
$("#save").onclick = saveAll;
$("#regen").onclick = regenerate;
$("#reload").onclick = ()=>reload(false);
$("#check").onclick = showReport;
$("#search").oninput = (ev)=>{ query = ev.target.value; renderTree(); };
$("#banner").onclick = ()=>{ if(dirtyCount() && !confirm("有未保存的编辑，确定重新载入？")) return; reload(false); };
document.addEventListener("keydown", (e)=>{
  if((e.ctrlKey||e.metaKey) && e.key.toLowerCase()==="s"){ e.preventDefault(); saveAll(); return; }
  if(e.target && /INPUT|TEXTAREA|SELECT/.test(e.target.tagName)) return;
  if(e.key==="ArrowRight"){ step(1); e.preventDefault(); }
  if(e.key==="ArrowLeft"){ step(-1); e.preventDefault(); }
  if(e.key==="e" || e.key==="E"){ toggleEdit(); e.preventDefault(); }
});
setInterval(async ()=>{
  if(!S) return;
  try{
    const r = await fetch("/api/rev").then(r=>r.json());
    if(r.rev !== S.rev){ $("#banner").classList.add("show"); }
  }catch(e){}
}, 4000);

(async function boot(){
  try{
    if(STATIC){ $("#save").style.display = "none"; $("#reload").style.display = "none"; }
    if(!S) S = await fetch("/api/state").then(r=>r.json());
    $("#boot").textContent = S.stats.entries+" 个条目 · "+S.stats.pages+" 页";
    const hash = decodeURIComponent(location.hash.replace(/^#/,""));
    cur.file = (S.entries.find(e=>e.file===hash) || S.entries[0] || {}).file;
    render();
  }catch(err){
    $("#main").innerHTML = `<div class="warn">载入失败：${esc(err.message)}</div>`;
  }
})();
</script></body></html>
"""


# ---------------------------------------------------------------- 入口

def serve(port, open_browser):
    try:
        server = QuietServer(("127.0.0.1", port), Handler)
    except OSError as exc:
        raise SystemExit(f"端口 {port} 起不来（{exc}）；换一个：--port 8778")
    actual = server.server_address[1]
    url = f"http://127.0.0.1:{actual}/"
    print(f"指导书预览：{url}")
    print("  Ctrl+C 结束；正文改完点「保存全部」（或 Ctrl+S）直接写回 zh_cn.json。")
    if open_browser:
        threading.Timer(0.4, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
    finally:
        server.server_close()


def write_static():
    state = load_state()
    BUILD.mkdir(parents=True, exist_ok=True)
    out = BUILD / "preview.html"
    html = PAGE_HTML.replace(
        "<script>", "<script>window.__STATIC_STATE__ = "
        + json.dumps(state, ensure_ascii=False).replace("</", "<\\/")
        + ";</script>\n<script>", 1)
    out.write_text(html, encoding="utf-8")
    print(f"已导出 {out}（只读，双击打开）")


def main():
    # 管道输出时按 UTF-8 编码，否则 Windows 会用 ANSI 代码页，中文在 agent/日志里变乱码。
    for stream in (sys.stdout, sys.stderr):
        if not stream.isatty():
            try:
                stream.reconfigure(encoding="utf-8")
            except Exception:
                pass
    parser = argparse.ArgumentParser(description="指导书离线预览 / 编辑台")
    parser.add_argument("--port", type=int, default=8777)
    parser.add_argument("--check", action="store_true", help="只做体检")
    parser.add_argument("--strict", action="store_true", help="体检发现问题时退出码 1")
    parser.add_argument("--static", action="store_true", help="导出只读 HTML")
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()
    if args.check:
        run_check(args.strict)
    elif args.static:
        write_static()
    else:
        serve(args.port, not args.no_browser)


if __name__ == "__main__":
    main()
