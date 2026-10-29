"""gui_treasure_basin.py - 聚宝盆界面底图（176x166，结构层）。

与 `gui_art.py` 的分工一致：**结构层（边框、槽框、分隔线）由程序锁死坐标**，
材质装饰层后续可以再走 AI 收口管线覆盖（本文件只负责让界面进游戏不缺图）。

槽坐标必须与 `TreasureBasinMenu` 完全一致：
    3x3 容器槽 (62,17) 起，间距 18
    玩家背包  (8,84) 起，9x3，间距 18
    快捷栏    (8,142)

用法（仓库根目录）：
    python tools/gui_treasure_basin.py
输出直接写 assets（1:1，不做缩放）。
"""
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/gensokyou/textures/gui/treasure_basin.png"

W, H = 176, 166

PAPER = (246, 238, 219)
BORDER_DARK = (74, 54, 34)
BORDER_GOLD = (196, 158, 96)
SEPARATOR = (206, 184, 146)
SLOT_FILL = (92, 72, 48)
SLOT_DARK = (56, 42, 28)
SLOT_LIGHT = (188, 152, 96)
ORNAMENT = (255, 193, 7)

CONTAINER_ORIGIN = (62, 17)
INVENTORY_ORIGIN = (8, 84)
HOTBAR_ORIGIN = (8, 142)


def recess(draw, x, y):
    """18x18 内凹槽：暗底 + 左上压暗 / 右下提亮。"""
    draw.rectangle([x - 1, y - 1, x + 16, y + 16], fill=SLOT_FILL)
    draw.line([x - 1, y - 1, x + 16, y - 1], fill=SLOT_DARK)
    draw.line([x - 1, y - 1, x - 1, y + 16], fill=SLOT_DARK)
    draw.line([x + 16, y, x + 16, y + 16], fill=SLOT_LIGHT)
    draw.line([x, y + 16, x + 16, y + 16], fill=SLOT_LIGHT)


def main():
    image = Image.new("RGB", (W, H), PAPER)
    draw = ImageDraw.Draw(image)

    # 双层边框：外铜 + 内金线
    draw.rectangle([0, 0, W - 1, H - 1], outline=BORDER_DARK, width=2)
    draw.rectangle([2, 2, W - 3, H - 3], outline=BORDER_GOLD, width=1)

    # 四角琥珀钉
    for x, y in ((5, 5), (W - 8, 5), (5, H - 8), (W - 8, H - 8)):
        draw.rectangle([x, y, x + 2, y + 2], fill=ORNAMENT)

    # 容器区与背包区之间的分隔线
    draw.line([6, 76, W - 7, 76], fill=SEPARATOR)

    # 3x3 盆口
    for row in range(3):
        for col in range(3):
            recess(draw, CONTAINER_ORIGIN[0] + col * 18, CONTAINER_ORIGIN[1] + row * 18)

    # 玩家背包 9x3
    for row in range(3):
        for col in range(9):
            recess(draw, INVENTORY_ORIGIN[0] + col * 18, INVENTORY_ORIGIN[1] + row * 18)

    # 快捷栏 9x1
    for col in range(9):
        recess(draw, HOTBAR_ORIGIN[0] + col * 18, HOTBAR_ORIGIN[1])

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print("[ok] %s (%dx%d)" % (OUT, W, H))


if __name__ == "__main__":
    main()
