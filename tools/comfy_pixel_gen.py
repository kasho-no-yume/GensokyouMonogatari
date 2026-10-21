"""comfy_pixel_gen.py - ComfyUI 驱动的像素贴图生成 / 重绘管线。

本工具补上 ASCII 画法（tools/gen_tex.py）做不到的部分：**大面积、复杂、
有机轮廓**的贴图（信物、符卡造物、灵器…）。ASCII 适合几何规整的小图标，
但画不出「御札上挥毫的朱砂笔触」这类内容。

管线（与 ComfyUI 里已有的 "zimage minecraft item" 工作流同源）：

    Z-Image Turbo（int8 convrot）+ 像素 LoRA(adapter_model)  → 1024px 像素画
        ↓ BiRefNet 抠图
    透明底 RGBA
        ↓ 裁切到 alpha 包围盒（主体占满画布）
    ↓ 预乘 alpha 的 LANCZOS 缩放（避免边缘黑边）
    目标像素栅格（64×64 / 32×32 / 16×16）
        ↓ alpha 二值化 + 调色板量化（MEDIANCUT，无抖动）
    成品 PNG

规格文件格式（默认 tools/textures/ai_items.py）：

    ITEMS = {
        "item/core_talisman": {
            "prompt": "pxlstl, pixel art, ...",
            "seed": 20251021,     # 省略则用文件名哈希（可复现）
            "size": 64,           # 目标像素栅格边长，默认 64
            "colors": 24,         # 量化色数，0 = 不量化
            "negative": "...",    # 可选，覆盖默认负面提示
        },
    }

用法：

    python tools/comfy_pixel_gen.py --spec tools/textures/ai_items.py
    python tools/comfy_pixel_gen.py --spec tools/textures/ai_items.py --only item/yen
    python tools/comfy_pixel_gen.py --spec tools/textures/ai_items.py --write-assets
    python tools/comfy_pixel_gen.py --spec tools/textures/ai_items.py --montage

只读 ComfyUI 的 HTTP API，不碰 models 目录；预览默认写到 tools/textures/_ai/，
只有显式 --write-assets 才会覆盖 src/main/resources 下的正式贴图。
"""
import argparse
import hashlib
import importlib.util
import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
TEX_DIR = Path(__file__).resolve().parent / "textures"
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures"
PREVIEW = TEX_DIR / "_ai"

# ---------------------------------------------------------------- ComfyUI 常量

UNET = "z_image_turbo_int8_convrot.safetensors"
CLIP = "qwen3-4b-heretic.safetensors"
VAE = "ae.safetensors"
LORA = "adapter_model.safetensors"          # Z-Image 像素风 LoRA（rank 16）
BG_REMOVAL = "birefnet.safetensors"

GEN_SIZE = 1024        # Z-Image Turbo 原生分辨率
# 「每个目标 texel 对应多少源像素」的经验值：像素 LoRA 生成的像素栅格块边长约 16px，
# 所以按 size * 16 反推生成分辨率，缩放时正好落在栅格线上，边缘不会糊。
# 16x16 -> 256 生成（勉强可用）；64x64 -> 1024 生成（清晰）。
GEN_PIXELS_PER_TEXEL = 16
STEPS = 4              # Turbo 模型 4 步即收敛
CFG = 1.0
SAMPLER = "res_multistep"
SCHEDULER = "simple"
SHIFT = 3.0
LORA_STRENGTH = 0.8

# 负面提示：把「写实 / 抗锯齿 / 文字」全部挡掉。像素画必须硬边、无字。
NEGATIVE = (
    "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
    "anti-aliased edges, detailed background, scenery, environment, person, human, "
    "hand, arm, character, multiple objects, tiny object, small object, distant object, "
    "excessive empty space, text, letters, watermark, signature, ui, border, frame, "
    "3d render, octane, unreal engine, blurry, jpeg artifacts"
)

# 正面提示的公共尾巴：像素画风格 + 构图要求（沿用本机工作流的写法）
PROMPT_TAIL = (
    "hard pixel edges, discrete square pixels, limited color palette, flat color blocks, "
    "crisp pixel clusters, no smooth gradients, no anti-aliasing, no realistic texture, "
    "single object, isolated, centered, the object fills 85 to 90 percent of the canvas, "
    "extremely close framing, minimal margin, solid dark charcoal gray background"
)

# ---------------------------------------------------------------- HTTP 小工具


def _post(base, path, payload, timeout=120):
    req = urllib.request.Request(
        base + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def _get(base, path, timeout=120):
    with urllib.request.urlopen(base + path, timeout=timeout) as r:
        return json.loads(r.read())


def _get_bytes(base, path, timeout=120):
    with urllib.request.urlopen(base + path, timeout=timeout) as r:
        return r.read()


def build_workflow(prompt, negative, seed, lora=LORA, lora_strength=LORA_STRENGTH,
                   gen_size=GEN_SIZE):
    """Z-Image Turbo + 像素 LoRA + BiRefNet 抠图，返回 API 格式的工作流。"""
    return {
        "1": {"class_type": "UNETLoader",
              "inputs": {"unet_name": UNET, "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader",
              "inputs": {"clip_name": CLIP, "type": "lumina2", "device": "default"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": VAE}},
        "4": {"class_type": "LoraLoader",
              "inputs": {"lora_name": lora, "strength_model": lora_strength,
                         "strength_clip": 0.0, "model": ["1", 0], "clip": ["2", 0]}},
        "5": {"class_type": "ModelSamplingAuraFlow",
              "inputs": {"shift": SHIFT, "model": ["4", 0]}},
        "6": {"class_type": "CLIPTextEncode",
              "inputs": {"text": prompt, "clip": ["4", 1]}},
        "7": {"class_type": "CLIPTextEncode",
              "inputs": {"text": negative, "clip": ["4", 1]}},
        "8": {"class_type": "EmptySD3LatentImage",
              "inputs": {"width": gen_size, "height": gen_size, "batch_size": 1}},
        "9": {"class_type": "KSampler",
              "inputs": {"seed": seed, "steps": STEPS, "cfg": CFG,
                         "sampler_name": SAMPLER, "scheduler": SCHEDULER, "denoise": 1.0,
                         "model": ["5", 0], "positive": ["6", 0],
                         "negative": ["7", 0], "latent_image": ["8", 0]}},
        "10": {"class_type": "VAEDecode", "inputs": {"samples": ["9", 0], "vae": ["3", 0]}},
        "11": {"class_type": "LoadBackgroundRemovalModel",
               "inputs": {"bg_removal_name": BG_REMOVAL}},
        "12": {"class_type": "RemoveBackground",
               "inputs": {"bg_removal_model": ["11", 0], "image": ["10", 0]}},
        "13": {"class_type": "InvertMask", "inputs": {"mask": ["12", 0]}},
        "14": {"class_type": "JoinImageWithAlpha",
               "inputs": {"image": ["10", 0], "alpha": ["13", 0]}},
        "15": {"class_type": "SaveImage",
               "inputs": {"filename_prefix": "gensokyou/ai", "images": ["14", 0]}},
    }


def generate(base, prompt, negative, seed, gen_size=GEN_SIZE):
    """跑一次工作流，返回 ComfyUI 输出的 RGBA Image。"""
    wf = build_workflow(prompt, negative, seed, gen_size=gen_size)
    res = _post(base, "/prompt", {"prompt": wf, "client_id": "gensokyou-ai"})
    pid = res["prompt_id"]
    deadline = time.time() + 600
    while time.time() < deadline:
        time.sleep(1.0)
        hist = _get(base, "/history/" + pid)
        if pid not in hist:
            continue
        entry = hist[pid]
        status = entry.get("status", {})
        if status.get("status_str") == "error":
            raise RuntimeError(json.dumps(status, ensure_ascii=False)[:2000])
        images = []
        for out in entry.get("outputs", {}).values():
            images.extend(out.get("images", []))
        if not images:
            continue
        meta = images[0]
        query = urllib.parse.urlencode({
            "filename": meta["filename"],
            "subfolder": meta.get("subfolder", ""),
            "type": meta.get("type", "output"),
        })
        raw = _get_bytes(base, "/view?" + query)
        import io
        return Image.open(io.BytesIO(raw)).convert("RGBA")
    raise TimeoutError(f"ComfyUI 任务超时: {pid}")


# ---------------------------------------------------------------- 后处理


def crop_to_alpha(im, margin_ratio=0.03):
    """裁到 alpha 包围盒，让主体占满画布（MC 物品图标惯例）。"""
    bbox = im.split()[3].getbbox()
    if not bbox:
        return im
    x0, y0, x1, y1 = bbox
    w, h = im.size
    pad = int(max(x1 - x0, y1 - y0) * margin_ratio)
    x0 = max(0, x0 - pad)
    y0 = max(0, y0 - pad)
    x1 = min(w, x1 + pad)
    y1 = min(h, y1 + pad)
    return im.crop((x0, y0, x1, y1))


def resize_premultiplied(im, size):
    """预乘 alpha 缩放。

    直接 resize RGBA 会把透明区域的黑色混进边缘，形成黑边。
    PIL 的 "RGBa" 模式就是预乘 alpha 的，resize 后再转回 RGBA 即可正确还原。
    """
    if isinstance(size, (list, tuple)):
        size = (int(size[0]), int(size[1]))
    else:
        size = (int(size), int(size))
    if im.size == size:
        return im
    return im.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def binarize_alpha(im, threshold=110):
    """alpha 二值化：像素画不要半透明羽化边。"""
    alpha = im.split()[3].point(lambda v: 255 if v >= threshold else 0)
    im.putalpha(alpha)
    return im


def quantize(im, colors):
    """调色板量化（MEDIANCUT，无抖动）。

    只动 RGB，不动 alpha：MC 贴图习惯是「少量纯色 + 明确的明暗阶」，
    抗锯齿产生的中间色在 16px 栅格上只会变成脏边。
    """
    if not colors or colors < 2:
        return im
    alpha = im.split()[3]
    flat = im.convert("RGB")
    pal = flat.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = pal.convert("RGB").convert("RGBA")
    out.putalpha(alpha)
    return out


def to_grayscale(im):
    """转灰度（保留 alpha）。

    染色层（tint）用：MC 的 tint 是 RGB 乘法，灰度底 × 主题色 = 同明暗的彩色，
    所以染层必须是「无色相的明暗信息」，不能带任何固有色相。
    """
    alpha = im.split()[3]
    out = im.convert("L").convert("RGBA")
    out.putalpha(alpha)
    return out


def apply_overlay(im, overlay, assets_dir):
    """把原贴图里的某块区域合成到新图上。

    用途：弹幕核左下角的 3x3 类型微图标是「一眼分辨核种类」的关键线索，
    AI 画不出这种精确小图标，所以从原 16x16 贴图里抠出来放大贴回去。

    overlay = {"source": "item/core_knife", "box": [x0, y0, x1, y1],
               "dest": [dx, dy], "scale": 4, "clear": True}
      box   —— 从源图抠的矩形（左闭右开，源图坐标）
      dest  —— 贴到目标图的左上角坐标
      scale —— 放大倍数（最近邻）
      clear —— 贴之前先把目标区域清成透明，保证图标可读
    """
    src_path = Path(assets_dir) / f"{overlay['source']}.png"
    if not src_path.exists():
        raise FileNotFoundError(src_path)
    src = Image.open(src_path).convert("RGBA")
    x0, y0, x1, y1 = overlay["box"]
    chip = src.crop((x0, y0, x1, y1))
    scale = int(overlay.get("scale", 4))
    chip = chip.resize((chip.width * scale, chip.height * scale), Image.NEAREST)
    dx, dy = overlay["dest"]
    if overlay.get("clear", False):
        # 默认不清：AI 画是满画幅的，清一块会把主体削掉一块（core_qilin 的箭头就被削缺了）。
        # 需要时再显式 clear（会先挖一块透明区保证图标可读）。
        clear_w = chip.width + 2
        clear_h = chip.height + 2
        box = (max(0, dx - 1), max(0, dy - 1),
               min(im.width, max(0, dx - 1) + clear_w),
               min(im.height, max(0, dy - 1) + clear_h))
        region = im.crop(box)
        region.putalpha(0)
        im.paste(region, box)
    im.alpha_composite(chip, (dx, dy))
    return im


def fit_centered(im, size, fit):
    """把主体缩到 fit 见方后居中贴在 size 见方的透明底上。

    用于「叠加层」：符卡纹章要坐在卡面正中，而不是铺满整张卡。
    """
    w, h = size
    scale = fit / max(im.width, im.height)
    nw = max(1, int(round(im.width * scale)))
    nh = max(1, int(round(im.height * scale)))
    small = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    canvas.alpha_composite(small, ((w - nw) // 2, (h - nh) // 2))
    return canvas


def resize_contain(im, size):
    """保持长宽比缩放到 size 见方内，居中贴在透明底上。

    与「拉伸铺满」的区别：拉伸会把竖长条（御札、锥形飞镖）压成方的，
    主体比例全失。保持比例 + 留边才是物品图标的正确做法。
    """
    w, h = size
    scale = min(w / im.width, h / im.height)
    nw = max(1, int(round(im.width * scale)))
    nh = max(1, int(round(im.height * scale)))
    small = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    canvas.alpha_composite(small, ((w - nw) // 2, (h - nh) // 2))
    return canvas


def punch_hole(im, diameter, shape="circle"):
    """在画面正中挖一个透明孔（圆 / 八角），让叠加层从孔里透出来。

    双层物品（layer0 底座 + layer1 染层）必须严丝合缝，而两次独立 AI 生成
    不可能对得准 —— 所以底座这边**程序化挖孔**，染层那边**定位置居中**，
    孔位与晶石位置由代码保证对齐，不靠运气。
    """
    alpha = im.split()[3].copy()
    mask = Image.new("L", im.size, 255)
    draw = ImageDraw.Draw(mask)
    cx, cy = im.width / 2.0, im.height / 2.0
    r = diameter / 2.0
    if shape == "octagon":
        pts = []
        for i in range(8):
            a = math.radians(22.5 + i * 45.0)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        draw.polygon(pts, fill=0)
    else:
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=0)
    im.putalpha(ImageChops.multiply(alpha, mask))
    return im


def finish(raw, size=64, colors=24, alpha_threshold=110, grayscale=False,
           overlays=None, assets_dir=None, fit=None, pad=False, hole=None,
           hole_shape="circle"):
    """raw 1024 RGBA → 成品像素栅格 RGBA。"""
    if isinstance(size, (list, tuple)):
        size = (int(size[0]), int(size[1]))
    else:
        size = (int(size), int(size))
    im = crop_to_alpha(raw)
    if fit:
        im = fit_centered(im, size, int(fit))
    elif pad:
        im = resize_contain(im, size)
    else:
        im = resize_premultiplied(im, size)
    im = binarize_alpha(im, alpha_threshold)
    if grayscale:
        im = to_grayscale(im)
    im = quantize(im, colors)
    for ov in overlays or []:
        im = apply_overlay(im, ov, assets_dir)
    if hole:
        im = punch_hole(im, int(hole), hole_shape)
    return im


# ---------------------------------------------------------------- 规格文件


def load_spec(path):
    p = Path(path)
    if not p.is_absolute():
        p = Path.cwd() / p
    spec = importlib.util.spec_from_file_location("ai_spec", p)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    items = getattr(mod, "ITEMS", None)
    if not items:
        raise SystemExit(f"{p} 里没有 ITEMS 字典")
    return items


def seed_for(name, spec):
    if "seed" in spec:
        return int(spec["seed"])
    digest = hashlib.sha256(name.encode("utf-8")).hexdigest()
    return int(digest[:8], 16)


def normalize_name(name):
    name = name.strip()
    if name.startswith("gensokyou:"):
        name = name[len("gensokyou:"):]
    if name.startswith("textures/"):
        name = name[len("textures/"):]
    return name.replace("\\", "/").strip("/")


# ---------------------------------------------------------------- 拼图预览


def montage(paths, out_path, cell=96, pad=6, cols=6, label_h=14):
    """把成品贴图拼成一张对照图，方便一次看完。paths = [(标签, 路径), ...]"""
    if not paths:
        return
    cols = max(1, min(cols, len(paths)))
    rows = (len(paths) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (cell + pad) + pad,
                               rows * (cell + label_h + pad) + pad),
                      (30, 30, 36, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (label, p) in enumerate(paths):
        im = Image.open(p).convert("RGBA")
        scale = max(1, cell // im.width)
        im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        bg = Image.new("RGBA", im.size, (70, 70, 80, 255))
        for y in range(0, im.height, scale):
            for x in range(0, im.width, scale):
                if (x // scale + y // scale) % 2 == 0:
                    bg.paste((92, 92, 102, 255), (x, y, x + scale, y + scale))
        bg.alpha_composite(im)
        r, c = divmod(i, cols)
        x0 = pad + c * (cell + pad)
        y0 = pad + r * (cell + label_h + pad)
        sheet.alpha_composite(bg, (x0, y0))
        draw.text((x0, y0 + cell + 2), label[:18], fill=(228, 228, 240, 255))
    sheet.convert("RGB").save(out_path)


# ---------------------------------------------------------------- 主流程


def main():
    ap = argparse.ArgumentParser(description="ComfyUI 像素贴图生成管线")
    ap.add_argument("--spec", default=str(TEX_DIR / "ai_items.py"), help="规格文件路径")
    ap.add_argument("--only", action="append", default=None, help="只处理指定贴图名（可多次）")
    ap.add_argument("--seed", type=int, default=None, help="覆盖规格里的 seed")
    ap.add_argument("--size", type=int, default=None, help="覆盖目标像素栅格边长")
    ap.add_argument("--colors", type=int, default=None, help="覆盖量化色数")
    ap.add_argument("--gen-size", type=int, default=None,
                    help="生成分辨率（默认按 size*16 自适应，夹在 256..1024）")
    ap.add_argument("--variants", type=int, default=1,
                    help="每项生成几个候选种子（>1 时输出 name__v1/v2/...，供人工挑选）")
    ap.add_argument("--server", default="http://127.0.0.1:8188", help="ComfyUI 地址")
    ap.add_argument("--out", default=None, help="输出目录（默认 tools/textures/_ai）")
    ap.add_argument("--write-assets", action="store_true", help="覆盖正式 assets 贴图")
    ap.add_argument("--apply", action="store_true",
                    help="不生成，直接把预览目录里已审定的成品拷进 assets")
    ap.add_argument("--raw", action="store_true", help="同时保存原始大图")
    ap.add_argument("--montage", action="store_true", help="额外输出拼图对照图")
    ap.add_argument("--dry-run", action="store_true", help="只打印将要做什么")
    ap.add_argument("--clean", action="store_true",
                    help="生成前清空输出目录里的旧 PNG（只影响本工具的预览目录）")
    args = ap.parse_args()

    items = load_spec(args.spec)
    names = sorted(items)
    if args.only:
        wanted = {normalize_name(n) for n in args.only}
        names = [n for n in names if normalize_name(n) in wanted]

    out_dir = Path(args.out) if args.out else PREVIEW
    out_dir.mkdir(parents=True, exist_ok=True)
    if args.clean:
        for old in sorted(out_dir.rglob("*.png")):
            old.unlink()
        print(f"已清空 {out_dir} 里的旧 PNG")

    if args.apply:
        # 把预览目录里已审定的成品拷进正式 assets（不重新生成，避免种子漂移）
        import shutil
        applied = 0
        for name in names:
            norm = normalize_name(name)
            src = out_dir / f"{norm}.png"
            if not src.exists():
                print(f"!! 预览缺失，跳过: {src}")
                continue
            dst = ASSETS / f"{norm}.png"
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
            print(f"-> {dst}")
            applied += 1
        print(f"已写入 {applied} 张")
        return

    print(f"共 {len(names)} 项 x {args.variants} 变体；输出目录 {out_dir}"
          f"{'（写入 assets）' if args.write_assets else '（仅预览）'}")

    done = []
    for i, name in enumerate(names, 1):
        spec = items[name]
        norm = normalize_name(name)
        base_seed = args.seed if args.seed is not None else seed_for(norm, spec)
        size = args.size or int(spec.get("size", 64))
        colors = args.colors if args.colors is not None else int(spec.get("colors", 24))
        gen_size = args.gen_size or int(spec.get("gen_size", 0)) or \
            max(256, min(1024, size * GEN_PIXELS_PER_TEXEL))
        prompt = spec["prompt"]
        if spec.get("tail", True):
            prompt = prompt.rstrip(", ") + ", " + PROMPT_TAIL
        negative = spec.get("negative", NEGATIVE)
        grayscale = bool(spec.get("grayscale", False))
        overlays = spec.get("overlays") or []
        if isinstance(overlays, dict):
            overlays = [overlays]
        fit = spec.get("fit")
        pad = bool(spec.get("pad", False))
        hole = spec.get("hole")
        hole_shape = spec.get("hole_shape", "circle")

        print(f"[{i}/{len(names)}] {norm}  size={size} colors={colors} gen={gen_size}"
              + ("  [gray]" if grayscale else "")
              + (f"  fit={fit}" if fit else "")
              + ("  [pad]" if pad else "")
              + (f"  hole={hole}" if hole else "")
              + (f"  [overlay x{len(overlays)}]" if overlays else ""))
        if args.dry_run:
            print(f"    prompt: {prompt[:110]}...")
            continue

        for v in range(args.variants):
            seed = base_seed + v * 1000003
            try:
                raw = generate(args.server, prompt, negative, seed, gen_size=gen_size)
            except Exception as exc:  # 单张失败不打断整批
                print(f"    !! 生成失败 (seed={seed}): {exc}")
                continue

            out = finish(raw, size=size, colors=colors, grayscale=grayscale,
                         overlays=overlays, assets_dir=ASSETS, fit=fit, pad=pad,
                         hole=hole, hole_shape=hole_shape)
            suffix = "" if args.variants == 1 else f"__v{v + 1}"
            target = (ASSETS / f"{norm}.png") if args.write_assets else \
                (out_dir / f"{norm}{suffix}.png")
            if args.write_assets and args.variants > 1:
                print("    !! --write-assets 与 --variants>1 冲突，请先人工挑选")
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            out.save(target)
            if args.raw:
                raw.save(target.with_name(target.stem + "_raw.png"))
            print(f"    v{v + 1} seed={seed} -> {target}")
            done.append((f"{norm}{suffix}", target))

    if args.montage and done:
        mpath = out_dir / "_montage.png"
        montage(done, mpath)
        print(f"拼图 -> {mpath}")


if __name__ == "__main__":
    main()
