/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.placeholders;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:lower()} function implementation.
 */
@Immutable
final class PipelineFunctionLower implements PipelineFunction {

    private static final String FUNCTION_NAME = "lower";

    private final PipelineFunctionParameterResolverFactory.EmptyParameterResolver parameterResolver =
            PipelineFunctionParameterResolverFactory.forEmptyParameters();

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public LowerFunctionSignature getSignature() {
        return LowerFunctionSignature.INSTANCE;
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        // check if signature matches (empty params!)
        validateOrThrow(paramsIncludingParentheses);
        return value.map(String::toLowerCase);
    }

    private void validateOrThrow(final String paramsIncludingParentheses) {
        if (!parameterResolver.test(paramsIncludingParentheses)) {
            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                    .build();
        }
    }

    /**
     * Describes the signature of the {@code lower()} function.
     */
    private static final class LowerFunctionSignature implements Signature {

        private static final LowerFunctionSignature INSTANCE = new LowerFunctionSignature();

        private LowerFunctionSignature() {
        }

        @Override
        public List<ParameterDefinition> getParameterDefinitions() {
            return Collections.emptyList();
        }

        @Override
        public <T> ParameterDefinition<T> getParameterDefinition(final int index) {
            throw new IllegalArgumentException("Signature does not define a parameter at index '" + index + "'");
        }

        @Override
        public String toString() {
            return renderSignature();
        }
    }

}
