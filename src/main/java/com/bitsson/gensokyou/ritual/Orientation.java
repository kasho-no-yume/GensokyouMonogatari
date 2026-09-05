package com.bitsson.gensokyou.ritual;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

import javax.annotation.Nullable;

/**
 * 格位朝向常量（schema v4，扁平 26 常量表）。
 * 1-4 水平四向（探测 HORIZONTAL_FACING→FACING→AXIS）；5-8 同上加 HALF=top；
 * 9-10 垂直（FACING/AXIS）；11-26 十六段旋转（ROTATION_16）。
 * 1.21.1 实测：RotationSegment 即罗盘序（北0 东4 南8 西12），R 系与段值恒等映射。
 * 变换表与四分之一展开的本原操作同复合：rot90 = 位置 (x,z)→(-z,x)（俯视顺时针），
 * mirrorX = 位置 x→-x；其余（mirrorZ、对角镜像、rot180/270）均为二者复合。
 * 轴属性（原木 AXIS）双向等价：AXIS=Z 同时满足 NORTH 与 SOUTH。
 */
public final class Orientation {
    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int NORTH_TOP = 5;
    public static final int EAST_TOP = 6;
    public static final int SOUTH_TOP = 7;
    public static final int WEST_TOP = 8;
    public static final int UP = 9;
    public static final int DOWN = 10;
    public static final int R0 = 11;
    public static final int MAX_ID = 26;

    private static final Direction[] HORIZONTAL =
            {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final String[] NAMES = {
            "north", "east", "south", "west",
            "north_top", "east_top", "south_top", "west_top",
            "up", "down",
            "r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7",
            "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15"};

    private Orientation() {
    }

    public static boolean valid(int id) {
        return id >= NORTH && id <= MAX_ID;
    }

    /** id → 规范名（报错/提示用）；非法 id 返回 "?"。 */
    public static String name(int id) {
        return id >= NORTH && id <= MAX_ID ? NAMES[id - 1] : "?";
    }

    /** 字符串名 → id（不区分大小写）；未知名返回 null。 */
    public static @Nullable Integer byName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(lower)) {
                return i + 1;
            }
        }
        return null;
    }

    /** 本原旋转：俯视顺时针 90°，与位置变换 (x,z)→(-z,x) 同复合。 */
    public static int rot90(int id) {
        if (id <= 4) {
            return id % 4 + 1;
        }
        if (id <= 8) {
            return (id - 4) % 4 + 5;
        }
        if (id <= 10) {
            return id;
        }
        return R0 + (id - R0 + 4) % 16;
    }

    public static int rot180(int id) {
        return rot90(rot90(id));
    }

    public static int rot270(int id) {
        return rot90(rot180(id));
    }

    /** 本原镜像：位置 x→-x（东西翻转）。 */
    public static int mirrorX(int id) {
        if (id >= 1 && id <= 8) {
            int base = id <= 4 ? 0 : 4;
            int idx = (id - 1) % 4;
            return base + new int[]{0, 3, 2, 1}[idx] + 1;
        }
        if (id <= 10) {
            return id;
        }
        return R0 + (16 - (id - R0)) % 16;
    }

    /** 位置 z→-z（南北翻转）= rot180∘mirrorX。 */
    public static int mirrorZ(int id) {
        return rot180(mirrorX(id));
    }

    /** 位置 (x,z)→(z,x) 对角镜像 = mirrorX∘rot90。 */
    public static int diagSwap(int id) {
        return mirrorX(rot90(id));
    }

    /** 位置 (x,z)→(-z,-x) 反对角镜像 = rot90∘mirrorX。 */
    public static int diagAnti(int id) {
        return rot90(mirrorX(id));
    }

    /** rot90 应用 rotations 次（匹配器旋转尝试 r）。 */
    public static int rotateBy(int id, int rotations) {
        int r = ((rotations % 4) + 4) % 4;
        for (int i = 0; i < r; i++) {
            id = rot90(id);
        }
        return id;
    }

    private static @Nullable Direction facingOf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return null;
    }

    private static boolean hasAxis(BlockState state) {
        return state.hasProperty(BlockStateProperties.AXIS);
    }

    /** 该世界状态能否表达此常量（属性存在性）。 */
    public static boolean supports(BlockState state, int id) {
        if (!valid(id)) {
            return false;
        }
        if (id >= R0) {
            return state.hasProperty(BlockStateProperties.ROTATION_16);
        }
        if (id >= UP) {
            return state.hasProperty(BlockStateProperties.FACING) || hasAxis(state);
        }
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && !state.hasProperty(BlockStateProperties.FACING) && !hasAxis(state)) {
            return false;
        }
        return id <= 4 || state.hasProperty(BlockStateProperties.HALF);
    }

    /** 把常量写入状态（确定性：1-4 在有 HALF 的块上显式写 BOTTOM）。 */
    public static BlockState apply(BlockState state, int id) {
        if (id >= R0) {
            return state.setValue(BlockStateProperties.ROTATION_16, id - R0);
        }
        if (id >= UP) {
            Direction vertical = id == UP ? Direction.UP : Direction.DOWN;
            if (state.hasProperty(BlockStateProperties.FACING)) {
                return state.setValue(BlockStateProperties.FACING, vertical);
            }
            return state.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        }
        Direction dir = HORIZONTAL[(id - 1) % 4];
        boolean top = id >= NORTH_TOP;
        BlockState out = state;
        if (out.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            out = out.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        } else if (out.hasProperty(BlockStateProperties.FACING)) {
            out = out.setValue(BlockStateProperties.FACING, dir);
        } else {
            out = out.setValue(BlockStateProperties.AXIS, dir.getAxis());
        }
        if (out.hasProperty(BlockStateProperties.HALF)) {
            out = out.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        }
        return out;
    }

    /** 世界状态是否满足期望常量（轴双向等价；HALF 严格对偶）。 */
    public static boolean matches(BlockState state, int id) {
        if (!supports(state, id)) {
            return false;
        }
        if (id >= R0) {
            return (int) state.getValue(BlockStateProperties.ROTATION_16) == id - R0;
        }
        if (id >= UP) {
            Direction actual = facingOf(state);
            if (actual != null) {
                return actual == (id == UP ? Direction.UP : Direction.DOWN);
            }
            return state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
        }
        Direction want = HORIZONTAL[(id - 1) % 4];
        Direction actual = facingOf(state);
        if (actual != null) {
            if (actual != want) {
                return false;
            }
        } else if (state.getValue(BlockStateProperties.AXIS) != want.getAxis()) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.HALF)) {
            return (id >= NORTH_TOP) == (state.getValue(BlockStateProperties.HALF) == Half.TOP);
        }
        return true;
    }

    /** 反推世界状态的规范常量（采集输出用）；无朝向属性返回 0。 */
    public static int detect(BlockState state) {
        if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
            return R0 + (int) state.getValue(BlockStateProperties.ROTATION_16);
        }
        Direction dir = facingOf(state);
        if (dir == null && hasAxis(state)) {
            dir = switch (state.getValue(BlockStateProperties.AXIS)) {
                case X -> Direction.EAST;
                case Y -> Direction.UP;
                case Z -> Direction.NORTH;
            };
        }
        if (dir == null) {
            return 0;
        }
        if (dir == Direction.UP) {
            return UP;
        }
        if (dir == Direction.DOWN) {
            return DOWN;
        }
        int idx = switch (dir) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> -1;
        };
        if (idx < 0) {
            return 0;
        }
        boolean top = state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.TOP;
        return top ? NORTH_TOP + idx : NORTH + idx;
    }
}
