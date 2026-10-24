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

package dev.anvilcraft.ponder;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * AnvilCraft × Ponder 兼容附属。
 *
 * <p>本模组本身不注册任何物品/方块，也不引用 AnvilCraft 的 Java 类：
 * 思索场景只按 {@code ResourceLocation}（如 {@code anvilcraft:magnet_ingot}）挂接，
 * 所以 AnvilCraft 只作为可选依赖（soft dependency），装不装都不影响本模组加载。</p>
 *
 * <p>真正与 Ponder 打交道（引用 Ponder 自己的类）的代码全部放在 {@code client} 包里，
 * 且只在客户端被加载，专用服务端不会碰 Ponder 的类。</p>
 */
@Mod(AnvilcraftPonder.MODID)
public class AnvilcraftPonder {

    public static final String MODID = "anvilcraft_ponder_plus";
    /** AnvilCraft 的 mod id。 */
    public static final String ANVILCRAFT_ID = "anvilcraft";
    /** Ponder 的 mod id。 */
    public static final String PONDER_ID = "ponder";

    private static final Logger LOGGER = LogUtils.getLogger();

    public AnvilcraftPonder(IEventBus modEventBus) {
        LOGGER.info("[{}] init", MODID);

        // 只在物理客户端挂载 Ponder 注册逻辑；服务端连 ClientSetup 这个类都不会加载。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(dev.anvilcraft.ponder.client.ClientSetup::onClientSetup);
        }
    }
}
