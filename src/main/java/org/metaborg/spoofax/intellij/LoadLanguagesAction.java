package org.metaborg.spoofax.intellij;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.metaborg.core.language.ILanguage;
import org.metaborg.core.language.ILanguageImpl;
import org.metaborg.core.language.ILanguageService;
import org.metaborg.spoofax.intellij.idea.IdeaPlugin;
import org.metaborg.spoofax.intellij.idea.languages.IIdeaLanguageManager;
import org.metaborg.spoofax.intellij.languages.LanguageManager;

/**
 * Created by daniel on 10/8/15.
 */
public class LoadLanguagesAction extends AnAction {

    @NotNull
    private final LanguageManager languageManager;
    @NotNull
    private final ILanguageService languageService;
    @NotNull
    private final IIdeaLanguageManager ideaLanguageManager;
    @NotNull
    private final IProjectLanguageIdentifierService languageIdentifierService;

    public LoadLanguagesAction() {
        this.languageManager = IdeaPlugin.injector().getInstance(LanguageManager.class);
        this.languageService = IdeaPlugin.injector().getInstance(ILanguageService.class);
        this.ideaLanguageManager = IdeaPlugin.injector().getInstance(IIdeaLanguageManager.class);
        this.languageIdentifierService = IdeaPlugin.injector().getInstance(IProjectLanguageIdentifierService.class);
    }

    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        // TODO: Get IProject for Project

        //this.languageManager.loadMetaLanguages();
        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (ILanguage language : this.languageService.getAllLanguages()) {
                ILanguageImpl implementation = this.languageIdentifierService.identify(language, null);
                this.ideaLanguageManager.load(implementation);
            }
        });

    }
}
