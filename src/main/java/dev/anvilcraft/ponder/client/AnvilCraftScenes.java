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

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

/**
 * 故事板（storyBoard）正文：一幕思索里的镜头、方块出现顺序与文字。
 *
 * <p>签名固定为 {@code program(SceneBuilder, SceneBuildingUtil)}——Ponder 1.0.x 已把
 * 老的 {@code begin} 改名成 {@code program}；方法引用会直接当故事板用，
 * 名字/签名对不上时编译期就报错，这是校验版本最快的办法。</p>
 *
 * <p>标题（{@code scene.title(...)}）由 {@link AnvilCraftPonderPlugin} 统一盖章，
 * 所以这里只写正文。坐标系：结构方块导出的 nbt 以 (0,0,0) 为最小角，
 * {@code configureBasePlate(x, z, size)} 的 size 一般取“结构最大边长 + 2”。</p>
 */
public final class AnvilCraftScenes {

    private AnvilCraftScenes() {
    }

    /** 磁铁锭（anvilcraft:magnet_ingot）。 */
    public static void magnetIngot(SceneBuilder scene, SceneBuildingUtil util) {
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        // 整个结构从上方落下。
        scene.world().showSection(util.select().everywhere(), Direction.UP);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Magnet Ingots are an AnvilCraft material. Stamping them onto a block shapes what it will attract.")
                .pointAt(util.vector().centerOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(90);

        scene.rotateCameraY(45);
        scene.idle(30);

        scene.markAsFinished();
    }

    public static void hollowmagnetblock(SceneBuilder scene, SceneBuildingUtil util) {

    }

    // ── 下面这些指令是写新场景时最常用的，抄着改即可 ──────────────────────────
    //
    // 取区域：util.select().everywhere() / .layer(0) / .layers(1,3)
    //        / .fromTo(0,0,0, 4,2,4) / .position(2,1,2) / .column(2,2)
    // 取坐标：util.grid().at(2, 1, 2)          → BlockPos
    //        util.vector().centerOf(2,1,2)      → Vec3（方块中心）
    //        util.vector().topOf(2,1,2)         → Vec3（方块顶面中心）
    //        util.vector().blockSurface(pos, Direction.UP) → 贴面点
    // 方块出现/消失：scene.world().showSection(sel, Direction.UP)
    //              scene.world().hideSection(sel, Direction.DOWN)
    //              scene.world().setBlock(pos, state, true)        （true = 有动画）
    //              scene.world().replaceBlocks(sel, state, true)
    //              scene.world().destroyBlock(pos)
    //              scene.world().modifyBlockEntity(pos, MyBE.class, be -> ...)
    // 文字/高亮：scene.overlay().showText(80).text("...").pointAt(vec).attachKeyFrame()
    //          scene.overlay().showOutlineWithText(sel, 80).colored(PonderPalette.RED)
    //          scene.overlay().showControls(vec, Pointing.DOWN, 40).rightClick()
    //          scene.overlay().showLine(PonderPalette.GREEN, from, to, 60)
    // 镜头/节奏：scene.rotateCameraY(45) / .scaleSceneView(1.2f) / .addKeyframe()
    //          scene.idle(10) / .idleSeconds(1)
    // 结束：scene.markAsFinished()
}
