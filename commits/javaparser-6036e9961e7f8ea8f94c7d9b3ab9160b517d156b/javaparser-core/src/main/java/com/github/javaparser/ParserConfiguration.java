/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2016 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.validator.Java8Validator;
import com.github.javaparser.ast.validator.ProblemReporter;
import com.github.javaparser.ast.validator.Validator;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.SymbolResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The configuration that is used by the parser.
 * Note that this can be changed even when reusing the same JavaParser instance.
 * It will pick up the changes.
 */
public class ParserConfiguration {
    private boolean storeTokens = true;
    private boolean attributeComments = true;
    private boolean doNotAssignCommentsPrecedingEmptyLines = true;
    private boolean doNotConsiderAnnotationsAsNodeStartForCodeAttribution = false;
    private boolean lexicalPreservationEnabled = false;
    private SymbolResolver symbolResolver = null;
    private int tabSize = 1;
    private Validator validator = new Java8Validator();
    private final List<ParseResult.PostProcessor> postProcessors = new ArrayList<>();

    public ParserConfiguration() {
        postProcessors.add((result, configuration) -> {
            if (configuration.isLexicalPreservationEnabled()) {
                if (configuration.isLexicalPreservationEnabled()) {
                    result.ifSuccessful(LexicalPreservingPrinter::setup);
                }
            }
        });
        postProcessors.add((result, configuration) -> {
            if (configuration.isAttributeComments()) {
                result.ifSuccessful(resultNode -> result
                        .getCommentsCollection().ifPresent(comments ->
                                new CommentsInserter(configuration).insertComments(resultNode, comments.copy().getComments())));
            }
        });
        postProcessors.add((result, configuration) ->
                getValidator().ifPresent(validator ->
                        validator.accept(result.getResult().get(), new ProblemReporter(newProblem -> result.getProblems().add(newProblem)))));
        postProcessors.add((result, configuration) -> configuration.getSymbolResolver().ifPresent(symbolResolver ->
                result.ifSuccessful(resultNode -> {
                    if (resultNode instanceof CompilationUnit) {
                        resultNode.setData(Node.SYMBOL_RESOLVER_KEY, symbolResolver);
                    }
                })
        ));
    }

    public boolean isAttributeComments() {
        return attributeComments;
    }

    /**
     * Whether to run CommentsInserter, which will put the comments that were found in the source code into the comment
     * and javadoc fields of the nodes it thinks they refer to.
     */
    public ParserConfiguration setAttributeComments(boolean attributeComments) {
        this.attributeComments = attributeComments;
        return this;
    }

    public boolean isDoNotAssignCommentsPrecedingEmptyLines() {
        return doNotAssignCommentsPrecedingEmptyLines;
    }

    public ParserConfiguration setDoNotAssignCommentsPrecedingEmptyLines(boolean doNotAssignCommentsPrecedingEmptyLines) {
        this.doNotAssignCommentsPrecedingEmptyLines = doNotAssignCommentsPrecedingEmptyLines;
        return this;
    }

    public boolean isDoNotConsiderAnnotationsAsNodeStartForCodeAttribution() {
        return doNotConsiderAnnotationsAsNodeStartForCodeAttribution;
    }

    public ParserConfiguration setDoNotConsiderAnnotationsAsNodeStartForCodeAttribution(boolean doNotConsiderAnnotationsAsNodeStartForCodeAttribution) {
        this.doNotConsiderAnnotationsAsNodeStartForCodeAttribution = doNotConsiderAnnotationsAsNodeStartForCodeAttribution;
        return this;
    }

    public ParserConfiguration setStoreTokens(boolean storeTokens) {
        this.storeTokens = storeTokens;
        if (!storeTokens) {
            setAttributeComments(false);
        }
        return this;
    }

    public boolean isStoreTokens() {
        return storeTokens;
    }

    public int getTabSize() {
        return tabSize;
    }

    /**
     * When a TAB character is encountered during parsing, the column position will be increased by this value.
     * By default it is 1.
     */
    public ParserConfiguration setTabSize(int tabSize) {
        this.tabSize = tabSize;
        return this;
    }

    public Optional<Validator> getValidator() {
        return Optional.of(validator);
    }

    /**
     * The language level validator to run directly after parsing.
     * By default it is {@link Java8Validator}
     * If it is null, all validation is turned off and only hard parse errors will be reported.
     */
    public ParserConfiguration setValidator(Validator validator) {
        this.validator = validator;
        return this;
    }

    /**
     * Disabled by default.
     * When this is enabled, LexicalPreservingPrinter.print can be used to reproduce
     * the original formatting of the file.
     */
    public ParserConfiguration setLexicalPreservationEnabled(boolean lexicalPreservationEnabled) {
        this.lexicalPreservationEnabled = lexicalPreservationEnabled;
        return this;
    }

    public boolean isLexicalPreservationEnabled() {
        return lexicalPreservationEnabled;
    }

    /**
     * Retrieve the SymbolResolver to be used while parsing, if any.
     */
    public Optional<SymbolResolver> getSymbolResolver() {
        return Optional.ofNullable(symbolResolver);
    }

    /**
     * Set the SymbolResolver to be injected while parsing.
     */
    public ParserConfiguration setSymbolResolver(SymbolResolver symbolResolver) {
        this.symbolResolver = symbolResolver;
        return this;
    }

    public List<ParseResult.PostProcessor> getPostProcessors() {
        return postProcessors;
    }
}
