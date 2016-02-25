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

package org.metaborg.intellij.idea.projects;

import com.google.inject.*;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.ui.configuration.*;
import com.intellij.openapi.startup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.*;
import org.apache.commons.vfs2.*;
import org.jetbrains.annotations.*;
import org.metaborg.core.language.*;
import org.metaborg.core.project.*;
import org.metaborg.intellij.*;
import org.metaborg.intellij.idea.graphics.*;
import org.metaborg.intellij.idea.projects.newproject.*;
import org.metaborg.intellij.idea.sdks.*;
import org.metaborg.intellij.logging.*;
import org.metaborg.intellij.logging.LoggerUtils;
import org.metaborg.intellij.resources.*;
import org.metaborg.spoofax.core.project.*;
import org.metaborg.spoofax.core.project.configuration.*;
import org.metaborg.spoofax.generator.language.*;
import org.metaborg.spoofax.generator.project.*;
import org.metaborg.util.log.*;

import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 * Builds a new Spoofax module.
 */
@Singleton
public final class MetaborgModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {

    private final IIntelliJResourceService resourceService;
    private final IIdeaProjectFactory projectFactory;
    private final IIdeaProjectService projectService;
    private final IIconManager iconManager;
    private final INewModuleWizardStepFactory wizardStepFactory;
    private final ISpoofaxLanguageSpecConfigBuilder configBuilder;
    private final ISpoofaxLanguageSpecPathsService pathsService;
    private final MetaborgModuleType moduleType;
    @InjectLogger
    private ILogger logger;

    @Nullable
    private List<Pair<String, String>> sourcePaths;

    private String name = "Untitled";
    private String extension = "u";
    private LanguageIdentifier languageId = new LanguageIdentifier(
            "org.example",
            "untitled",
            LanguageVersion.parse("1.0.0-SNAPSHOT")
    );


    /**
     * Gets the name of the module.
     *
     * @return The name.
     */
    @Override
    public String getName() { return this.name; }

    /**
     * Sets the name of the module.
     *
     * @param name The name.
     */
    @Override
    public void setName(final String name) { this.name = name; }

    /**
     * Gets the extension of the language.
     *
     * @return The language file extension.
     */
    public String getExtension() { return this.extension; }

    /**
     * Sets the extension of the language.
     *
     * @param extension The language file extension.
     */
    public void setExtension(final String extension) { this.extension = extension; }

    /**
     * Gets the language ID of the module.
     *
     * @return The language identifier.
     */
    public LanguageIdentifier getLanguageIdentifier() {
        return this.languageId;
    }

    /**
     * Sets the language ID of the module.
     *
     * @param languageId The language identifier; or <code>null</code>.
     */
    public void setLanguageIdentifier(final LanguageIdentifier languageId) {
        this.languageId = languageId;
    }


    @Inject
    private MetaborgModuleBuilder(
            final IIntelliJResourceService resourceService,
            final IIdeaProjectFactory projectFactory,
            final IIdeaProjectService projectService,
            final ISpoofaxLanguageSpecPathsService pathsService,
            final ISpoofaxLanguageSpecConfigBuilder configBuilder,
            final INewModuleWizardStepFactory wizardStepFactory,
            final IIconManager iconManager,
            final MetaborgModuleType moduleType) {
        super();
        this.resourceService = resourceService;
        this.projectFactory = projectFactory;
        this.projectService = projectService;
        this.pathsService = pathsService;
        this.configBuilder = configBuilder;
        this.wizardStepFactory = wizardStepFactory;
        this.iconManager = iconManager;
        this.moduleType = moduleType;
    }

    /**
     * Gets the wizard step shown under the SDK selection.
     *
     * @param context          The wizard context.
     * @param parentDisposable The parent disposable.
     * @return The wizard step.
     */
    @Override
    @Nullable
    public final ModuleWizardStep getCustomOptionsStep(
            final WizardContext context,
            final Disposable parentDisposable) {

        return this.wizardStepFactory.create(this, context);
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(
            final WizardContext wizardContext, final ModulesProvider modulesProvider) {
        return ModuleWizardStep.EMPTY_ARRAY;
    }

    @Override
    @Nullable
    public final ModuleWizardStep modifySettingsStep(final SettingsStep settingsStep) {
        return getModuleType().modifySettingsStep(settingsStep, this);
    }

    @Override
    public final ModuleWizardStep modifyProjectTypeStep(final SettingsStep settingsStep) {
        @Nullable final ModuleWizardStep wizardStep = StdModuleTypes.JAVA.modifyProjectTypeStep(settingsStep, this);
        assert wizardStep != null;
        return wizardStep;
    }

    /**
     * Setups the root model.
     *
     * @param rootModel The root model.
     * @throws ConfigurationException
     */
    @Override
    public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {

        // Set the content roots.
        this.logger.debug("Adding content and source roots.");
        @Nullable final ContentEntry contentEntry = doAddContentEntryAndSourceRoots(rootModel);
        if (contentEntry != null) {
            // TODO: Get this from the paths interface.
            contentEntry.addExcludeFolder(contentEntry.getUrl() + File.separator + ".idea");
            contentEntry.addExcludeFolder(contentEntry.getUrl() + File.separator + ".cache");
            contentEntry.addExcludeFolder(contentEntry.getUrl() + File.separator + "lib");
            contentEntry.addExcludeFolder(contentEntry.getUrl() + File.separator + "src-gen");
            contentEntry.addExcludeFolder(contentEntry.getUrl() + File.separator + "include");
        }
        this.logger.info("Added content and source roots.");

        // Set the SDK.
        this.logger.debug("Setting SDK.");
        setSdk(rootModel);
        this.logger.info("Set SDK.");

        // Set the module.
        final Module module = rootModel.getModule();
        final Project project = module.getProject();

//        // Set the dependencies.
//        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206116919-Using-ModifiableRootModel-addLibraryEntry-still-doesn-t-set-the-library-dependency-correctly
//        // https://github.com/consulo/consulo-ruby/blob/5111ab5ed8b71ba208cafafa09c0bf86395e532c/src/org/jetbrains/plugins/ruby/jruby/JRubySdkTableListener.java
//        this.logger.debug("Adding dependencies.");
//        final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
//        final String libName = "org.metaborg.core-2.0.0-SNAPSHOT";
//        @Nullable Library library = libraryTable.getLibraryByName(libName);
//        if (library == null) {
//            library = libraryTable.createLibrary("Metaborg Core");
//            final Library.ModifiableModel libraryModel = library.getModifiableModel();
//            libraryModel.addJarDirectory();
//            // TODO
//            this.logger.info("Library was NULL.");
//        }
//        else {
//            this.logger.info("Library was not NULL: {}", library);
//            rootModel.addLibraryEntry(library);
//        }
//        this.logger.info("Added dependencies.");

        // Generate the project files.
        ApplicationManager.getApplication().runWriteAction(() -> {
            MetaborgModuleBuilder.this.logger.debug("Generating project files.");
            // Generate the module structure (files and directories).
            final FileObject location = MetaborgModuleBuilder.
                    this.resourceService.resolve(getContentEntryPath());
            final IdeaLanguageSpecProject ideaProject = MetaborgModuleBuilder.
                    this.projectFactory.create(module,location);
            MetaborgModuleBuilder.this.projectService.open(ideaProject);
            WriteCommandAction.runWriteCommandAction(
                    project, "Create new Spoofax module", null, () -> generateModuleStructure(ideaProject));
            MetaborgModuleBuilder.this.logger.info("Generated project files.");
        });

    }

    /**
     * Runs a runnable once the specified project has been initialized.
     *
     * @param project  The project.
     * @param runnable The runnable.
     */
    private static void runWhenInitialized(final Project project, final Runnable runnable) {
        if (project.isDisposed())
            // Project is disposed. Nothing to do.
            return;

        final Application application = ApplicationManager.getApplication();
        if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
            // Runnable cannot be run in background. Just run it.
            runnable.run();
        } else if (!project.isInitialized()) {
            // Run runnable once project has initialized.
            StartupManager.getInstance(project).registerPostStartupActivity(DisposeAwareRunnable.create(
                    runnable,
                    project
            ));
        } else if (DumbService.isDumbAware(runnable)) {
            // The runnable is dumb aware. Just run it.
            runnable.run();
        } else {
            // The runnable is not dumb aware. Run it when applicable.
            DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(runnable, project));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSuitableSdkType(final SdkTypeId sdkType) {
        return sdkType instanceof MetaborgSdkType;
    }

    /**
     * Sets the SDK.
     *
     * @param rootModel The root model.
     */
    private void setSdk(final ModifiableRootModel rootModel) {
        if (this.myJdk != null) {
            // An SDK was selected in the wizard.
            rootModel.setSdk(this.myJdk);
        } else {
            // No SDK was selected in the wizard.
            rootModel.inheritSdk();
        }
    }

    /**
     * Generates the module directory structure and files.
     *
     * @param languageSpec The language specification.
     */
    private void generateModuleStructure(
            final ILanguageSpec languageSpec) {
        final String name = getName();
        final LanguageIdentifier identifier = getLanguageIdentifier();

        try {
            final ISpoofaxLanguageSpecConfig config = this.configBuilder
                    .reset()
                    .withIdentifier(identifier)
                    .withName(name)
                    .build(languageSpec.location());

            // TODO: Use ISpoofaxLanguageSpecPathsService instead.
            final ISpoofaxLanguageSpecPaths paths = new SpoofaxLanguageSpecPaths(languageSpec.location(), config);
            final LanguageSpecGeneratorScope scope = new LanguageSpecGeneratorScope(config, paths);
//            // TODO: Get from SDK.
//            generatorSettings.setMetaborgVersion("1.5.0-SNAPSHOT");
            // FIXME: Factory?
            final NewLanguageSpecGenerator newGenerator = new NewLanguageSpecGenerator(
                    scope,
                    new String[]{getExtension()}
            );
            newGenerator.generateAll();
            // FIXME: Factory?
            final LanguageSpecGenerator generator = new LanguageSpecGenerator(scope);
            generator.generateAll();

//            // TODO: Get the source folders and exclude folders from the generator, and add them to the `contentEntry`.
//            final VirtualFile f = resourceService.unresolve(project.location().resolveFile("editor/java/"));
//            contentEntry.addSourceFolder(f, false, "");


        } catch (ProjectException | IOException e) {
            throw LoggerUtils.exception(this.logger, UnhandledException.class, "Unexpected unhandled exception.", e);
        }
    }

    /**
     * Gets the module type.
     *
     * @return The module type.
     */
    @Override
    public final ModuleType getModuleType() {
        return this.moduleType;
    }

    /**
     * Gets the module's big icon.
     *
     * @return The big icon.
     */
    // TODO: Use project's ILanguage facet defined icon.
    @Override
    public final Icon getBigIcon() {
        return this.iconManager.getDefaultIcon();
    }

    /**
     * Gets the module's normal icon.
     *
     * @return The normal icon.
     */
    // TODO: Use project's ILanguage facet defined icon.
    @Override
    public final Icon getNodeIcon() {
        return this.iconManager.getDefaultIcon();
    }

    /**
     * Gets the module builder's description.
     *
     * @return The module builder's description.
     */
    @Override
    public final String getDescription() {
        return "Creates a new <b>Spoofax Language</b> module, used for developing domain-specific languages " +
                "using the <b>Spoofax Language Workbench</b>.";
    }

    /**
     * Gets the module builder's presentable name.
     * <p>
     * This name is shown in the <em>New Project</em> and <em>New Module</em> wizards.
     *
     * @return The module builder's presentable name.
     */
    @Override
    public final String getPresentableName() {
        return "Spoofax Language";
    }

    /**
     * Gets the module builder's group name.
     * <p>
     * I suspect module builders with the same group name are grouped
     * in the <em>New Project</em> and <em>New Module</em> wizards.
     *
     * @return The group name.
     */
    @Override
    public final String getGroupName() {
        return "Spoofax";
    }

    /**
     * Gets a list of source paths.
     *
     * @return A list of (path, packagePrefix) pairs.
     * @throws ConfigurationException
     */
    @Override
    @Nullable
    public List<Pair<String, String>> getSourcePaths() throws ConfigurationException {
        if (this.sourcePaths == null) {
            final List<Pair<String, String>> paths = new ArrayList<>();
            final String path = getContentEntryPath() + File.separator + "editor" + File.separator + "java";
            final boolean foldersCreated = new File(path).mkdirs();
            if (!foldersCreated) {
                this.logger.error("Failed to create some folders in path: {}", path);
            }
            paths.add(Pair.create(path, ""));
            return paths;
        }
        return this.sourcePaths;
    }

    /**
     * Sets the list of source paths.
     *
     * @param sourcePaths A list of (path, packagePrefix) pairs.
     */
    @Override
    public void setSourcePaths(@Nullable final List<Pair<String, String>> sourcePaths) {
        this.sourcePaths = sourcePaths != null ? new ArrayList<>(sourcePaths) : null;
    }

    /**
     * Adds a source path.
     *
     * @param sourcePathInfo A (path, packagePrefix) pair.
     */
    @Override
    public void addSourcePath(final Pair<String, String> sourcePathInfo) {
        if (this.sourcePaths == null) {
            this.sourcePaths = new ArrayList<>();
        }
        this.sourcePaths.add(sourcePathInfo);
    }

    @Nullable
    protected ContentEntry doAddContentEntryAndSourceRoots(final ModifiableRootModel rootModel) throws
            ConfigurationException {
        // Add the content entry path as a content root.
        @Nullable final ContentEntry contentEntry = doAddContentEntry(rootModel);
        if (contentEntry == null)
            return null;

        @Nullable final List<Pair<String, String>> sourcePaths = getSourcePaths();

        if (sourcePaths == null)
            return null;

        for (final Pair<String, String> sourcePath : sourcePaths) {
            final String first = sourcePath.first;
            try {
                VfsUtil.createDirectories(first);
            } catch (final IOException e) {
                throw new UnhandledException(e);
            }
            @Nullable final VirtualFile sourceRoot = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(first));
            if (sourceRoot != null) {
                contentEntry.addSourceFolder(sourceRoot, false, sourcePath.second);
            }
        }

        return contentEntry;
    }

    @Nullable
    @Override
    public List<Module> commit(@NotNull final Project project,
                               final ModifiableModuleModel model,
                               final ModulesProvider modulesProvider) {
        final LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject());
        @Nullable final Boolean aDefault = extension.getDefault();
        final LanguageLevelProjectExtension instance = LanguageLevelProjectExtension.getInstance(project);
        if(aDefault != null && !aDefault) {
            instance.setLanguageLevel(extension.getLanguageLevel());
            instance.setDefault(false);
        } else {
            instance.setDefault(true);
        }

        return super.commit(project, model, modulesProvider);
    }
}