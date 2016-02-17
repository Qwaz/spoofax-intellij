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

package org.metaborg.intellij.idea.gui.languagespanel;

import org.metaborg.core.language.*;

import javax.annotation.*;
import javax.swing.*;

/**
 * A language implementation node.
 */
public final class LanguageImplNode extends TreeNodeWithValue<ILanguageImpl, LanguageImplNode>
        implements ITreeNodeWithIcon, ILanguageTreeNode<ILanguageImpl> {

    private final LanguageIdentifier id;

    /**
     * Initializes a new instance of the {@link LanguageImplNode} class.
     *
     * @param languageImpl The language implementation.
     */
    public LanguageImplNode(final ILanguageImpl languageImpl) {
        super(languageImpl);
        this.id = languageImpl.id();
    }

    /**
     * Initializes a new instance of the {@link LanguageImplNode} class.
     *
     * @param id The language identifier.
     */
    public LanguageImplNode(final LanguageIdentifier id) {
        super(null);
        this.id = id;
    }

    /**
     * Gets the language identifier.
     *
     * @return The language identifier.
     */
    public LanguageIdentifier id() {
        return this.id;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Object getValueOfColumn(final ModelColumnInfo<LanguageImplNode> column) {
        return this.id();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Icon getIcon() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public String getName() {
        return this.id().id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public String getGroupId() {
        return this.id().groupId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public LanguageVersion getVersion() {
        return this.id().version;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public LanguageStatus getStatus() {
        return null;
    }
}