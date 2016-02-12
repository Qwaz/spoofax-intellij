/*
 * Copyright © 2015-2016
 *
 * This file is part of Spoofax for IntelliJ.
 *
 * Spoofax for IntelliJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Spoofax for IntelliJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Spoofax for IntelliJ.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.metaborg.intellij.jps;

import com.google.inject.*;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.*;
import org.jetbrains.jps.model.module.*;

/**
 * A JPS Spoofax module type, used to identify Spoofax JPS modules.
 */
@Singleton
public final class JpsMetaborgModuleType extends JpsElementTypeWithDummyProperties
        implements JpsModuleType<JpsDummyElement> {

    public static final JpsMetaborgModuleType INSTANCE = new JpsMetaborgModuleType();

    private JpsMetaborgModuleType() {
        super();
    }

}