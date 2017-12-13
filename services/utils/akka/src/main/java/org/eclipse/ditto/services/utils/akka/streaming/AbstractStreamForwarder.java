/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_FINISHED_MSG;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor that receives a stream of elements, forwards them to another actor, and expects as many acknowledgements as
 * there are streamed elements. Terminates self if no message was received for a period of time. <p> Each stream element
 * is to be acknowledged by a {@code akka.actor.Status.Success}. At the end of the stream, the special success message
 * {@link StreamConstants#STREAM_FINISHED_MSG} MUST be sent. </p>
 *
 * @param <E> Type of received stream elements.
 */
public abstract class AbstractStreamForwarder<E> extends AbstractActor {

    /**
     * Logger associated with this actor.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private Instant lastMessageReceived = Instant.now();
    private Set<String> toBeAckedElementIds = new HashSet<>();
    private long forwardedElementCount = 0;
    private long ackedElementCount = 0;
    private boolean streamComplete = false;

    private Cancellable activityCheck;

    protected AbstractStreamForwarder() {
        log.info("Creating new StreamForwarder");
    }

    /**
     * Returns the actor to send transformed stream elements to.
     *
     * @return Reference of the recipient actor.
     */
    protected abstract ActorRef getRecipient();

    /**
     * Returns the maximum time this actor waits for a message before it terminates itself.
     *
     * @return The maximum idle time.
     */
    protected abstract Duration getMaxIdleTime();

    /**
     * Returns the class of stream elements.
     *
     * @return The class.
     */
    protected abstract Class<E> getElementClass();

    /**
     * Returns a function which maps a stream element to an identifier (to correlate acks).
     *
     * @return The function.
     */
    protected abstract Function<E, String> getElementIdentifierFunction();

    /**
     * Invoked when all stream elements are forwarded and acknowledged. Do not start asynchronous operations: the actor
     * terminates immediately after this method returns.
     */
    private void onSuccess() {
        logInfoWithDetails("Stream successfully finished");
        getCompletionRecipient().tell(STREAM_FINISHED_MSG, getSelf());
    }

    private void logDebugWithDetails(final String mainMessage) {
        if (log.isDebugEnabled()) {
            log.debug(createDetailedMessage(mainMessage));
        }
    }

    private void logInfoWithDetails(final String mainMessage) {
        if (log.isInfoEnabled()) {
            log.info(createDetailedMessage(mainMessage));
        }
    }

    private void logErrorWithDetails(final String mainMessage) {
        log.error(createDetailedMessage(mainMessage));
    }

    private String createDetailedMessage(final String mainMessage) {
        return MessageFormat.format(
                "{0}. Stream status: {1} elements forwarded, {2} acks received, lastMessage received at {3}. " +
                        "To be acked: <{4}>.",
                mainMessage, forwardedElementCount, ackedElementCount, lastMessageReceived, toBeAckedElementIds);
    }

    /**
     * Returns the actor to send a message when the stream has been successfully completed.
     *
     * @return Reference of the recipient actor.
     */
    protected abstract ActorRef getCompletionRecipient();

    @Override
    public void preStart() throws Exception {
        super.preStart();

        final FiniteDuration delayAndInterval = FiniteDuration.create(getMaxIdleTime().getSeconds(), TimeUnit.SECONDS);
        activityCheck = getContext().system().scheduler()
                .schedule(delayAndInterval, delayAndInterval, getSelf(), CheckForActivity.INSTANCE,
                        getContext().dispatcher(), ActorRef.noSender());
    }

    @Override
    public void postStop() throws Exception {
        if (null != activityCheck) {
            activityCheck.cancel();
        }
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(STREAM_FINISHED_MSG, this::handleStreamComplete)
                .match(getElementClass(), this::transformAndForwardElement)
                .match(StreamAck.class, this::onAck)
                .match(CheckForActivity.class, unused -> checkForActivity())
                .matchAny(this::unhandled)
                .build();
    }

    private void transformAndForwardElement(final E element) {
        if (streamComplete) {
            log.warning("Received stream element <{}> after stream termination; will forward it anyway.", element);
        }
        final String identifier = getElementIdentifierFunction().apply(element);
        toBeAckedElementIds.add(identifier);
        forwardedElementCount++;
        log.debug("Got element with identifier: {}", identifier);

        getRecipient().tell(element, getSelf());
        updateLastMessageReceived();
    }

    private void onAck(final StreamAck streamAck) {
        final String elementId = streamAck.getElementId();
        if (StreamAck.Status.SUCCESS.equals(streamAck.getStatus())) {
            log.debug("Got successful ack for element: <{}>", elementId);
        } else {
            log.warning("Got unsuccessful ack with status <{}> for element: <{}>", streamAck.getStatus(), elementId);
        }
        final boolean wasExpected = toBeAckedElementIds.remove(elementId);
        if (!wasExpected) {
            log.warning("Got unexpected ack for element: <{}>", elementId);
            return;
        } else {
            ackedElementCount++;
        }

        updateLastMessageReceived();
        checkAllElementsAreAcknowledged();
    }

    private void handleStreamComplete(final Object msg) {
        log.debug("Received completion msg: {}", msg);
        streamComplete = true;
        checkAllElementsAreAcknowledged();
    }

    private void checkAllElementsAreAcknowledged() {
        if (streamComplete && toBeAckedElementIds.isEmpty()) {
            logInfoWithDetails("Stream complete");
            onSuccess();
            shutdown();
        }
    }

    private void checkForActivity() {
        final Duration sinceLastMessage = Duration.between(lastMessageReceived, Instant.now());
        if (sinceLastMessage.compareTo(getMaxIdleTime()) > 0) {
            logErrorWithDetails("Stream timed out");
            getCompletionRecipient().tell(FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG, getSelf());
            shutdown();
        } else {
            logDebugWithDetails("Stream is still considered as active");
        }
    }

    private void updateLastMessageReceived() {
        lastMessageReceived = Instant.now();
        logDebugWithDetails("Updated last message");
    }

    private void shutdown() {
        getContext().stop(getSelf());
    }

    private static final class CheckForActivity {

        private static final CheckForActivity INSTANCE = new CheckForActivity();

        private CheckForActivity() {
            // no-op
        }
    }
}
