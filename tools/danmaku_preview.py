#!/usr/bin/env python3
"""Serve the spellcard preview site from the repository root.

Why a server and not `file://`: the site uses native ES modules and fetches the baked
projections plus the real textures from `src/main/resources/assets`, which browsers
refuse to load from the filesystem.

Usage (repository root):
    python tools/danmaku_preview.py            # serve on 127.0.0.1:8787 and open a browser
    python tools/danmaku_preview.py --no-open --port 9000

Data comes from `preview/data/`. If it is missing or stale:
    tools\\gradle_task.ps1 exportDanmakuPreview
    tools\\gradle_task.ps1 checkDanmakuPreview     # compare only, never writes
"""
import argparse
import functools
import http.server
import pathlib
import webbrowser

ROOT = pathlib.Path(__file__).resolve().parent.parent
PAGE = "/preview/index.html"


class Handler(http.server.SimpleHTTPRequestHandler):
    """No caching: a fresh bake must show up on reload."""

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, fmt, *args):
        message = fmt % args
        # Keep the console quiet unless something is actually broken.
        if " 404 " in message or " 500 " in message:
            super().log_message(fmt, *args)


def main():
    parser = argparse.ArgumentParser(description="Serve the danmaku preview site.")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--no-open", action="store_true")
    args = parser.parse_args()

    handler = functools.partial(Handler, directory=str(ROOT))
    with http.server.ThreadingHTTPServer(("127.0.0.1", args.port), handler) as server:
        url = "http://127.0.0.1:%d%s" % (args.port, PAGE)
        print("danmaku preview : %s" % url)
        print("baked data      : preview/data/  (missing or stale -> exportDanmakuPreview)")
        if not args.no_open:
            webbrowser.open(url)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nstopped")


if __name__ == "__main__":
    main()
