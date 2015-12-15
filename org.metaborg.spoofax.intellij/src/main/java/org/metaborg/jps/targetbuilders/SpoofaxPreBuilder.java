/*
 * Copyright © 2015-2015
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

package org.metaborg.jps.targetbuilders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.tools.ant.BuildListener;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.metaborg.core.MetaborgException;
import org.metaborg.core.build.BuildInput;
import org.metaborg.core.build.BuildInputBuilder;
import org.metaborg.core.build.IBuildOutput;
import org.metaborg.core.build.NewBuildInputBuilder;
import org.metaborg.core.build.dependency.IDependencyService;
import org.metaborg.core.build.dependency.INewDependencyService;
import org.metaborg.core.build.paths.ILanguagePathService;
import org.metaborg.core.logging.InjectLogger;
import org.metaborg.core.messages.IMessage;
import org.metaborg.core.processing.ITask;
import org.metaborg.core.project.ILanguageSpecPaths;
import org.metaborg.core.project.ILanguageSpecPathsService;
import org.metaborg.core.project.ILanguageSpecService;
import org.metaborg.core.project.ProjectException;
import org.metaborg.core.transform.CompileGoal;
import org.metaborg.jps.project.MetaborgJpsProject;
import org.metaborg.spoofax.core.processing.SpoofaxProcessorRunner;
import org.metaborg.spoofax.core.project.ISpoofaxLanguageSpecPaths;
import org.metaborg.spoofax.core.project.ISpoofaxLanguageSpecPathsService;
import org.metaborg.spoofax.core.project.configuration.ISpoofaxLanguageSpecConfigService;
import org.metaborg.spoofax.core.project.settings.ISpoofaxProjectSettingsService;
import org.metaborg.spoofax.core.project.settings.SpoofaxProjectSettings;
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector;
import org.metaborg.jps.project.JpsProjectService;
import org.metaborg.spoofax.intellij.languages.LanguageManager;
import org.metaborg.spoofax.meta.core.LanguageSpecBuildInput;
import org.metaborg.spoofax.meta.core.MetaBuildInput;
import org.metaborg.spoofax.meta.core.SpoofaxLanguageSpecBuilder;
import org.metaborg.spoofax.meta.core.SpoofaxMetaBuilder;
import org.metaborg.spoofax.meta.core.ant.AntSLF4JLogger;
import org.slf4j.Logger;
import org.spoofax.interpreter.terms.IStrategoTerm;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;

/**
 * Builder executed before Java compilation.
 */
@Singleton
public final class SpoofaxPreBuilder extends SpoofaxBuilder<SpoofaxPreTarget> {

    private final SpoofaxLanguageSpecBuilder builder;
    private final LanguageManager languageManager;
    private final ILanguagePathService languagePathService;
    private final INewDependencyService dependencyService;
    private final SpoofaxProcessorRunner processorRunner;
    private final ISpoofaxLanguageSpecPathsService languageSpecPathsService;
    @InjectLogger
    private Logger logger;

    @Inject
    public SpoofaxPreBuilder(
            final SpoofaxPreTargetType targetType,
            final SpoofaxLanguageSpecBuilder builder,
            final JpsProjectService projectService,
            final ILanguageSpecService languageSpecService,
            final ISpoofaxLanguageSpecConfigService spoofaxLanguageSpecConfigService,
            final LanguageManager languageManager,
            final ILanguagePathService languagePathService,
            final INewDependencyService dependencyService,
            final SpoofaxProcessorRunner processorRunner,
            final ISpoofaxLanguageSpecPathsService languageSpecPathsService) {
        super(targetType, projectService, languageSpecService, spoofaxLanguageSpecConfigService);
        this.builder = builder;
        this.languageManager = languageManager;
        this.languagePathService = languagePathService;
        this.dependencyService = dependencyService;
        this.processorRunner = processorRunner;
        this.languageSpecPathsService = languageSpecPathsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getPresentableName() {
        return "Spoofax pre-Java builder";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void build(
            final SpoofaxPreTarget target,
            final DirtyFilesHolder<SpoofaxSourceRootDescriptor, SpoofaxPreTarget> holder,
            final BuildOutputConsumer consumer,
            final CompileContext context) throws ProjectBuildException, IOException {

        try {
            final LanguageSpecBuildInput metaInput = getBuildInput(target.getModule());

            this.languageManager.loadMetaLanguages();

            initialize(metaInput, context);
            generateSources(metaInput, context);
            regularBuild(metaInput, context);
            compilePreJava(metaInput, null, new AntSLF4JLogger(), context);

        } catch (ProjectException e) {
            this.logger.error("An unexpected project exception occurred.", e);
            throw new ProjectBuildException(e);
        } catch (Exception e) {
            this.logger.error("An unexpected exception occurred.", e);
            throw new ProjectBuildException(e);
        }

    }

    /**
     * Executes the initialize meta-build step.
     *
     * @param metaInput The meta build input.
     * @param context   The compile context.
     * @throws Exception
     * @throws ProjectBuildException
     */
    private void initialize(final LanguageSpecBuildInput metaInput, final CompileContext context) throws
            ProjectBuildException {
        try {
            context.checkCanceled();
            context.processMessage(BuilderUtils.formatProgress(0f, "Initializing {}", metaInput.languageSpec));

            this.builder.initialize(metaInput);
        } catch (FileSystemException e) {
            throw new ProjectBuildException("Error initializing", e);
        }
    }

    /**
     * Executes the generate-sources meta-build step.
     *
     * @param metaInput The meta build input.
     * @param context   The compile context.
     * @throws Exception
     * @throws ProjectBuildException
     */
    private void generateSources(
            final LanguageSpecBuildInput metaInput,
            final CompileContext context) throws Exception, ProjectBuildException {
        try {
            context.checkCanceled();
            context.processMessage(BuilderUtils.formatProgress(0f,
                                                               "Generating Spoofax sources for {}",
                                                               metaInput.languageSpec));

            ISpoofaxLanguageSpecPaths paths = this.languageSpecPathsService.get(metaInput.languageSpec);
            this.builder.generateSources(metaInput, paths);
        } catch (Exception e) {
            throw new ProjectBuildException(e.getMessage(), e);
        }
    }

    /**
     * Executes the regular build meta-build step.
     *
     * @param metaInput The meta build input.
     * @param context   The compile context.
     * @throws Exception
     * @throws ProjectBuildException
     */
    private void regularBuild(
            final LanguageSpecBuildInput metaInput,
            final CompileContext context) throws Exception, ProjectBuildException {

        context.processMessage(BuilderUtils.formatProgress(0f, "Analyzing and transforming {}", metaInput.languageSpec));
        context.checkCanceled();

        final BuildInput input = getBuildInput(metaInput);

        try {
            final ITask<IBuildOutput<IStrategoTerm, IStrategoTerm, IStrategoTerm>> task = processorRunner
                    .build(input, null, null)
                    .schedule()
                    .block();

            if (!task.cancelled()) {
                @Nullable final IBuildOutput<?, ?, ?> output = task.result();
                if (output != null) {
                    for (IMessage msg : output.allMessages()) {
                        context.processMessage(BuilderUtils.formatMessage("Spoofax", msg));
                    }
                    for (IMessage msg : output.extraMessages()) {
                        context.processMessage(BuilderUtils.formatMessage("Spoofax", msg));
                    }
                    // TODO:
                    if (!output.success()) {
                        throw new ProjectBuildException("Compilation finished but failed.");
                    }
                } else {
                    throw new ProjectBuildException("Compilation finished with no output.");
                }
            } else {
                throw new ProjectBuildException("Compilation cancelled.");
            }
        } catch (InterruptedException e) {
            throw new ProjectBuildException("Interrupted!", e);
        }
    }

    /**
     * Executes the pre-Java compile meta-build step.
     *
     * @param metaInput The meta build input.
     * @param context   The compile context.
     * @throws Exception
     * @throws ProjectBuildException
     */
    private void compilePreJava(
            final LanguageSpecBuildInput metaInput,
            @Nullable final URL[] classpath,
            @Nullable final BuildListener listener,
            final CompileContext context) throws Exception, ProjectBuildException {
        context.checkCanceled();
        context.processMessage(BuilderUtils.formatProgress(0f, "Building language project {}", metaInput.languageSpec));
        this.builder.compilePreJava(metaInput, classpath, listener, null);
    }

    /**
     * Creates the {@link BuildInput} for the project.
     *
     * @param metaInput           The meta input.
     * @return The created {@link BuildInput}.
     * @throws ProjectBuildException
     */
    private BuildInput getBuildInput(final LanguageSpecBuildInput metaInput) throws
            ProjectBuildException {
        BuildInput input;
        try {
            input = new NewBuildInputBuilder(metaInput.languageSpec)
                    .withDefaultIncludePaths(true)
                    .withSourcesFromDefaultSourceLocations(true)
                    .withSelector(new SpoofaxIgnoresSelector())
//                    .withMessagePrinter()
                    .withThrowOnErrors(false)
                    .withPardonedLanguageStrings(metaInput.config.pardonedLanguages())
                    .addTransformGoal(new CompileGoal())
                    .build(this.dependencyService, this.languagePathService);
        } catch (MetaborgException e) {
            throw new ProjectBuildException(e);
        }
        return input;
    }

}