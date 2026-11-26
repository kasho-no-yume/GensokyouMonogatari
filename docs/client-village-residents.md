# 人间之里环境居民

居民是客户端模型，不是实体。不会响应攻击、交易或实体选择器，不产生服务器 AI、居民同步或存档。具名角色仍使用 NPC 系统。

## 预览

在创造模式站到平坦、干燥、已加载的地面上，面向一片空地执行：

```text
/gs_residents preview 24
/gs_residents status
/gs_residents clear
```

预览排在执行时玩家前方，最多 48 名，一半驻足、一半沿短路线往返。预览期间暂时替代正式活动点，清除后恢复正式场景。只在本客户端可见，不修改方块；清除、切维度、退出世界或资源重载都会移除预览。生存模式不能创建预览。

## 建筑接入

资源包在 `assets/<namespace>/village_residents/<scene>.json` 放活动点：

```json
{
  "dimension": "gensokyou:gensokyo",
  "origin": [-200, 72, 100],
  "residents": [
    {"id": "bookstall_reader", "points": [[-6, 0, 2]], "facing": 90},
    {"id": "lane_walker", "points": [[0, 0, 0], [8, 0, 0], [8, 0, 6]]}
  ]
}
```

原点和点偏移单位均为方块，Y 为预期脚底高度，允许向下两格、向上半格的局部地面适配，避免把低顶或临时障碍的顶部当成新落点。一个点代表驻足，多点代表沿折线往返，每个节点短暂停留。`facing` 使用 Minecraft 偏航角。槽位 ID 需要唯一且稳定，修改 ID 会改变外观。

正式活动只在指定维度的已加载区域生效，脚下必须是 `landmark_brick`、`landmark_wood`、`landmark_tile`、对应半砖或楼梯；不因为自然草地碰巧在同一坐标就出现居民。不要把路线穿过建筑或水面。每条最多 16 点，相邻点 0.05–64 格，每场景最多 128 人，总数最多 512 人，同时最多活动/绘制 48 人，距离上限 96 格。

正式 `human_village.json` 现为 116 个槽位（50 名驻足、66 名行人），分布于龙神广场、东西主街与市集、铃奈庵书坊街、鲵吞亭水巷、稗田宅门前及高札场/香霖堂西道。它只依赖跨种子固定的路网和铺装，与外围随机民居分离；同时活动/绘制上限仍为 48 人。需要重新生成配置时运行 `python tools/export_human_village_residents.py`。

场景原点为 `[-200,73,100]`：路面方块在 `Y=72`，居民脚底是 `Y=73`。不要把建筑原点或地面方块 Y 直接当成居民脚底高度。服务器按已加载区块渐进放置正式村庄；旧的空白地形区块也可首次补建，已完成区块不会因重进世界再次覆盖。

管理员可使用 `/gs_village visit dragon|hieda|suzunaan|geidontei|kourindou|east_gate` 抵达正式村庄，使用 `/gs_village stats` 查看建筑、三圈民居数量、连通率、放置进度及采样群系边界余量。`/gs_village showcase` 继续使用独立样件维度，供逐栋查看外观。修改居民资源后执行 F3+T 即可重新加载场景。

自动化回归由 `HumanVillageResidentsAlignmentTest` 读取正式 NBT 与居民 JSON，对每段路线每 0.25 格检查固定铺装和碰撞。隔离服务端可运行 `./tools/gradle_task.ps1 runHumanVillageProbe`，使用真实模组方块校验分块放置、存档、溪水、保护及全部居民路线；需先在 `build/human-village-probe/` 中接受 EULA。测试不代替客户端视效与性能验收。

## 实机验收

- 预览不同居民，检查三种衣形、四种头部样式、配件与配色，离开再返回时外观稳定。
- 居民正常被不透明墙遮住，日夜和灯光影响亮度；不得全亮或透墙。
- 在行走路径前放方块，居民停住；移除后从原位置继续。检查台阶/半砖、坑洞、水和低顶，不穿墙、不跳过阻挡。
- 暂停单人游戏时动画停止；F3+T、退出重进、切维度后无旧场景或预览残留。
- 联机执行预览，另一客户端不出现该预览，服务端没有新增居民实体。
- 使用 `status` 记录本地活动/绘制数量，沿 96 格边界和低视距巡查；性能数值需在实际硬件采样，不以实体数为零代替帧耗验收。

## 开发工具

本次使用 OpenSpec 2.2.0；当前容器中安装在用户工具目录，仓库提供 `bash tools/openspec_task.sh` 入口：

```bash
bash tools/openspec_task.sh status --change add-client-village-residents
bash tools/openspec_task.sh validate add-client-village-residents --strict
```
