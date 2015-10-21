package org.metaborg.spoofax.intellij.idea.project;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.metaborg.spoofax.intellij.factories.ILanguageImplTableModelFactory;

import javax.swing.*;
import java.awt.*;

public final class LanguageImplPanel extends JPanel {
    @NotNull
    private final ModuleConfigurationState state;
    @NotNull
    private final LanguageImplTableModel model;
    @NotNull
    private final TableView<LanguageImplItem> table;
    @NotNull
    private final ILanguageImplTableModelFactory languageImplTableModelFactory;

    @Inject
    private LanguageImplPanel(
            @Assisted @NotNull final ModuleConfigurationState state,
            @NotNull final ILanguageImplTableModelFactory languageImplTableModelFactory) {
        super(new BorderLayout());

        this.state = state;
        this.languageImplTableModelFactory = languageImplTableModelFactory;

        this.model = this.languageImplTableModelFactory.create(this.state);
        this.table = new TableView<>(this.model);
        new TableViewSpeedSearch<LanguageImplItem>(this.table) {
            @Nullable
            @Override
            protected String getItemText(@NotNull final LanguageImplItem item) {
                return item.language().name();
            }
        };


//        this.table.setSurrendersFocusOnKeystroke(true);
        //this.table.setShowGrid(false);
//        this.table.setDragEnabled(false);
//        this.table.setIntercellSpacing(new Dimension(0, 0));
//        this.table.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        //this.table.setDefaultRenderer(ILanguage.class, new LanguageItemRenderer());
        //JComboBox implEditor = new ComboBox(new CollectionListModel<>(ILanguageImpl.class));
        //this.table.setDefaultEditor(ILanguageImpl.class, new DefaultCellEditor(implEditor));

        add(createTableWithButtons(), BorderLayout.CENTER);
    }

    private JComponent createTableWithButtons() {
        final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(this.table);
        final JPanel panel = decorator.createPanel();
        return panel;
    }
}
