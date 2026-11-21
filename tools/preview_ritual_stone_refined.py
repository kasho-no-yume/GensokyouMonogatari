"""Compare current ritual stones with a restrained inset-seal material study."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from gen_tex import load_data_file, render

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'design/ritual-stone-study'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source, entries = load_data_file(ROOT / 'tools/textures/ritual_stone_refined_study.py')
    board = Image.new('RGB', (990, 720), '#211e27')
    draw = ImageDraw.Draw(board)
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 18)
    title = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 26)
    draw.text((28, 18), '仪式石 · 收敛版材质草图', font=title, fill='#eee8f3')
    draw.text((28, 58), '保留砌石秩序 / 封印嵌入石面 / 阶色集中点亮', font=font, fill='#c2b7ce')
    for index, (name, pal, rows) in enumerate(entries):
        tier = int(name[-1])
        y = 111 + index * 196
        image = render(pal, rows, source, name)
        image.save(OUT / f'refined_{tier}.png')
        old = Image.open(ROOT / f'src/main/resources/assets/gensokyou/textures/block/ritual_stone_{tier}.png').convert('RGBA')
        bad = Image.open(OUT / f'draft_{tier}.png').convert('RGBA')
        draw.text((28, y + 42), f'{tier} 阶', font=font, fill='#eee8f3')
        draw.text((120, y), '现有', font=font, fill='#c2b7ce')
        draw.text((390, y), '灵脉草图', font=font, fill='#c2b7ce')
        draw.text((660, y), '收敛版', font=font, fill='#c2b7ce')
        board.paste(old.resize((128, 128), Image.Resampling.NEAREST), (120, y + 32))
        board.paste(bad.resize((128, 128), Image.Resampling.NEAREST), (390, y + 32))
        board.paste(image.resize((128, 128), Image.Resampling.NEAREST), (660, y + 32))
    board.save(OUT / 'refined-comparison.png')
    print(OUT / 'refined-comparison.png')


if __name__ == '__main__':
    main()
