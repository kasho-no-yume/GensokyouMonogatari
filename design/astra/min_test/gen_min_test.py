#!/usr/bin/env python3
"""最小样例（astra-design-kit 任务 2.5）：验证 struct_compile 双输出路径。

运行：python design/astra/min_test/gen_min_test.py
游戏内验证（由用户跑，重进存档或 /reload 后）：
  1. /place template gensokyou:min_test   （单人存档直接可用：编译器已写 generated/ 回退目录）
  2. /function gensokyou:building/min_test （单人存档也可用：编译器已同步 gs_ritual_test 数据包；
                                            setblock 用绝对坐标，先传到测试区 (104,100,20) 附近看效果）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from tools.struct_compile import put, save_structure, slab

cells = {}
slab(cells, y=0, r2=2, s="minecraft:stone_bricks")   # 9 格圆盘
put(cells, 0, 1, 0, "gensokyou:ritual_core")          # mod 方块
# 站立旗帜只有 rotation(0-15)，facing 属于 purple_wall_banner——用错会 WARN 且属性被静默丢弃
put(cells, 2, 1, 0, "minecraft:purple_banner[rotation=4]")  # 带属性方块
save_structure("min_test", cells)
