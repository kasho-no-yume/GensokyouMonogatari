"""Isometric inventory sprite for the placed surveying pillar."""

NAME = "item/landscaping_tool"
PAL = {
    '.': None,
    '#': (37, 28, 48),
    's': (68, 56, 86), 'g': (98, 82, 122), 'v': (133, 115, 155),
    'o': (92, 68, 40), 'm': (163, 113, 57), 'l': (232, 198, 135),
    'r': (158, 50, 49), 'R': (221, 99, 73),
    'e': (56, 152, 112), 'E': (164, 241, 199),
}

# Drawn as a 32px sprite on the existing 64px item canvas. The right faces
# use the shadow ramp. Top compass, narrow shaft and wide foot match the block.
ART = [
    "................................",
    "................................",
    "...............####.............",
    ".............##vvvv##...........",
    "...........##vvleelvv##.........",
    ".........##vvllrEErllvg##.......",
    ".......##vvlmrREERrmlvggg##.....",
    "......#vvlmeorRRrormelvgggg#....",
    "......#lvvlmmlEElmmolgggsso#....",
    "......#mllvvgmlmmolggsoooss#....",
    "......#gmmllvvglggsooommoss#....",
    ".......##ggmllvvgsoommos###.....",
    ".........##ggmllsommoss##.......",
    "..........#gvgg#ososs#..........",
    "..........#vggr#sogss#..........",
    "..........#ggrR#esgss#..........",
    "..........#gvRE#srgss#..........",
    "..........#ggrr#resgs#..........",
    "..........#vgvg#sogss#..........",
    "..........#gvvg#sgsss#..........",
    ".........#lmmgg#ssoom##.........",
    ".......##vllmml#oommoss##.......",
    ".....##vvvvgmllsommossggg##.....",
    "....#lvvvgvvvvgsgssgggssso##....",
    "...#vvlvvvgvvvs#sggggssooss##...",
    "...#llvvgvvggvs#sgggsssoomss#...",
    "...#mlllvvgvggs#sgssssoomoss#...",
    "....##mmlllvggs#ssoommmoss##....",
    "......###mmmllg#oommmoss##......",
    ".........###mmm#oomoss##........",
    "............##########..........",
    "................................",
]

TEX = ["".join(pixel * 2 for pixel in row) for row in ART for _ in range(2)]
