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
package org.eclipse.ditto.model.messages;

import java.nio.ByteBuffer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.things.ThingId;

/**
 * A mutable builder with a fluent API for an immutable {@link Message}.
 *
 * @param <T> the type of the Message's payload
 */
@NotThreadSafe
public interface MessageBuilder<T> {

    /**
     * Returns a new builder for {@link MessageHeaders}.
     *
     * @param direction the direction of the message.
     * @param thingId the thing ID of the message.
     * @param subject the subject of the message.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} or {@code subject} is empty.
     * @deprecated Thing ID is now typed. Use
     * {@link #newHeadersBuilder(MessageDirection, org.eclipse.ditto.model.things.ThingId, CharSequence)}
     * instead.
     */
    @Deprecated
    static MessageHeadersBuilder newHeadersBuilder(final MessageDirection direction, final CharSequence thingId,
            final CharSequence subject) {

        return newHeadersBuilder(direction, ThingId.of(thingId), subject);
    }

    /**
     * Returns a new builder for {@link MessageHeaders}.
     *
     * @param direction the direction of the message.
     * @param thingId the thing ID of the message.
     * @param subject the subject of the message.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} or {@code subject} is empty.
     */
    static MessageHeadersBuilder newHeadersBuilder(final MessageDirection direction, final ThingId thingId,
            final CharSequence subject) {

        return MessagesModelFactory.newHeadersBuilder(direction, thingId, subject);
    }

    /**
     * Sets the payload of the Message.
     *
     * @param payload the payload of the Message.
     * @return this builder to allow method chaining.
     * @throws UnsupportedOperationException if the parameter was already predefined for the Message to build.
     */
    MessageBuilder<T> payload(@Nullable T payload);

    /**
     * Sets the raw payload of the Message.
     *
     * @param rawPayload the raw payload of the Message.
     * @return this builder to allow method chaining.
     * @throws UnsupportedOperationException if the parameter was already predefined for the Message to build.
     */
    MessageBuilder<T> rawPayload(@Nullable ByteBuffer rawPayload);

    /**
     * Adds a {@code responseConsumer} which is invoked with a potential response Message to the built Message.
     *
     * @param responseConsumer Consumer to invoke for a potential response Message.
     * @return this builder to allow method chaining.
     */
    MessageBuilder<T> responseConsumer(@Nullable MessageResponseConsumer<?> responseConsumer);

    /**
     * Creates a new immutable {@link Message} containing all properties which were set to this builder beforehand.
     *
     * @return the new Message.
     */
    Message<T> build();

}
