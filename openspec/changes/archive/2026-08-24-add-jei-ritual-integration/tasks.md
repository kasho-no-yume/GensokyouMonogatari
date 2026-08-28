## 1. 构建接入

- [x] 1.1 gradle.properties 新增 jei_version；build.gradle 加 blamejared maven 仓库与三条依赖（compileOnly×2 + localRuntime），gradlew build 验证解析成功
- [x] 1.2 neoforge.mods.toml 声明 JEI 可选依赖与插件 entrypoint

## 2. JEI 插件骨架

- [x] 2.1 `jei` 包：GensokyouJeiPlugin（IModPlugin）+ RitualRecipeWrapper（包装 RitualPattern）
- [x] 2.2 RitualCategory：注册、标题、背景尺寸；palette 字符→ItemStack 映射（EXACT/TAG 代表物/AIR 略过/_ignore 跳过，tag 无成员画屏障+tooltip）
- [x] 2.3 分层绘制：levels 分组网格 + 层级号标注 + 锚点格高亮描边与 tooltip
- [x] 2.4 注册 recipes（RitualPatternLoader.all() 只读消费）

## 3. 查找入口

- [x] 3.1 核心/祭品台物品 → 全部含该方块的仪式条目关联
- [x] 3.2 催化剂静态映射表（召唤/琪露诺催化剂等）→ 对应仪式条目关联

## 4. 收尾验证

- [x] 4.1 lang 条目 zh_cn/en_us（category 标题等）
- [x] 4.2 游戏内验证：JEI 显示 6 个仪式结构正确、锚点标注可见、核心/催化剂按 U 关联正确；无 JEI 环境启动无报错；dedicated server 冒烟
- [x] 4.3 新增仪式检查单文档（docs/，含 rituals JSON、催化剂映射登记、lang 键等同步项）

## 5. 视口拖拽与多层分页（迭代）

- [x] 5.1 RitualRecipeWrapper 增加 levelIndex；插件按层数生成条目（单层合并为一条）
- [x] 5.2 StructureViewWidget：拖拽/滚轮平移 + 包围盒钳制 + 初始居中 + 悬停 tooltip
- [x] 5.3 查找关联迁移至隐形成分；画布定尺；多层分页指示与 registryName 后缀
- [x] 5.4 交互修正：取消按层拆条目，改为条目内翻层控件（◀/▶ + 进度显示），切层后视图重新居中
- [x] 5.5 对齐与提示迭代：切层以锚点为视口中心、贯穿准线标记阵眼、超界方向边缘箭头
