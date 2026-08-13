---
name: stronghold_finding
description: 合成末影之眼，使用 locate_structure 查找要塞（无需投掷末影之眼），抵达传送门房间，并通过 inspect_block 与 interact_at 填满 12 个框架。
---

# 技能：寻找要塞

这是屠龙路线的第 5 阶段。取得烈焰棒和末影珍珠后，合成末影之眼并直接前往要塞；**`locate_structure("minecraft:stronghold")` 取代了投掷并三角定位的整套流程**，最后激活末地传送门。

## 完成条件

- 站在**已激活的末地传送门**旁（12 个框架全部填满，中央出现紫黑色星空表面）
- 屠龙装备仍然完整（用 `get_self_status` 对照 `dragon_combat` 的物资清单）

## 第 1 步——合成末影之眼

两项都是 2×2/无序配方：用 `lookup_recipe` 获取布局，再通过 `transfer` 把材料放进合成格并取出结果（参见 `containers`；无需工作台）。

1. `blaze_powder`——每根烈焰棒可分解成 2 份烈焰粉。
2. `ender_eye`（×12）——每个需要 1 份烈焰粉和 1 颗末影珍珠。

12 个是最坏情况；框架生成时每格有 10% 概率已填入（通常约 1–2 格），因此可能会有剩余。**绝不要通过投掷末影之眼寻路**——`locate_structure("minecraft:stronghold")` 免费且精确，末影之眼只用于框架。

## 第 2 步——前往要塞

1. `locate_structure("minecraft:stronghold")` 返回坐标、方向和距离（通常相距 1000–2500 格，旅途本身耗时最长）。
2. 使用 `goto(x, ~60, z)` 穿越地表，再用 `goto(x, 30, z)`；导航会自行向下挖掘。要塞通常位于 Y 6–50。
3. 遇到石砖就说明已经进入。使用 `scan_blocks(end_portal_frame)` 查找传送门房间；没有结果时用 `goto` 探索走廊并重新扫描。（走廊主要由 stone_bricks、mossy_stone_bricks 和 cracked_stone_bricks 构成。）

## 第 3 步——控制传送门房间

框架下方有熔岩池，楼梯上还有一个**蠹虫刷怪笼**：

1. 立即 `mine(spawner)`；与烈焰人刷怪笼不同，它只有危险，没有保留价值。
2. 如果已有蠹虫出现，扫描后把它们的运行时 ID 交给 `attack`；不要让它们钻入砖墙。
3. 用 `build` 在需要站立的熔岩池边缘铺设圆石。

## 第 4 步——填充框架

1. 12 个 `end_portal_frame` 围绕一个 3×3 开口。`scan_blocks(end_portal_frame)` 会列出全部 12 个位置。
2. 逐个调用 `inspect_block`；`has_eye` 属性会标明哪些已经填入眼睛。
3. 对每个空框架调用 `interact_at(button=right, x, y, z, item_id=minecraft:ender_eye)`。**填入后无法取回。**
4. 第 12 个眼睛会激活传送门，开口中出现星空表面。

## 跳入传送门之前

- **现在**调用 `load_skill(name="dragon_combat")`，不要等进入末地后再加载；首领战必须提前规划。
- 再次核对屠龙物资，并**告诉主人传送门已经激活以及它的坐标**。主人可能想在附近设置重生点并前来观看；末影龙死亡前，进入末地后无法正常返回。

## 接下来加载什么

加载 `dragon_combat`。这是最后一个阶段。
