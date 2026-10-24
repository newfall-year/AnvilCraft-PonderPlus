/*
 * AnvilCraft Ponder Plus - Ponder scenes for AnvilCraft
 * Copyright (C) 2026 newfall-year
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the LICENSE file for the full text.
 */

package dev.anvilcraft.ponder.client;

import dev.anvilcraft.ponder.AnvilcraftPonder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 把 AnvilCraft 的物品/方块挂到思索场景上。
 *
 * <p><b>给新物品加思索 = 在 {@link #SCENES} 里加一行</b>，然后：</p>
 * <ol>
 *   <li>把结构方块导出的 nbt 放到
 *       {@code src/main/resources/assets/anvilcraft_ponder_plus/ponder/anvilcraft/<物品路径>.nbt}
 *       （路径 = "anvilcraft/" + 物品路径，不用手写）；</li>
 *   <li>在 {@link AnvilCraftScenes} 里写这个物品的故事板（或复用已有的）；</li>
 *   <li>在 {@code assets/anvilcraft_ponder_plus/lang/{en_us,zh_cn}.json} 补
 *       {@code anvilcraft_ponder_plus.ponder.<物品路径>.header} 与 {@code .text_1/.text_2…}。</li>
 * </ol>
 *
 * <p>Ponder 1.0.69 实测过的约定：component 必须是<b>物品或方块 id</b>（玩家手持/指向它长按 W
 * 才会出提示）；schematic 相对本模组命名空间，最终读
 * {@code assets/<pluginModId>/ponder/<schematicPath>.nbt}；同一 component 连续
 * {@code addStoryBoard} 就是“上一幕/下一幕”的多场景顺序。</p>
 */
public class AnvilCraftPonderPlugin implements PonderPlugin {

    /** 物品路径 → 标题 → 故事板。想加就加一行。 */
    private static final List<ItemScene> SCENES = List.of(
            new ItemScene("magnet_ingot", "Magnet Ingot", AnvilCraftScenes::magnetIngot),
            new ItemScene("giant_anvil", "Giant Anvil", AnvilCraftScenes:: hollowmagnetblock)
            // 例：new ItemScene("royal_steel_ingot", "Royal Steel Ingot", AnvilCraftScenes::royalSteelIngot),
    );

    /** 结构 nbt 的目录前缀：assets/anvilcraft_ponder_plus/ponder/anvilcraft/xxx.nbt */
    private static final String SCHEMATIC_PREFIX = "anvilcraft/";

    @Override
    public String getModId() {
        return AnvilcraftPonder.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        for (ItemScene entry : SCENES) {
            // 标题统一在这里盖章：lang key 前缀 = 物品路径（scene.title 的第一参数）。
            PonderStoryBoard board = (scene, util) -> {
                scene.title(entry.itemPath(), entry.title());
                entry.content().program(scene, util);
            };
            helper.addStoryBoard(ac(entry.itemPath()), SCHEMATIC_PREFIX + entry.itemPath(), board);
        }
    }

    /** 拼 AnvilCraft 的 id，例如 {@code anvilcraft:magnet_ingot}。 */
    static ResourceLocation ac(String path) {
        return ResourceLocation.fromNamespaceAndPath(AnvilcraftPonder.ANVILCRAFT_ID, path);
    }

    private record ItemScene(String itemPath, String title, SceneContent content) {
    }

    /** 故事板正文：和 Ponder 的 {@code program(SceneBuilder, SceneBuildingUtil)} 同签名，便于方法引用。 */
    @FunctionalInterface
    interface SceneContent {
        void program(SceneBuilder scene, SceneBuildingUtil util);
    }
}
