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

import net.createmod.ponder.foundation.PonderIndex;

/**
 * 唯一真正触碰 Ponder 类的“握手点”。
 *
 * <p>单独成类是为了让 Ponder 缺席时的类加载是安全的：
 * 只有 {@code ClientSetup#onClientSetup} 在确认 {@code ponder} 已加载后才会调用这里。</p>
 */
public final class PonderIntegration {

    private PonderIntegration() {
    }

    public static void register() {
        PonderIndex.addPlugin(new AnvilCraftPonderPlugin());
    }
}
