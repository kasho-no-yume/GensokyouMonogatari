"""genpic_client.py - Qwen-Image-2.1 生图调用封装（multi_edit / t2i），**本地 / 远程双模式**。

为什么需要它：把「怎么调」固化成代码，而不是散落在对话里。
规格见 openspec/changes/sakura-gui-art-system/（capability gui-art-system）。

⚠️ 三条踩过的坑，改动前先读：

1. **远程 MUST 用 /run + 轮询 /status，MUST NOT 用 /runsync。**
   qwen21 一次生成常 >90s，而 /runsync 服务端只等 90 秒、且**结果只保留 1 分钟**
   （/run 保留 30 分钟）。用 /runsync 会「发出去 7 分钟后再查就是 404，但任务其实跑完了」。
2. **参考图必须走 input.images[]，且 LoadImage.inputs.image 要与 images[].name 一字不差。**
   容器里只有那个名字存在；留着没喂文件的 LoadImage 节点会让整单失败。
3. **cfg 固定 1.0**（官方路径），此时 negative_prompt 不生效 ——
   要规避某元素只能写进正向描述。

## 本地模式（--local）

目标：**完全离线、零 API 费用**。本机 ComfyUI（F:\\Comfy-Desktop\\ComfyUI-Installs\\ComfyUI）
与 Runpod 端点跑同一套工作流，差别只有两处：

| 位置 | 远程 | 本地 |
|---|---|---|
| UNET 加载器 | `UNETLoader` + `qwen_image_2.1_int8_convrot.safetensors` | `UnetLoaderGGUF` + `qwen-image-2.1-Q4_K_M.gguf` |
| 提交/取图 | `/run` + 轮询 `/status` | `/upload/image` + `/prompt` + 轮询 `/history` + `/view` |

⚠️ 本地 ComfyUI **不是随游戏/仓库启动的**，用完 `--local` 之后要自己确认端口是否还占着。
启动命令（本机实测可用；用 `ComfyUI\\.venv` 里的 python，不要用 standalone-env —— 那里没有 torch）：

    F:\\Comfy-Desktop\\ComfyUI-Installs\\ComfyUI\\ComfyUI\\.venv\\Scripts\\python.exe ^
      F:\\Comfy-Desktop\\ComfyUI-Installs\\ComfyUI\\ComfyUI\\main.py --listen 127.0.0.1 --port 8188

用法（仓库根目录执行）：
    # 只看请求体长什么样，不发出去（默认）
    python tools/genpic_client.py --prompt "..." --draft build/draft.png --material tools/textures/sakura/material.png --width 1200 --height 800
    # 本地真跑（默认 http://127.0.0.1:8188）
    python tools/genpic_client.py --prompt "..." --draft build/draft.png --material tools/textures/sakura/material.png --width 1456 --height 192 --local --send
    # 远程真跑（需要环境变量 RUNPOD_API_KEY）
    $env:RUNPOD_API_KEY = "<key>"
    python tools/genpic_client.py --prompt "..." --draft build/draft.png --material tools/textures/sakura/material.png --width 1200 --height 800 --send

    --endpoint-id 缺省用环境变量 GENPIC_ENDPOINT_ID（再缺省用 DEFAULT_ENDPOINT）。
    ⚠️ **key MUST 只经环境变量传入**，MUST NOT 写进仓库文件（`build/` 已 gitignore，
       可以临时落在 `build/` 下）。

典型三步（面板烘焙）：
    1) python tools/gui_draft.py --layout <面板名> --k 4 --out build/draft_k4.png
    2) python tools/genpic_client.py --prompt "<prompt>" --draft build/draft_k4.png \
         --material tools/textures/sakura/material.png --width 1200 --height 800 \
         --out build/raw.png --send
    3) python tools/gui_art.py --layout <面板名> --ai build/raw.png --write-assets
"""
import argparse
import base64
import json
import mimetypes
import os
import pathlib
import sys
import time
import urllib.parse
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "tools" / "textures"

DEFAULT_ENDPOINT = os.environ.get("GENPIC_ENDPOINT_ID", "sfhy5bkcvvsojm")
LOCAL_URL = os.environ.get("GENPIC_LOCAL_URL", "http://127.0.0.1:8188")
TERMINAL = {"COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT"}

UNET = "qwen_image_2.1_int8_convrot.safetensors"
UNET_GGUF = "qwen-image-2.1-Q4_K_M.gguf"
CLIP = "qwen3vl_8b_int8_convrot.safetensors"
VAE = "qwen_image_2.1_vae_bf16.safetensors"


# ---------------------------------------------------------------- 工作流

def build_workflow(prompt, seed, width, height, steps=25, cfg=1.0, n_images=2, local=False):
    """ComfyUI **API 格式**的工作流（节点 id 为字符串）。

    n_images=1 -> 文生图；n_images>=2 -> 多图编辑，prompt 里用 <image1>…<imageN> 指代，
    其中 image_1 是**编辑目标**（结构草稿）。

    local=True 只换 UNET 加载器（GGUF 走 UnetLoaderGGUF）；CLIP / VAE / 采样链与远程**完全同源**，
    否则「本地出的图和远程不一样」就无从判断是模型差异还是接线差异。
    """
    wf = {
        "1": ({"class_type": "UnetLoaderGGUF", "inputs": {"unet_name": UNET_GGUF}}
              if local else
              {"class_type": "UNETLoader",
               "inputs": {"unet_name": UNET, "weight_dtype": "default"}}),
        "2": {"class_type": "CLIPLoader",
              "inputs": {"clip_name": CLIP, "type": "qwen_image", "device": "default"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": VAE}},
    }
    for i in range(1, n_images + 1):
        wf[str(10 + i)] = {"class_type": "LoadImage",
                           "inputs": {"image": "input_%d.png" % i, "upload": "image"}}
    enc = {"prompt": prompt, "negative_prompt": "", "resolution": 1024,
           "clip": ["2", 0], "vae": ["3", 0]}
    for i in range(1, n_images + 1):
        enc["images.image_%d" % i] = [str(10 + i), 0]
    wf["4"] = {"class_type": "TextEncodeQwenImage21", "inputs": enc}
    wf["5"] = {"class_type": "EmptyLatentImage",
               "inputs": {"width": width, "height": height, "batch_size": 1}}
    wf["6"] = {"class_type": "QwenImage21Cache",
               "inputs": {"model": ["1", 0], "device": "auto", "dtype": "default"}}
    wf["7"] = {"class_type": "KSampler",
               "inputs": {"seed": seed, "steps": steps, "cfg": cfg,
                          "sampler_name": "euler", "scheduler": "simple",
                          "model": ["6", 0], "positive": ["4", 0], "negative": ["4", 0],
                          "latent_image": ["5", 0], "denoise": 1.0}}
    wf["8"] = {"class_type": "VAEDecode", "inputs": {"samples": ["7", 0], "vae": ["3", 0]}}
    wf["9"] = {"class_type": "SaveImage",
               "inputs": {"images": ["8", 0], "filename_prefix": "gensokyou_gui"}}
    return wf


def encode_image(path, name):
    mime = mimetypes.guess_type(str(path))[0] or "image/png"
    data = base64.b64encode(pathlib.Path(path).read_bytes()).decode()
    return {"name": name, "image": "data:%s;base64,%s" % (mime, data)}


def build_payload(workflow, image_paths):
    images = [encode_image(p, "input_%d.png" % (i + 1)) for i, p in enumerate(image_paths)]
    return {"input": {"workflow": workflow, "images": images}}


# ---------------------------------------------------------------- 发送

def _request(url, key, payload=None, timeout=120):
    req = urllib.request.Request(
        url,
        data=None if payload is None else json.dumps(payload).encode("utf-8"),
        headers={"Authorization": "Bearer %s" % key, "Content-Type": "application/json"},
        method="GET" if payload is None else "POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def send(endpoint_id, payload, key, timeout=900, poll=4):
    """/run 提交 + 轮询 /status。结果在服务端保留 30 分钟。"""
    base = "https://api.runpod.ai/v2/%s" % endpoint_id
    job = _request(base + "/run", key, payload)
    job_id = job.get("id")
    if not job_id:
        raise RuntimeError("提交失败：%s" % json.dumps(job)[:400])
    print("submitted: %s (%s)" % (job_id, job.get("status")))
    deadline = time.time() + timeout
    while job.get("status") not in TERMINAL:
        if time.time() > deadline:
            raise RuntimeError("超时，job %s 还在跑（30 分钟内可继续查 /status）" % job_id)
        time.sleep(poll)
        job = _request("%s/status/%s" % (base, job_id), key)
    if job.get("status") != "COMPLETED":
        raise RuntimeError("%s: %s" % (job.get("status"), job.get("error")))
    return job


# ---------------------------------------------------------------- 本地 ComfyUI

def _json_call(url, payload=None, timeout=120):
    req = urllib.request.Request(
        url,
        data=None if payload is None else json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="GET" if payload is None else "POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def upload_image(base, path, name, timeout=120):
    """multipart 上传到 /upload/image，返回服务端**实际**使用的名字（可能带子目录）。

    ⚠️ MUST 用返回的名字回填 LoadImage，MUST NOT 假定就是传入的 filename ——
    ComfyUI 在重名且 overwrite 未生效时会自己改名，名字对不上就是整单失败。
    """
    boundary = "----gensokyou%d" % int(time.time() * 1000)
    body = bytearray()

    def field(key, value):
        body.extend(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                     % (boundary, key, value)).encode("utf-8"))

    field("type", "input")
    field("overwrite", "true")
    body.extend(("--%s\r\nContent-Disposition: form-data; name=\"image\"; filename=\"%s\"\r\n"
                 "Content-Type: image/png\r\n\r\n" % (boundary, name)).encode("utf-8"))
    body.extend(pathlib.Path(path).read_bytes())
    body.extend(("\r\n--%s--\r\n" % boundary).encode("utf-8"))
    req = urllib.request.Request(
        base + "/upload/image", data=bytes(body),
        headers={"Content-Type": "multipart/form-data; boundary=%s" % boundary},
        method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        info = json.loads(resp.read().decode("utf-8"))
    got = info.get("name") or name
    sub = info.get("subfolder") or ""
    return (sub + "/" + got) if sub else got


def send_local(base, workflow, image_paths, timeout=1800, poll=2):
    """本地 ComfyUI：上传参考图 -> /prompt -> 轮询 /history -> /view 取回 PNG 字节。"""
    for i, path in enumerate(image_paths):
        name = upload_image(base, path, "input_%d.png" % (i + 1))
        print("uploaded: %s -> %s" % (path, name))
        workflow[str(11 + i)]["inputs"]["image"] = name
    res = _json_call(base + "/prompt", {"prompt": workflow, "client_id": uuid.uuid4().hex})
    pid = res.get("prompt_id")
    if not pid:
        raise RuntimeError("提交失败：%s" % json.dumps(res)[:600])
    print("submitted: %s" % pid)
    deadline = time.time() + timeout
    entry = None
    while entry is None:
        if time.time() > deadline:
            raise RuntimeError("超时，prompt %s 还在跑（本地可去 /history/%s 继续查）" % (pid, pid))
        time.sleep(poll)
        hist = _json_call(base + "/history/" + pid)
        entry = hist.get(pid)
        if entry and not (entry.get("status") or {}).get("completed", True):
            entry = None
    status = entry.get("status") or {}
    if status.get("status_str") == "error":
        raise RuntimeError("生成失败：%s" % json.dumps(status.get("messages"))[-1500:])
    outputs = entry.get("outputs") or {}
    for node in outputs.values():
        for img in node.get("images") or []:
            query = urllib.parse.urlencode({"filename": img["filename"],
                                            "subfolder": img.get("subfolder", ""),
                                            "type": img.get("type", "output")})
            with urllib.request.urlopen(base + "/view?" + query, timeout=120) as resp:
                return resp.read()
    raise RuntimeError("COMPLETED 但没有图：%s" % json.dumps(outputs)[:600])



def main(argv):
    ap = argparse.ArgumentParser(description="Runpod 生图端点调用封装")
    ap.add_argument("--prompt", default=None)
    ap.add_argument("--prompt-file", default=None,
                    help="从文件读 prompt（避免 PowerShell 传长串时被截断/转义）")
    ap.add_argument("--draft", default=None, help="结构草稿（编辑目标，image_1）")
    ap.add_argument("--material", default=None, help="材质件（风格参考，image_2）")
    ap.add_argument("--width", type=int, required=True)
    ap.add_argument("--height", type=int, required=True)
    ap.add_argument("--seed", type=int, default=20261030)
    ap.add_argument("--steps", type=int, default=25)
    ap.add_argument("--cfg", type=float, default=1.0)
    ap.add_argument("--endpoint-id", default=DEFAULT_ENDPOINT)
    ap.add_argument("--local", action="store_true",
                    help="走本机 ComfyUI（GGUF UNET），不花 API 费用")
    ap.add_argument("--local-url", default=LOCAL_URL, help="本机 ComfyUI 地址")
    ap.add_argument("--timeout", type=int, default=1800, help="等图上限（秒）")
    ap.add_argument("--out", default=None, help="出图落盘路径")
    ap.add_argument("--payload-out", default=None,
                    help="把**完整请求体**写成 JSON（给 MCP / 手工 /run 复用，MUST NOT 含 key）")
    ap.add_argument("--send", action="store_true", help="真发（默认只打印请求体摘要）")
    args = ap.parse_args(argv)

    if args.prompt_file:
        args.prompt = pathlib.Path(args.prompt_file).read_text(encoding="utf-8").strip()
    if not args.prompt:
        print("缺少 --prompt 或 --prompt-file")
        return 1

    paths = [p for p in (args.draft, args.material) if p]
    if not paths:
        print("至少要给 --draft 或 --material 之一")
        return 1
    wf = build_workflow(args.prompt, args.seed, args.width, args.height,
                        args.steps, args.cfg, len(paths), local=args.local)
    body = json.dumps({"input": {"workflow": wf, "images": []}}) if not args.local else None
    if args.payload_out and not args.local:
        payload = build_payload(wf, paths)
        out = pathlib.Path(args.payload_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        print("payload  -> %s（%.2f MB，不含 key）" % (out, out.stat().st_size / 1e6))
    print("mode     : %s" % ("LOCAL %s" % args.local_url if args.local
                             else "RUNPOD %s" % args.endpoint_id))
    print("output   : %dx%d  steps=%d cfg=%.1f seed=%d" %
          (args.width, args.height, args.steps, args.cfg, args.seed))
    print("images   : %s" % ", ".join(paths))
    if not args.send:
        if args.local:
            print("dry-run：未发送。加 --send 真跑（需要本机 ComfyUI 在 %s）" % args.local_url)
        else:
            print("payload  : %.1f MB（上限 10MB；base64 会把原图放大约 33%%）"
                  % (len(body) / 1e6))
            print("dry-run：未发送。加 --send 真发（需要 RUNPOD_API_KEY）。")
        return 0

    if args.local:
        data = send_local(args.local_url, wf, paths, timeout=args.timeout)
    else:
        key = os.environ.get("RUNPOD_API_KEY")
        if not key:
            print("缺少环境变量 RUNPOD_API_KEY")
            return 1
        payload = build_payload(wf, paths)
        job = send(args.endpoint_id, payload, key, timeout=args.timeout)
        outs = (job.get("output") or {}).get("images") or []
        if not outs:
            print("COMPLETED 但没有图：%s" % (job.get("output") or {}).get("errors"))
            return 1
        data = base64.b64decode(outs[0]["data"])
        print("耗时: 执行 %sms，排队 %sms" % (job.get("executionTime"), job.get("delayTime")))
    out = pathlib.Path(args.out) if args.out else pathlib.Path("gui_out.png")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(data)
    print("saved: %s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
