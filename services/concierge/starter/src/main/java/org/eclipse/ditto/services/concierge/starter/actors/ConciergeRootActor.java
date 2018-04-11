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
package org.eclipse.ditto.services.concierge.starter.actors;

import static java.util.Objects.requireNonNull;

import java.net.ConnectException;
import java.util.NoSuchElementException;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.concierge.starter.proxy.AuthorizationProxyPropsFactory;
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfigReader;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorInitializationException;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;

/**
 * The root actor of the concierge service.
 */
public final class ConciergeRootActor extends AbstractActor {

    private static final String RESTARTING_CHILD_MSG = "Restarting child...";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef conciergeShardRegion;

    private final SupervisorStrategy supervisorStrategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MSG);
                return SupervisorStrategy.restart();
            }).match(IllegalArgumentException.class, e -> {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e -> {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e -> {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e -> {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e -> {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MSG);
                return SupervisorStrategy.restart();
            }).match(InvalidActorNameException.class, e -> {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorInitializationException.class, e -> {
                log.error(e, "ActorInitializationException in child actor: {}", e.getMessage());
                return SupervisorStrategy.stop();
            }).match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                log.info(RESTARTING_CHILD_MSG);
                return SupervisorStrategy.restart();
            }).match(DittoRuntimeException.class, e -> {
                log.error(e,
                        "DittoRuntimeException '{}' should not be escalated to RootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(Throwable.class, e -> {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "conciergeRoot";

    private ConciergeRootActor(final ConciergeConfigReader configReader, final ActorRef pubSubMediator,
            final AuthorizationProxyPropsFactory authorizationProxyPropsFactory) {
        requireNonNull(configReader);
        requireNonNull(pubSubMediator);
        requireNonNull(authorizationProxyPropsFactory);

        conciergeShardRegion = authorizationProxyPropsFactory.startActors(getContext(), configReader, pubSubMediator);
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param configReader the config reader.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param authorizationProxyPropsFactory the {@link AuthorizationProxyPropsFactory}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ConciergeConfigReader configReader, final ActorRef pubSubMediator,
            final AuthorizationProxyPropsFactory authorizationProxyPropsFactory) {
        return Props.create(ConciergeRootActor.class,
                () -> new ConciergeRootActor(configReader, pubSubMediator, authorizationProxyPropsFactory));
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState ->
                        conciergeShardRegion.forward(getShardRegionState, getContext()))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure <{}>!", f))
                .matchAny(m -> {
                    log.warning("Unknown message <{}>.", m);
                    unhandled(m);
                }).build();
    }
}
