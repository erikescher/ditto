/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.base.headers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.model.base.headers.DittoHeaderDefinition}.
 */
public final class DittoHeaderDefinitionTest {

    @Test
    public void allInternalHeadersArePrefixed() {
        for (DittoHeaderDefinition definition : DittoHeaderDefinition.values()) {
            if (!definition.shouldReadFromExternalHeaders() && !definition.shouldWriteToExternalHeaders()) {
                assertThat(definition.getKey()).startsWith("ditto-");
            } else {
                assertThat(definition.getKey()).doesNotStartWith("ditto-");
            }
        }
    }
}
