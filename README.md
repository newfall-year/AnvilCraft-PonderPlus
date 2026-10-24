# AnvilCraft：Ponder Plus — 铁砧工艺 × 思索（Ponder）兼容附属

> modid：`anvilcraft_ponder_plus` ｜ 显示名：`AnvilCraft：Ponder Plus`
> 简介：为铁砧工艺添加了思索模组内容

目标：给 **AnvilCraft（铁砧工艺）** 的物品/方块挂上 **Ponder（思索）** 的场景引导。
环境：**Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21 / ModDevGradle 2.0.144**。

> 本文所有 API 签名都对着实际发布的 `Ponder-NeoForge-1.21.1-1.0.69.jar`
> 用 `javap` 核对过，并且 `gradlew build` 通过（Gradle 9.2.1 + JDK 21）。

---

## 1. 架构决策（为什么这样做）

| 决策 | 理由 |
| --- | --- |
| **不依赖 AnvilCraft 的 Java 类**，只按 `ResourceLocation` 挂接场景 | 思索场景只要物品 id + 结构 nbt。不引 AnvilCraft 就不用连它自建的 maven（`server.cjsah.net:1002`），也不会被它的版本号绑死 |
| Ponder 用 `compileOnly`，不打包 | Ponder 是纯客户端库；玩家自己装。jar 里不塞别人的库（避开 jarJar/版本冲突） |
| 与 Ponder 相关的代码全部放 `client` 包 | 专用服务端不会加载这些类，也就不需要 Ponder 存在于服务端 |
| `ModList.get().isLoaded("ponder")` + toml 里 `type="optional"` | 双层软依赖：没装 Ponder 时既不会加载它的类，也不会被加载器拦住 |

## 2. 依赖坐标（已实测）

```groovy
// settings.gradle -> dependencyResolutionManagement.repositories
maven { url = "https://neoforged.forgecdn.net/releases" }    // maven.neoforged.net 的镜像
maven { url = "https://maven.createmod.net" }                // Ponder / Flywheel
maven { url = "https://raw.githubusercontent.com/Fuzss/modresources/main/maven/" }
mavenCentral()
```

```groovy
// build.gradle -> dependencies
compileOnly "net.createmod.ponder:Ponder-NeoForge-${minecraft_version}:${ponder_version}"                 // 1.0.69
compileOnly "dev.engine-room.flywheel:flywheel-neoforge-api-${minecraft_version}:${flywheel_version}"    // 1.0.6
```

dev 运行期（`runClient` 里要真的加载，见 6.1）：

```groovy
runtimeOnly "net.createmod.ponder:Ponder-NeoForge-${minecraft_version}:${ponder_version}"
runtimeOnly("dev.dubhe:anvilcraft-neoforge-${minecraft_version}:${anvilcraft_version}") { transitive = false }
runtimeOnly "dev.anvilcraft.lib:anvillib-neoforge-${minecraft_version}:${anvillib_version}"
runtimeOnly "dev.anvilcraft.resource:ageratum-neoforge-${minecraft_version}:${ageratum_version}"
```

| 坐标 | 当前值 | 说明 |
| --- | --- | --- |
| `ponder_version` | `1.0.69` | [maven-metadata](https://maven.createmod.net/net/createmod/ponder/Ponder-NeoForge-1.21.1/maven-metadata.xml) 上 1.21.1 线最新 |
| `anvilcraft_version` | `1.6.0+snapshot.2657` | 快照号=build 号，**≥2650 用这个**（2651/2655/2657 都是 2650 之后） |
| `anvillib_version` | `2.0.0+snapshot.550` | 抄自 AnvilCraft 2657 的 pom，**1.4.0 线与 2.0.0 不兼容** |
| `ageratum_version` | `0.0.1+build.121` | 同上，AnvilCraft 的引导文档库 |

- Ponder 最新 1.21.1 版本：
  `https://maven.createmod.net/net/createmod/ponder/Ponder-NeoForge-1.21.1/maven-metadata.xml`
- AnvilCraft 全部构建（快照号）：
  `https://server.cjsah.net:1002/maven/releases/dev/dubhe/anvilcraft-neoforge-1.21.1/maven-metadata.xml`
- 换 AnvilCraft 版本时**必须同时改 AnvilLib / Ageratum**：直接看新版 AnvilCraft 的 pom，
  里面写着它要求的那两个版本；只改 AnvilCraft 会在运行时因库不匹配崩。
- 玩家侧运行期依赖：**Ponder 1.0.69+** 与 **Flywheel 1.0.x（CLIENT）**——
  Ponder 自己的 `neoforge.mods.toml` 把 flywheel 声明成必需的 CLIENT 依赖。

### 网络坑（本机实测）

`maven.neoforged.net`、`maven.parchmentmc.org` 在国内经常直接 `Connect timed out`；
换成官方 CDN 镜像 `https://neoforged.forgecdn.net/releases` 即可
（`piston-meta/piston-data`、`repo1.maven.org`、`maven.createmod.net` 实测可用）。
所以本工程把仓库统一声明在 `settings.gradle` 的 `dependencyResolutionManagement`
（`RepositoriesMode.PREFER_SETTINGS`），没走工程级 `repositories {}`。

## 3. 代码结构与职责

```
src/main/java/dev/anvilcraft/ponder/
├── AnvilcraftPonder.java              @Mod 主类：只在 dist==CLIENT 时挂 FMLClientSetupEvent
└── client/
    ├── ClientSetup.java               客户端入口：检查 ponder 是否加载 → 调用 PonderIntegration
    ├── PonderIntegration.java         唯一 import Ponder 类的地方（PonderIndex.addPlugin）
    ├── AnvilCraftPonderPlugin.java    implements PonderPlugin：按物品 id 注册 storyBoard
    └── AnvilCraftScenes.java          故事板本体：program(SceneBuilder, SceneBuildingUtil)
```

### 注册时序（最容易踩死的地方）

1. `FMLClientSetupEvent` ← **我们在这里** `PonderIndex.addPlugin(new AnvilCraftPonderPlugin())`
2. `FMLLoadCompleteEvent`（客户端）→ Ponder 的 `NeoForgePonderClient#loadCompleted`
   → `PonderClient.modLoadCompleted()` → `PonderIndex.registerAll()`
   → 遍历所有插件调 `registerScenes(...)`，这时才把 storyBoard 真正落进 `PonderSceneRegistry`

即：**必须早于 FMLLoadComplete**。注册阶段结束后再 `addStoryBoard` 会抛
`IllegalStateException("Registration Phase has already ended!")`。

## 4. 场景 nbt 从哪来

Ponder 只认 `StructureTemplate` 格式的 nbt，读取路径固定为：

```
assets/<你的 modid>/ponder/<schematicPath>.nbt
```

本示例：
- `addStoryBoard("anvilcraft/magnet_ingot", ...)` → `assets/anvilcraft_ponder_plus/ponder/anvilcraft/magnet_ingot.nbt`
- `addStoryBoard("anvilcraft/giant_anvil", ...)` → `assets/anvilcraft_ponder_plus/ponder/anvilcraft/giant_anvil.nbt`

导出步骤：

1. 创造模式搭好要展示的结构（尽量紧凑，最小角对齐结构方块原点）。
2. 放 **结构方块（Structure Block）** → 模式 `SAVE` → 框选 → 命名（如 `magnet_ingot`）→ 保存。
3. 从存档目录 `generated/minecraft/structures/magnet_ingot.nbt` 复制出来。
4. 粘到上面第 2 条列出的路径（`ponder/` 下可建子目录）。

**没有 nbt 会怎样**：Ponder 打开该场景时 `LOGGER.error` 并退回空结构——
底板和文字照常，但看不到方块。所以先放 nbt，再调镜头。

### 4.1 nbt 的硬性注意事项（均对 1.21.1 / Ponder 1.0.69 源码核过）

1. **尺寸上限 48³。** `StructureBlockEntity.MAX_SIZE_PER_AXIS = 48`，结构方块每轴最多 48 格；
   但思索场景实际用 5~15 格最舒服（`configureBasePlate` 的 size）。越大，每次打开都要
   `loadSchematic` + `placeInWorld` 重建一遍虚拟世界，越卡。
2. **不做 DataFix，也没有版本迁移。** Ponder 直接 `NbtIo.read` + `StructureTemplate.load`，
   而 `load()` 里**根本不读 `DataVersion`**。所以：**用 1.21.1 导出**，跨版本搬会把改名过的方块认错。
3. **未知方块 id 会静默变成空气**（这是最阴的坑）。`NbtUtils.readBlockState` 里
   `optional.isEmpty() → Blocks.AIR.defaultBlockState()`，**一行日志都不打**。
   后果：AnvilCraft 版本换了 / 玩家没装 AnvilCraft 时，nbt 里那些方块直接消失，场景看着“空了一半”，
   却没有任何报错。排查看 `logs/latest.log` 是不会有的，只能靠对比。
4. **未知 blockstate 属性只 WARN**：`Unable to read property: ...`，属性回落到默认值。
   所以版本升级后请检查日志里这条。
5. **空气和结构空位**：结构方块保存时**空气也会写进 nbt**（体积偏大但无害）；
   想表达“这里什么都别放”，在结构里填 **结构空位（structure void）**——
   `fillFromWorld(...)` 明确把 `STRUCTURE_VOID` 作为“跳过”的方块。
6. **实体会被一起导入**：结构方块默认保存实体（`ignoreEntities` 未勾选时），
   而 Ponder 用默认 `StructurePlaceSettings`（不忽略实体）→ 结构里的实体/画/盔甲架会出现在场景里。
   不需要就在结构方块里勾 **Ignore entities**。
7. **三个轴都必须 ≥1**，否则 `placeInWorld` 直接返回 false（等于空场景）。
8. **文件名必须与 `schematicPath` 逐字一致**（大小写敏感、可带子目录、建议全小写）。
   本工程的 `schematicPath` = `"anvilcraft/" + 物品路径`，所以文件必须是
   `ponder/anvilcraft/<物品路径>.nbt`。放错位置的典型症状就是“打开思索是空的 + 日志里一条 error”。
9. **必须放 `assets/`，不是 `data/`**；结构方块导出的就是 gzip 压缩 nbt，直接放进去即可。
10. **nbt 是二进制资源，不会被 Groovy 模板碰**：`processResources` 只对 `neoforge.mods.toml` 做
    `expand`，所以 nbt 里的字节不会被误解析。改完 nbt 要重新 `build`（或 dev 里重启 `runClient`）。
11. **建议：复杂机器别整块存进 nbt。** 带大量方块实体/运行状态的 AnvilCraft 多方块机器，
    在思索的虚拟世界里没有完整 tick 语义，看起来会“静止/错位”；
    这类场景更适合 nbt 只放**静态外壳**，再用 `setBlock` / `modifyBlockEntity` 指令演出动作。

## 5. 语言文件与 lang key 规则

`scene.title("magnet_ingot", "Magnet Ingot")` 的**第一个参数**决定 lang key 前缀，
命名空间 = 插件 `getModId()`（也就是本模组 id）：

```
anvilcraft_ponder_plus.ponder.magnet_ingot.header     ← 标题
anvilcraft_ponder_plus.ponder.magnet_ingot.text_1     ← 第 1 条 showText(...).text(...)
anvilcraft_ponder_plus.ponder.magnet_ingot.text_2     ← 第 2 条，依此类推
```

> `text("English...")` 里写的英文会在 Ponder“编辑模式”下被回写进 lang；
> 正常游玩读的是 `I18n.get(langKey)`，所以**必须自己提供 lang 文件**
> （本工程已带 `en_us` / `zh_cn`）。

## 6. 构建与测试

```powershell
# 构建（首次会跑 NeoForm 反编译，约 3~5 分钟）
.\gradlew.bat build          # → build/libs/anvilcraft_ponder_plus-0.1.0.jar

# 开发客户端（想在 dev 环境里真的看到思索效果，需要：
#   1) 把两个 compileOnly 改成 implementation
#   2) 把 AnvilCraft / Flywheel 的 jar 放进 run/mods/）
.\gradlew.bat runClient
```

### 6.1 在 IntelliJ IDEA 里打开 / 运行

IDEA 里没有“打开某个游戏实例”这回事。你能跑起来的是一个**独立的开发实例**：
`runClient` 会在工程下建 `run/`（自带 `options.txt`、`saves`、`logs`、`mods`、`config`），
和你平时玩的 Prism / CurseForge 实例完全隔离。

1. `File | Open…` → 选 **`E:\ModTest\anvilcraft-ponder-plus`**（含 `build.gradle` 的那层）→ `Trust Project`。
2. `Settings | Build, Execution, Deployment | Build Tools | Gradle`：
   - **Gradle JVM = 21**（本机 `D:\jdk21`）；Gradle 9.2.1 支持 17~25，但工程 toolchain 是 21。
   - 首次 Sync 会下载 NeoForge 并跑一次 NeoForm（3~5 分钟，属正常）。
3. 右上角运行下拉里应有 moddev 自动生成的 **`client` / `server` / `data`** 三个配置，
   选 `client` 点运行/调试即可。没出现时：Gradle 工具窗 → 右键工程 → `Reload Gradle Project`；
   或直接跑 `Tasks → neoforge runs → runClient`。
4. 想给配置改名：`neoForge { runs { client { ideName = "Run Dev Client" } } }`。
5. 进游戏后是**离线账户，用户名 `Dev`**；想用正版账号登录才有皮肤/挂机记录：
   `client { devLogin = true }`（首次启动会要求去 microsoft.com/link 输码）。

**给 dev 实例（`run/`）加 mod 的两种方式**，二选一：

**方式 A：加进 Gradle（推荐，已配好，`gradlew dependencies` 实测可解析）**

```groovy
// build.gradle
dependencies {
    // 编译期
    compileOnly "net.createmod.ponder:Ponder-NeoForge-1.21.1:1.0.69"
    compileOnly "dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6"

    // dev 运行期（只进 runtimeClasspath，不进发布 pom）
    runtimeOnly "net.createmod.ponder:Ponder-NeoForge-1.21.1:1.0.69"                                   // 自带 flywheel
    runtimeOnly("dev.dubhe:anvilcraft-neoforge-1.21.1:1.6.0+snapshot.2657") { transitive = false }
    runtimeOnly "dev.anvilcraft.lib:anvillib-neoforge-1.21.1:2.0.0+snapshot.550"
    runtimeOnly "dev.anvilcraft.resource:ageratum-neoforge-1.21.1:0.0.1+build.121"
}
```

仓库（`settings.gradle`）：

```groovy
maven { url = "https://maven.createmod.net" }                       // Ponder / Flywheel
maven { url = "https://server.cjsah.net:1002/maven/releases/" }     // AnvilCraft / AnvilLib
```

要点：

- Ponder 的 pom 把 `flywheel-neoforge-1.21.1` 声明成 runtime 依赖，**不用手加 flywheel**（`-api` 那个只是编译用）。
- AnvilCraft 必须 `transitive = false`——和[官方附属模板](https://github.com/Anvil-Dev/AnvilCraftAddonTemplate)
  一致。它 pom 里的 `com.tterrag.registrate:Registrate:MC1.21-1.3.0+62` 在 tterrag 仓库根本没有
  （该仓库最新只到 `MC1.20-1.3.11`），开着 transitive 会直接解析失败。AnvilLib 要单独加。
- 加 JEI 调试时：`maven { url = "https://maven.blamejared.com/" }` +
  `runtimeOnly "mezz.jei:jei-1.21.1-neoforge:19.21.0.247"`。

**方式 B：往 `run/mods/` 丢 jar**

`runClient` 第一次跑完会生成 `E:\ModTest\anvilcraft-ponder-plus\run\`，把 Modrinth/CurseForge 下的
1.21.1 NeoForge 版 jar 放进 `run/mods/`（目录没有就自己建），重启 run 即可。
注意两个坑：① jar-in-jar 打包进去的库在 dev 里不一定展开，AnvilCraft 这种要另放 AnvilLib；
② 不要把你平时整合包的一堆 mod 全拷进来，版本冲突会让你以为是自己代码的问题。

不想折腾依赖时最省事的验证法：先在 dev 里把场景临时挂到 `minecraft:anvil`
（把 `ac("magnet_ingot")` 换成 `ResourceLocation.withDefaultNamespace("anvil")`）确认
UI + nbt + 文字都正常，再换回 AnvilCraft 的 id，用正式 jar 在真机实例里实测。

真机实例（不是 dev 的 `run/`）验证清单，`mods/` 里放：

- [ ] `anvilcraft`（铁砧工艺 1.21.1 线）
- [ ] `ponder` 1.0.69+ 与 `flywheel` 1.0.x
- [ ] 本模组 jar

进游戏手持 `anvilcraft:magnet_ingot` **长按 W** → 出现思索界面；标题栏 “Magnet Ingot”，
场景里 nbt 结构从上方落下并显示文字。

## 7. 常见坑

| 现象 | 原因 / 处理 |
| --- | --- |
| 编译报 `program(...)` 不存在 / 需要 `begin` | Ponder 版本差异。1.0.x 是 `program(SceneBuilder, SceneBuildingUtil)`；旧版 Create 内置 Ponder 是 `begin`。以 jar 为准：`javap -p -classpath ponder.jar net.createmod.ponder.api.scene.PonderStoryBoard` |
| `Missing ModLoader` | 1.21.1 的 `neoforge.mods.toml` 必须显式写 `modLoader="javafml"` + `loaderVersion`（本工程已写） |
| `modId` 报非法 | mod id 只允许 `[a-z][a-z0-9_]{1,63}`，**不能有连字符**（本工程用 `anvilcraft_ponder_plus`，jar 名可以带连字符） |
| 装了 Ponder 长按 W 没反应 | ① `addPlugin` 晚于 FMLLoadComplete；② 物品 id 写错（`anvilcraft:magnet_ingot` 不是 `anvilcraft:magnet`）；③ 玩家没装 Ponder |
| 专用服务端启动崩 `NoClassDefFoundError: net/createmod/...` | Ponder 相关代码漏在非 client 类里，或 `PonderIntegration` 没被 `ModList.isLoaded` 保护 |
| 场景里方块是紫黑格 / 空 | nbt 路径或命名空间不对（`assets/<你的 modid>/ponder/...`，是**你的** modid，不是 anvilcraft） |
| `maven.neoforged.net ... Connection timed out` | 换 `https://neoforged.forgecdn.net/releases` 镜像 |

## 8. 下一步可以加的东西

- **思索标签（tag）**：`registerTags(PonderTagRegistrationHelper<ResourceLocation>)`
  `helper.registerTag(id).title(...).description(...).icon(...).addToIndex().register();`
  再用 `addStoryBoard(component, path, board, tagId)` 把场景挂进标签页。
- **共用文本**：`registerSharedText(h -> h.registerSharedText("stamp", "Stamp"))`，
  场景里 `.sharedText("stamp")` 调用，key 为 `<ns>.ponder.shared.<key>`。
- **多段场景**：同一 component 连续 `addStoryBoard(...)`；`StoryBoardEntry`
  另有 `orderBefore/orderAfter` 调顺序、`highlightTag/highlightAllTags`。
- **要引用 AnvilCraft 类时**（例如给它的方块实体做 `modifyBlockEntity` 动画）：
  走官方附属模板 `Anvil-Dev/AnvilCraftAddonTemplate`（Architectury Loom），
  仓库 `https://server.cjsah.net:1002/maven`，坐标 `dev.dubhe:anvilcraft-neoforge-1.21.1:<版本>`。

## 9. 工程杂项

- 1.21.1 必须 `modLoader="javafml"` + `loaderVersion`（javafml 默认化是更晚的 21.x）。
- 本线未启用 Parchment；需要参数名可自行加回。
- 无 `pack.mcmeta`（官方 MDK 现状）。
- 首次构建 NeoForm 反编译 3~5 分钟属正常（R9）。

## 10. 给别的物品加思索：固定五步

代码已经做成“**一个物品 = 一行**”，加场景不需要碰注册逻辑。

**① 找到物品 id**

- 游戏里 `/give @s anvilcraft:` 让自动补全列表出来，挑 id；
- 或直接查 AnvilCraft 的 lang 键：`"item.anvilcraft.xxx"` / `"block.anvilcraft.xxx"`；
- 官方 1.6 线现有 200+ 个物品 id（`magnet_ingot`、`anvil_hammer`、`royal_steel_ingot`、
  `giant_anvil`、`celestial_forging_anvil`…）。

**② 导出结构 nbt**

1. 创造模式搭好要展示的最小结构（3~7 格见方最舒服，最小角对上结构方块原点）；
2. 结构方块 → `SAVE` → 框选 → 命名 = **物品路径**（如 `magnet_ingot`）→ 保存；
3. 从 `<存档>/generated/minecraft/structures/magnet_ingot.nbt` 复制到
   `src/main/resources/assets/anvilcraft_ponder_plus/ponder/anvilcraft/magnet_ingot.nbt`。

> 路径规则写死在插件里：`SCHEMATIC_PREFIX = "anvilcraft/"` + 物品路径。
> 所以 nbt 文件名必须和物品路径一致，否则运行时只会打一行 error + 空场景。

**③ 写故事板**（`AnvilCraftScenes.java`）

```java
public static void royalSteelIngot(SceneBuilder scene, SceneBuildingUtil util) {
    scene.configureBasePlate(0, 0, 5);
    scene.showBasePlate();
    scene.idle(5);
    scene.world().showSection(util.select().everywhere(), Direction.UP);
    scene.idle(15);
    scene.overlay().showText(80)
            .text("Royal Steel Ingots are stamped into blocks to upgrade them.")
            .pointAt(util.vector().centerOf(2, 1, 2))
            .attachKeyFrame();
    scene.idle(90);
    scene.markAsFinished();
}
```

常用指令清单见 `AnvilCraftScenes.java` 文件末尾的注释（取区域 / 取坐标 / 显隐方块 /
改方块 / 方块实体动画 / 文字 / 高亮 / 镜头）。

**④ 注册一行**（`AnvilCraftPonderPlugin.java`）

```java
private static final List<ItemScene> SCENES = List.of(
        new ItemScene("magnet_ingot", "Magnet Ingot", AnvilCraftScenes::magnetIngot),
        new ItemScene("giant_anvil",  "Giant Anvil",  AnvilCraftScenes::giantAnvil),
        new ItemScene("royal_steel_ingot", "Royal Steel Ingot", AnvilCraftScenes::royalSteelIngot)  // ← 新增
);
```

**⑤ 补 lang**（`assets/anvilcraft_ponder_plus/lang/en_us.json` + `zh_cn.json`）

```json
"anvilcraft_ponder_plus.ponder.royal_steel_ingot.header": "Royal Steel Ingot",
"anvilcraft_ponder_plus.ponder.royal_steel_ingot.text_1": "Royal Steel Ingots are stamped into blocks to upgrade them."
```

key 规则：`<本模组 id>.ponder.<scene.title 的第一参数>.<header|text_1|text_2…>`。
`text_1` 就是第 1 个 `showText(...).text(...)`，按代码里的出现顺序递增——**改文本顺序要同步改 key**。

跑 `gradlew build`，把 jar 丢进带 AnvilCraft + Ponder 的实例，手持该物品长按 W 验收。
多幕场景：同一物品再 `addStoryBoard` 一次即可，或直接用 `StoryBoardEntry.orderBefore/orderAfter` 排序。


## 11. 文档现状（英文 / 中文）

**结论：Ponder 没有任何叙事型开发文档（中英文都没有），但官方发布了 API javadoc 工件。**

### 11.1 英文：官方只有两样东西

1. **README** — https://github.com/Creators-of-Create/Ponder
   只讲 Gradle 接入（仓库、坐标、各平台写法），**不涉及任何 API 用法**。
2. **API javadoc（只有 jar，没有托管网站）** — maven 上同时发布了 `-javadoc.jar`，
   解开就是完整 javadoc HTML 站点：

   ```
   https://maven.createmod.net/net/createmod/ponder/Ponder-NeoForge-1.21.1/1.0.69/Ponder-NeoForge-1.21.1-1.0.69-javadoc.jar
   ```

   下载解压 → 打开 `index.html`；或在 IDEA 里让 Gradle 自动挂：

   ```groovy
   idea { module { downloadJavadoc = true; downloadSources = true } }
   ```

   ⚠️ 覆盖很不均匀：`PonderPlugin` / `PonderSceneRegistrationHelper` 只有一行式说明，
   `WorldInstructions` 这类**一条注释都没有**。所以 javadoc 只够“确认签名”，语义得读源码。

### 11.2 英文：真正好用的是源码样例（事实上的活文档）

1. **Create 本体的 25+ 个场景文件** —— 最大、最完整的样例集：
   [`infrastructure/ponder/scenes/`](https://github.com/Creators-of-Create/Create/tree/mc1.21.1/dev/src/main/java/com/simibubi/create/infrastructure/ponder/scenes)
   （`KineticsScenes` 57KB、`ProcessingScenes` 37KB、`RedstoneScenes` 33KB、`ArmScenes`/`BeltScenes`/`SteamScenes`…）
2. **Ponder 自带示例**：[`TemplateScenes.java`](https://github.com/Creators-of-Create/Ponder/blob/mc1.21.1/dev/common/src/main/java/net/createmod/ponder/foundation/content/TemplateScenes.java)（最小骨架）、
   [`DebugScenes.java`](https://github.com/Creators-of-Create/Ponder/blob/mc1.21.1/dev/common/src/main/java/net/createmod/ponder/foundation/content/DebugScenes.java)（各种花式指令）。
3. **[Create wiki · Pondering](https://createmod.wiki/wiki/Ponder)** —— 玩家向，讲长按 W / 索引 / 标签的 UI 概念，
   不含 addon API，但能帮你判断“做出来的场景该长什么样”。

> `-sources.jar` 也一起发布了；开 `downloadSources` 后能在 IDEA 里直接跳进 Ponder / Create 的实现。

### 11.3 中文：唯一成体系的教程 + 一个必须知道的坑

1. **[MC百科《Ponder-为你的模组添加思索引导》](https://www.mcmod.cn/post/5404.html)**
   —— 目前唯一成体系的中文教程，环境正好是 1.21.1 NeoForge + Ponder 1.0.60。
   ⚠️ 它写故事板用 `begin(...)`，而 **1.0.x 实际是 `program(...)`**（对着 1.0.69 的 jar 核过）。
2. **[AnvilCraft 官方文档](https://www.anvilcraft.dev/posts/docs/addon/index.html)**（可切中文）
   —— 查铁砧工艺物品 id / 机制 / 附属开发套路。
3. 本仓库的 §4（nbt 路径）、§5（lang key）、§10（加物品五步）就是上面这些 + 实测归纳出来的，
   可以直接当团队内部文档用。

## 12. 改简介 / modid / 名称

### 12.1 三样东西分别在哪

| 想改什么 | 改哪里 | 连带影响 |
| --- | --- | --- |
| **简介 description** | `neoforge.mods.toml` 第 13 行的 `description=`（**直接写死，不要走 `${mod_description}`**，原因见 12.2） | 模组列表里显示的描述 |
| **显示名 displayName** | `neoforge.mods.toml` 的 `displayName=`（中文同理写死），或 `gradle.properties:24` 的 `mod_name`（仅限纯英文） | Mods 列表里的名字；**jar 文件名不变** |
| **modid** | ① `gradle.properties:23` `mod_id`；② `AnvilcraftPonder.java:23` 的 `MODID` 常量；③ `src/main/resources/assets/<modid>/` 目录改名；④ 两个 lang 文件的 key 前缀 | jar 名（`build.gradle:12 archivesName = mod_id`）、lang 命名空间、Ponder 的 lang key 前缀 |

不用动的：`mod_group_id` / Java 包名 `dev.anvilcraft.ponder`（和 modid 无关，除非你要重新发布到 Maven）；
`settings.gradle:13` 的 `rootProject.name` 只影响 Gradle / IDEA 里的工程显示名。

`modid` 规则：`^[a-z][a-z0-9_]{1,63}$` —— **小写、不能有连字符、不能有中文**。所以 `anv-pon` 只能当目录名/jar 名前缀，
不能当 `mod_id`（要写 `anv_pon`）。

### 12.2 两个必须知道的编码坑（都实测复现过）

1. **中文写进 `gradle.properties` 会变乱码。** Gradle 按 `java.util.Properties` 的规矩以 **ISO-8859-1**
   读这个文件，所以 `mod_description=测试用的简介` 展开进 toml 后变成 `æµè¯ç¨çç®ä»…`。
   解法：`gradle.properties` 只放 ASCII，**中文简介直接写在 `neoforge.mods.toml` 里**（TOML 是 UTF-8，
   实测 jar 内中文正常）；实在要放 properties 就用 `\u6d4b\u8bd5` 这种 Unicode 转义。
2. **别用 Windows PowerShell 5.1 的 `Set-Content -Encoding utf8` 改这些文件。** 它会写出 UTF-8 **BOM**，
   Gradle 立刻报 `Unexpected character: '\ufeff' @ line 1`；命令里直接写的英文以外的字面量也会按 GBK(936)
   解码成乱码。用 IDEA / VS Code 存成「UTF-8 无 BOM」，或
   `[IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding($false)))`。

### 12.3 改完怎么验

```powershell
.\gradlew.bat build
# 看 jar 里的元数据是否真的变了（Windows 自带 jar/javap 都在 JDK 21 的 bin 下）
& "D:\jdk21\bin\jar.exe" xf build\libs\<新jar名>.jar META-INF/neoforge.mods.toml
Get-Content META-INF/neoforge.mods.toml -Encoding utf8
```

> ⚠️ **改完 `gradle.properties` 里那些占位符的值之后，`processResources` 有可能仍报 `UP-TO-DATE`，
> jar 里还是旧值**（`expand` 读的是配置期快照，Gradle 追踪不到）。所以改名之后第一次打包，
> 请用 `gradlew clean build`，或 `gradlew processResources --rerun-tasks`——否则你会以为改失败了。
> 上面那条“解包看 toml”就是唯一可靠的验证方式。

实测过整条链路：把 modid 改成 `anvil_ponder_test`、名字改成 `Anvil Ponder Test`、资源目录与 lang key 同步、
中文简介写死在 toml → `build` 通过，产物 `anvil_ponder_test-0.1.0.jar` 内
`modId` / `displayName` / `description` / `assets/anvil_ponder_test/lang/*.json` 全部正确。

## 13. 许可证与致谢

**本项目采用 [GNU Lesser General Public License v3.0](LICENSE)（SPDX：`LGPL-3.0-or-later`）。**

| 文件 | 内容 | 为什么两个都要 |
| --- | --- | --- |
| [`LICENSE`](LICENSE) | LGPL-3.0 正文（165 行，取自 GitHub Licenses API） | GitHub 靠它识别仓库许可证 |
| [`COPYING`](COPYING) | GPL-3.0 正文（674 行） | LGPLv3 是以 GPLv3 为基础的“附加许可”，§3/§4 明确要求随附 GPL 全文 |

源文件头部统一带：

```java
/*
 * Copyright (C) 2026 newfall-year
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * ...
 */
```

**想改成 `LGPL-3.0-only`（不授权“或任何更新版本”）**：三处同步改
`gradle.properties` 的 `mod_license`、所有源文件头的 SPDX、本 README 这一节。目前选 `or-later`，
是为了跟上游 AnvilCraft 保持一致（它的 LICENSE 写的是 “version 3 … or any later version”）。

**第三方**（都**没有**被打包进本模组的 jar）：

- **AnvilCraft（铁砧工艺）** —— LGPL-3.0-or-later，© Gugle / Anvil-Dev。
  仅作运行期软依赖（`type="optional"`），本仓库不含它的任何源码或资源。
  AnvilCraft 官方文档也要求：一旦你直接包含或修改它的源码，你的附属**必须**用 LGPL-3.0。
- **Ponder（思索）** —— MIT，© simibubi / Creators-of-Create。`compileOnly` 引用其 API。
- **Flywheel** —— 由 Ponder 在客户端自带，本模组只 `compileOnly` 引用其 API 工件。

致谢：AnvilCraft 提供物品与机制，Create/Ponder 提供思索框架；场景结构（`.nbt`）与故事板由本项目自行制作。
