"""Compose existing textures and draft previews; never write to game assets."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from gen_tex import load_data_file, render

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'design/ritual-stone-study'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source, entries = load_data_file(ROOT / 'tools/textures/ritual_stone_vein_study.py')
    board = Image.new('RGB', (960, 830), '#201d28')
    draw = ImageDraw.Draw(board)
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 18)
    title = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 26)
    draw.text((28, 18), '仪式石 · 灵脉材质草图', font=title, fill='#ebe4f3')
    draw.text((28, 59), '16×16 原生像素 / 紫石主体 / 阶色嵌入裂隙 / 预览未应用', font=font, fill='#bdb2cd')
    for index, (name, pal, rows) in enumerate(entries):
        tier = int(name[-1])
        image = render(pal, rows, source, name)
        image.save(OUT / f'draft_{tier}.png')
        old = Image.open(ROOT / f'src/main/resources/assets/gensokyou/textures/block/ritual_stone_{tier}.png').convert('RGBA')
        x = 36 + index * 310
        draw.text((x, 103), f'{tier} 阶 · ' + {0: '沉睡刻痕', 3: '琥珀灵脉', 5: '紫晶分叉'}[tier], font=font, fill='#ebe4f3')
        draw.text((x, 139), '现有贴图', font=font, fill='#bdb2cd')
        board.paste(old.resize((128, 128), Image.Resampling.NEAREST), (x, 170))
        draw.text((x, 319), '灵脉草图', font=font, fill='#bdb2cd')
        board.paste(image.resize((128, 128), Image.Resampling.NEAREST), (x, 350))
        draw.text((x, 501), '3×3 连铺检查', font=font, fill='#bdb2cd')
        tile = image.resize((80, 80), Image.Resampling.NEAREST)
        for row in range(3):
            for col in range(3):
                board.paste(tile, (x + col * 80, 535 + row * 80))
    draw.text((28, 793), '检查重点：阶色辨认、石材比例、重复节奏。此图不包含游戏光照或自发光。', font=font, fill='#bdb2cd')
    board.save(OUT / 'comparison.png')
    print(OUT / 'comparison.png')


if __name__ == '__main__':
    main()
