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

package org.metaborg.intellij.idea.gui.languagesettings;

import com.google.inject.*;
import com.intellij.openapi.project.*;
import org.jetbrains.annotations.*;
import org.metaborg.intellij.idea.*;

import javax.swing.*;

/**
 * Metaborg language configuration dialog.
 */
public final class MetaborgProjectLanguagesSettings extends LanguagesSettings {

    private MetaborgProjectLanguagesConfigurableForm form;

    /**
     * This instance is created by IntelliJ's plugin system.
     * Do not call this constructor manually.
     */
    public MetaborgProjectLanguagesSettings(final Project project) {
        super(project);
        SpoofaxIdeaPlugin.injector().injectMembers(this);
    }

    @Inject
    @SuppressWarnings("unused")
    protected void inject() {
    }

    /**
     * {@inheritDoc}
     */
    @Nls
    @Override
    public String getDisplayName() {
        return "Metaborg Languages";
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public JComponent createComponent() {
        if (this.form == null) {
            this.form = new MetaborgProjectLanguagesConfigurableForm();
        }
        return this.form.getComponent();
    }


    /**
     * Called on form load and Cancel.
     */
    @Override
    public void reset() {
        super.reset();
        if (this.form == null)
            return;
        this.form.getLanguagesPanel().attachController(this);
        updateLanguagesList();
    }

    /**
     * Called on form unload.
     */
    @Override
    public void disposeUIResources() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateLanguagesList() {
        if (this.form == null)
            return;
        this.form.getLanguagesPanel().setLanguages(getLanguages());
    }

}
