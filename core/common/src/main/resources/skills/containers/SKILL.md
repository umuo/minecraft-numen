---
name: containers
description: 如何在任意容器或机器 GUI 中存取物品——箱子、木桶、潜影盒、熔炉及模组机器。涵盖“打开 → inspect_gui → transfer → close_gui”循环，使用 transfer 存入、取出和交换物品，在合成格中摆放配方、给熔炉装料，以及错误恢复。
---

# 技能：容器与机器

你要像真实玩家一样通过 GUI 移动物品：打开方块、查看槽位、移动物品、关闭界面。这里没有按物品类型封装的黑箱；你使用 `transfer` 直接操作菜单。它适用于**任何**原版或模组容器/机器，而且能让你**看见并修正**错误。

## 寻找存储设施

如果 `<known_blocks>` 已给出容器坐标，就直接复用。否则调用 `scan_storage` 并设置 `storage_type=items`；它会根据真实库存能力发现原版和模组的箱子、货箱、抽屉、仓库与机器，所以不要猜测模组方块 ID，也不要只扫描 `minecraft:chest`。查找储罐、电池或混合机器时使用 `fluids`、`energy` 或 `all`。结果会给出精确坐标，可用于 `inspect_block_storage` 或 `interact_at`。

## 基本循环

1. **打开**——对容器方块调用 `interact_at`，设置 `button=right`（必要时会先自动寻路）。GUI 会打开并保持开启。
2. **查看**——调用 `inspect_gui`。它会列出每个槽位：`index: item xN`、属于容器还是你的背包，以及只允许取出的槽位 `[output]`（例如熔炉产物或机器输出）。
3. **移动**——使用 `transfer` 转移物品。传入一个**列表**，一次调用完成整个操作。
4. **验证**——`transfer` 的结果已说明实际发生了什么；只有需要再次确认时才重新调用 `inspect_gui`。
5. **关闭**——完成后调用 `close_gui`。（走远时也会自动关闭。）

## 使用 transfer 移动物品

`transfer` 接收一个移动操作**列表**（`moves=[{from, to?, count?}, …]`），按顺序在**一次调用**中执行。不要每件物品往返调用一次。每个移动项包含：

- **`from`**——来源槽位，由 `inspect_gui` 获取。
- **`to`**——目标槽位。**省略它**会把整组物品送往菜单的*另一侧*，由菜单自动路由（存入箱子、从箱子取出、把可烧炼物放进熔炉输入槽）。这是批量存取的简便方式，无需选择槽位。**指定它**则会放入该精确槽位：空槽就直接移动；同类物品会合并；**不同物品会交换两个槽位**。
- **`count`**——必须配合 `to`，只移动指定数量，而不是整组。

结果会逐项说明实际移动数量、合并、交换，或未移动的原因（已满、仅输出槽等），因此通常无需重新检查。

**存入/取出整组物品**——省略 `to`，每组一个移动项：
```
transfer moves=[{from:S1}, {from:S2}, …]      # 每组自动路由到另一侧
```

**精确数量**——指定空闲的 `to` 和 `count`：
```
transfer moves=[{from:<iron>, to:<free>, count:10}]
```

**交换两个槽位**——把 `to` 指向装有不同物品的槽位：
```
transfer moves=[{from:<pickaxe>, to:<slot with junk>}]
```

## 读取机器进度

`inspect_gui` 还会显示 `data values: [...]`——这是菜单同步的整数值，与真实 GUI 绘制进度、燃料或能量条时使用的数据相同，独立于物品槽。其含义取决于机器；**原版熔炉**使用 `[litTime, litDuration, cookProgress, cookTotal]`：

- 烧炼百分比 = `cookProgress / cookTotal`；
- `litTime > 0` 表示仍在燃烧。

检查熔炉时：用 `interact_at` 打开 → `inspect_gui` → 查看输入数量和数据值。输出槽出现物品也明确表示已有一次烧炼完成。

## 合成

通过 `transfer` 自己把配方摆进合成格，再取出结果。

1. **`lookup_recipe <item>`**——获取材料，以及有序配方的格子布局。
2. **打开合成格：**
   - **≤2×2 配方**（木板、木棍、火把、工作台）：无需工作台；没有其他 GUI 打开时调用 `inspect_gui`，即可看到自身 2×2 合成格。
   - **3×3 配方**（多数工具等）：对工作台调用 `interact_at button=right`，再调用 `inspect_gui`；结果会以二维图显示槽位编号。
3. **摆放配方**——把布局对齐合成格**左上角**，对每个非空格调用 `transfer {from:<ingredient>, to:<cell>, count:1}`，并在一次调用中批量执行。单材料配方只占**一个**格子，不要填满其他格子。
4. **取出结果**——调用 `transfer {from:<result slot>}`，省略 `to`，让结果自动进入背包并完成合成。需要多件时重复步骤 3–4。
5. 使用工作台时，完成后调用 `close_gui`。

**留意格子位置**——合成格可能比配方更宽。宽 2 格的配方放进 3×3 工作台时使用左上角，并**不是连续槽位号**（例如 2×2 配方会跳过右侧一列）。务必按 `inspect_gui` 的二维图逐格对应；这里最容易出错。

**一次制作多件**——在每个格子放一组材料（`count:N`），然后只需一次 `transfer {from:<result>}`，就会持续合成直到某个格子耗尽。例如：一个格子放 7 个原木，一次取出得到 28 个木板；木棍配方的两个格子各放 8 个木板，一次取出得到 32 根木棍。这比逐次摆放调用少得多。

*示例——木棍（2 个 oak_planks 竖直堆叠）：* `inspect_gui`（自身合成格，假设 2×2 格子为槽位 1–4）→ `transfer moves=[{from:<planks>, to:1, count:1}, {from:<planks>, to:3, count:1}]` → `transfer moves=[{from:<result>}]`。

## 烧炼

烧炼不是合成，没有自动工具；熔炉只是需要你自己装填的几个槽位：

1. 对熔炉、高炉或烟熏炉调用 `interact_at button=right`。
2. 装入原料：`transfer moves=[{from:<raw item>}]`——省略 `to`，让菜单自动放入上方输入槽。
3. 添加燃料：`transfer moves=[{from:<coal>}]`——会自动进入下方燃料槽。**燃料规则**：1 个煤炭/木炭可烧炼 8 件；一根原木或木板约 1.5 件，所以烧炼 N 件时准备约 `⌈N/8⌉` 个煤炭。
4. 调用 `close_gui`，再用 `set_timer` 设置预计完成时间。原版熔炉每件约 10 秒，高炉/烟熏炉约 5 秒。计时器不会占用身体，可以离开做其他事，不要站在旁边反复查询。
5. 收到 `timer` 事件后回来重新打开。计时器只是提醒，不是完成证明；用 `inspect_gui` 查看真实状态（`data values` = `[litTime, litDuration, cookProgress, cookTotal]`）。尚未完成时设置更短的计时器，再次离开。
6. 使用 `transfer moves=[{from:<output>}]` 收取产物（同时获得烧炼经验），然后 `close_gui`。

## 模组机器（手动装料）

自定义机器拥有自己的槽位。单输入机器使用 `transfer moves=[{from:<input>}]` 并省略 `to`，让菜单自动路由。具有多个专用输入槽时，先调用 `inspect_gui`，再为每项指定精确目标：`transfer moves=[{from:<a>, to:<slotA>}, {from:<b>, to:<slotB>}]`。模组的*合成*网格与原版相同：`inspect_gui` 会以二维图显示槽位号，按格子逐一摆放配方（小配方对齐左上角），每个非空格使用一个 `transfer {from, to, count:1}`。

## 常用操作模式

**把某一类物品全部存入最近的箱子：**
```
interact_at button=right x,y,z             （箱子）
inspect_gui                                （在“你的背包”中找到圆石组）
transfer moves=[{from:S1}, {from:S2}, …]   （一次调用将所有组送入箱子）
close_gui
```

**从箱子精确取出 10 个铁：**
```
interact_at button=right x,y,z
inspect_gui                                （找到铁和一个空闲背包槽）
transfer moves=[{from:<iron>, to:<free>, count:10}]
close_gui
```

**清空熔炉输出：** `inspect_gui` → 找到 `[output]` 槽 → `transfer moves=[{from:<output>}]`。

## 错误恢复（你的优势）

`transfer` 会报告每个移动项的结果，而且你随时可以调用 `inspect_gui`，因此不会盲目操作：

- **报告“nothing moved”**——目标已满，或者它是不能放入物品的 `[output]` 槽。改用其他槽位或容器。
- **箱子已满**——`inspect_gui` 中没有空的容器槽。通过扫描或 `<known_blocks>` 找其他箱子，或先取出一些物品。
- **发生了不想要的交换**——指定的 `to` 中已有其他物品。省略 `to` 让菜单自动路由，或选择空槽。
- **“no GUI open”**——尚未打开 GUI，或走出范围后 GUI 已关闭。再次对方块调用 `interact_at`。

完成后一定要调用 `close_gui`（或走远），不要让菜单一直处于打开状态。
