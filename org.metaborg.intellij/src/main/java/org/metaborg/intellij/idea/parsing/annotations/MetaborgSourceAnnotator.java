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

package org.metaborg.intellij.idea.parsing.annotations;

import com.intellij.openapi.fileTypes.*;
import org.apache.commons.vfs2.FileObject;
import org.jetbrains.annotations.Nullable;
import org.metaborg.core.analysis.*;
import org.metaborg.core.context.ContextException;
import org.metaborg.core.context.IContext;
import org.metaborg.core.context.IContextService;
import org.metaborg.core.language.ILanguageIdentifierService;
import org.metaborg.core.language.ILanguageImpl;
import org.metaborg.core.messages.IMessage;
import org.metaborg.core.messages.MessageSeverity;
import org.metaborg.core.processing.analyze.*;
import org.metaborg.core.processing.parse.*;
import org.metaborg.core.project.IProject;
import org.metaborg.core.source.ISourceRegion;
import org.metaborg.core.syntax.*;
import org.metaborg.intellij.UnhandledException;
import org.metaborg.intellij.idea.parsing.SourceRegionUtil;
import org.metaborg.intellij.idea.parsing.elements.*;
import org.metaborg.intellij.idea.projects.IIdeaProjectService;
import org.metaborg.intellij.logging.InjectLogger;
import org.metaborg.intellij.logging.LoggerUtils;
import org.metaborg.intellij.resources.IIntelliJResourceService;
import org.metaborg.util.concurrent.*;
import org.metaborg.util.iterators.*;
import org.metaborg.util.log.ILogger;

import com.google.common.base.Objects;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import rx.*;

/**
 * Annotates metaborg source files.
 *
 * @param <P> The parser term type.
 * @param <A> The analysis term type.
 */
@Singleton
public final class MetaborgSourceAnnotator<P, A>
        extends ExternalAnnotator<MetaborgSourceAnnotationInfo, AnalysisFileResult<P, A>> {

    private final IContextService contextService;
    private final IIdeaProjectService projectService;
    private final IIntelliJResourceService resourceService;
    private final ILanguageIdentifierService identifierService;
    private final IParseResultRequester<P> parseResultRequester;
    private final IAnalysisResultRequester<P, A> analysisResultRequester;
    private final IAnalysisResultUpdater<P, A> analysisResultProcessor;
    private final IAnalysisService<P, A> analyzer;
    @InjectLogger
    private ILogger logger;

    /**
     * Initializes a new instance of the {@link MetaborgSourceAnnotator} class.
     *
     * @param contextService The context service.
     * @param projectService The project service.
     * @param resourceService The resource service.
     * @param identifierService The identifier service.
     * @param analysisResultRequester The analysis result requester.
     */
    @Inject
    public MetaborgSourceAnnotator(
            final IContextService contextService,
            final IIdeaProjectService projectService,
            final IIntelliJResourceService resourceService,
            final ILanguageIdentifierService identifierService,
            final IParseResultRequester<P> parseResultRequester,
            final IAnalysisResultRequester<P, A> analysisResultRequester,
            final IAnalysisService<P, A> analyzer,
            final IAnalysisResultUpdater<P, A> analysisResultProcessor
    ) {
        super();
        this.contextService = contextService;
        this.projectService = projectService;
        this.resourceService = resourceService;
        this.identifierService = identifierService;
        this.parseResultRequester = parseResultRequester;
        this.analysisResultRequester = analysisResultRequester;
        this.analyzer = analyzer;
        this.analysisResultProcessor = analysisResultProcessor;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public MetaborgSourceAnnotationInfo collectInformation(final PsiFile file) {
        throw new UnsupportedOperationException("This method is not expected to be called, ever.");
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public MetaborgSourceAnnotationInfo collectInformation(final PsiFile file, final Editor editor,
                                                           final boolean hasErrors) {

        this.logger.debug("Collecting annotation information for file: {}", file);

        final MetaborgSourceAnnotationInfo info;
        @Nullable final IProject project = this.projectService.get(file);
        if (project == null) {
            this.logger.warn("Cannot annotate source code; cannot get language specification for resource {}. " +
                    "Is the file excluded?", Objects.firstNonNull(file.getVirtualFile(), "<unknown>"));
            return null;
        }

        try {
            final FileObject resource = this.resourceService.resolve(file.getVirtualFile());
            @Nullable final ILanguageImpl language = this.identifierService.identify(resource, project);
            if (language == null) {
                this.logger.warn("Skipping annotation. Could not identify the language of resource: {}", resource);
                return null;
            }
            final IContext context = this.contextService.get(resource, project, language);
            final String text = editor.getDocument().getImmutableCharSequence().toString();
            info = new MetaborgSourceAnnotationInfo(resource, text, context);
        } catch (final ContextException e) {
            throw LoggerUtils.exception(this.logger, UnhandledException.class,
                    "Unexpected unhandled exception.", e);
        }

        this.logger.info("Collected annotation information for file: {}", file);

        return info;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public AnalysisFileResult<P, A> doAnnotate(final MetaborgSourceAnnotationInfo info) {
//        return this.analysisResultRequester.get(info.resource());

        this.logger.debug("Requesting analysis result for file: {}", info.resource());

        final AnalysisFileResult<P, A> result = this.analysisResultRequester.request(
                info.resource(),
                info.context(),
                info.text()
        ).toBlocking().single();

        this.logger.info("Requested analysis result for file: {}", info.resource());

        return result;

//        final IContext context = info.context();
//
//        final ParseResult<P> parseResult =
//                this.parseResultRequester.request(info.resource(), context.language(), info.text())
//                        .toBlocking().single();
//
//        final AnalysisResult<P, A> analysisResult;
//        try(IClosableLock lock = context.write()) {
//            this.analysisResultProcessor.invalidate(parseResult.source);
//            try {
//                analysisResult = this.analyzer.analyze(Iterables2.singleton(parseResult), context);
//            } catch(final AnalysisException e) {
//                this.analysisResultProcessor.error(info.resource(), e);
//                throw new RuntimeException(e);
//            }
//            this.analysisResultProcessor.update(analysisResult);
//        }
//
//
//        analysisResult.fileResults.iterator().next().
//        return analysisResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void apply(
            final PsiFile file,
            final AnalysisFileResult<P, A> analysisResult,
            final AnnotationHolder holder) {

        this.logger.debug("Applying analysis result to file: {}", file);

        for (final IMessage message : analysisResult.messages) {
            addAnnotation(message, file, holder);
        }

        this.logger.info("Applied analysis result to file: {}", file);
    }

    /**
     * Adds an annotation.
     *
     * @param message The message.
     * @param file The PSI file.
     * @param holder The annotation holder.
     */
    private void addAnnotation(final IMessage message, final PsiFile file, final AnnotationHolder holder) {
        final TextRange range = getRange(message.region(), file);
        final HighlightSeverity severity = getSeverity(message.severity());
        // NOTE: We can add a HTML tooltip if we want.
        holder.createAnnotation(severity, range, message.message());
    }

    /**
     * Gets the text range that corresponds to the specified source region.
     *
     * @param region The source region.
     * @param file The PSI file.
     * @return The text range.
     */
    private TextRange getRange(@Nullable final ISourceRegion region, final PsiFile file) {
        final TextRange range;
        if (region != null) {
            range = SourceRegionUtil.toTextRange(region);
        } else {
            // The message affects the entire source file.
            // FIXME: Is this actually desirable? Do we want to annotate the entire file?
            range = file.getTextRange();
        }
        return range;
    }

    /**
     * Gets the {@link HighlightSeverity} that corresponds to the specified {@link MessageSeverity}.
     * @param messageSeverity The severity (of the message).
     * @return The severity (of the annotation).
     */
    private HighlightSeverity getSeverity(final MessageSeverity messageSeverity) {
        switch (messageSeverity) {
            case ERROR: return HighlightSeverity.ERROR;
            case WARNING: return HighlightSeverity.WARNING;
            case NOTE: return HighlightSeverity.INFORMATION;
            default: return HighlightSeverity.INFORMATION;
        }
    }
}
