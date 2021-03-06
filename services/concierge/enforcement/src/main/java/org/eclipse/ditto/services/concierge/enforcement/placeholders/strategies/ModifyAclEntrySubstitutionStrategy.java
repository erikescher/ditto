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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;

/**
 * Handles substitution for ACL {@link AuthorizationSubject}s inside a {@link ModifyAclEntry} command.
 */
final class ModifyAclEntrySubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifyAclEntry> {

    ModifyAclEntrySubstitutionStrategy() {
        super(ModifyAclEntry.class);
    }

    @Override
    public WithDittoHeaders apply(final ModifyAclEntry modifyAclEntry,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifyAclEntry);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = modifyAclEntry.getDittoHeaders();
        final AclEntry existingAclEntry = modifyAclEntry.getAclEntry();
        final AclEntry substitutedAclEntry = substituteAclEntry(existingAclEntry, substitutionAlgorithm, dittoHeaders);

        if (existingAclEntry.equals(substitutedAclEntry)) {
            return modifyAclEntry;
        } else {
            return ModifyAclEntry.of(modifyAclEntry.getThingEntityId(), substitutedAclEntry, dittoHeaders);
        }
    }

}
