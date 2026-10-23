"""Regression checks for matte removal and runtime window geometry."""
import unittest

from PIL import Image, ImageChops

import boss_bar_art as art
import preview_boss_bar as preview


class BossBarArtTest(unittest.TestCase):
    def test_white_matte_and_paper(self):
        image = Image.new("RGBA", (3, 1))
        image.putdata([(255, 255, 255, 255), (238, 226, 202, 255),
                       (255, 239, 239, 255)])
        art._white_to_alpha(image)
        self.assertEqual(image.getpixel((0, 0))[3], 0)
        self.assertEqual(image.getpixel((1, 0)), (238, 226, 202, 255))
        edge = image.getpixel((2, 0))
        self.assertTrue(0 < edge[3] < 255)
        self.assertLess(edge[1], 239)  # White contamination was removed.

    def test_windows_remain_readable_after_runtime_stretch(self):
        for tier in range(1, 6):
            path = art.PREVIEW / ("boss_bar_art_tier%d.png" % tier)
            with Image.open(path) as frame:
                self.assertEqual(frame.size, (art.TW, art.TH))
                for width in (120, 160, 182, 240, 300, 400):
                    with self.subTest(tier=tier, width=width):
                        result = preview.nine_slice(frame, width * art.SS)
                        window = result.getchannel("A").crop((
                            art.TX0, art.WIN_TOP_TEX,
                            width * art.SS - art.TX0, art.WIN_BOT_TEX + 1))
                        # End ornaments can be dense, particularly at minimum
                        # width. The central reading area must stay open.
                        center = window.crop((window.width // 2 - 8, 8,
                                              window.width // 2 + 8, 24))
                        self.assertLess(sum(i * n for i, n in enumerate(center.histogram())) /
                                        (center.width * center.height), 64)

    def test_window_preserves_source_ornament(self):
        raw = Image.open(art.BUILD / 'raw_v4_0.png').convert('RGB').convert('RGBA')
        windows = art.load_windows(art.TEX_DIR / 'boss_bar_windows_v4.json')
        for tier, geometry in windows.items():
            expected = art._resize_alpha(art._white_to_alpha(raw.crop(geometry['window']), ornament=True),
                                        (art.TX1 - art.TX0 + 1, art.WIN_BOT_TEX - art.WIN_TOP_TEX + 1))
            with Image.open(art.PREVIEW / ('boss_bar_art_tier%d.png' % tier)) as frame:
                actual = frame.crop((art.TX0, art.WIN_TOP_TEX, art.TX1 + 1, art.WIN_BOT_TEX + 1))
                difference = ImageChops.difference(actual.getchannel('A'), expected.getchannel('A'))
                self.assertEqual(difference.getextrema(), (0, 0))

    def test_ornament_key_removes_pale_matte_but_keeps_pigment(self):
        image = Image.new('RGBA', (4, 1))
        image.putdata([(245, 230, 250, 255), (240, 235, 235, 255),
                       (100, 25, 170, 255), (190, 20, 40, 255)])
        art._white_to_alpha(image, ornament=True)
        self.assertEqual([image.getpixel((x, 0))[3] for x in range(4)], [0, 0, 255, 255])


if __name__ == "__main__":
    unittest.main()
