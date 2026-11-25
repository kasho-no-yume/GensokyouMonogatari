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

附带的 `human_village.json` 只是中心预留区的保护铺装接入样例，未生成村庄或铺路。建筑定稿后替换活动坐标并执行 F3+T，无需服务器逻辑或世界种子。

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
