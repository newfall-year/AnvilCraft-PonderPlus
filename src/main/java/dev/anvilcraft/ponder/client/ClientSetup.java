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
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：把本模组的 {@link net.createmod.ponder.api.registration.PonderPlugin} 塞给 Ponder。
 *
 * <p>时序是硬约束：Ponder {@code 1.0.69} 在 <b>FMLLoadCompleteEvent</b>（mod bus，客户端）里
 * 调 {@code PonderClient.modLoadCompleted() → PonderIndex.registerAll()}，
 * 一次性把已注册插件的场景全部登记。所以必须在它<b>之前</b>调用 {@code PonderIndex.addPlugin}——
 * {@code FMLClientSetupEvent} 正好在 FMLLoadComplete 之前触发，是所有 Create 系附属的通用走法。</p>
 */
public final class ClientSetup {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnvilCraftPonder/ClientSetup");

    private ClientSetup() {
    }

    /** 由主类在主入口里按 dist 注册到 mod bus（避免 @EventBusSubscriber.bus 的弃用告警）。 */
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 软依赖：没装 Ponder 就直接跳过，PonderIntegration 这个类也就不会被加载。
        if (!ModList.get().isLoaded(AnvilcraftPonder.PONDER_ID)) {
            LOGGER.info("Ponder not installed - skipping ponder scene registration.");
            return;
        }
        PonderIntegration.register();
    }
}
