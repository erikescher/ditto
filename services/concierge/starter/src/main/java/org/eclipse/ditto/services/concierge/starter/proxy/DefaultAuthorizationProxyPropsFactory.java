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
package org.eclipse.ditto.services.concierge.starter.proxy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsReporter;
import org.eclipse.ditto.services.concierge.starter.actors.DispatcherActor;
import org.eclipse.ditto.services.concierge.util.cache.AclEnforcerCacheLoader;
import org.eclipse.ditto.services.concierge.util.cache.CacheFactory;
import org.eclipse.ditto.services.concierge.util.cache.IdentityCache;
import org.eclipse.ditto.services.concierge.util.cache.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.concierge.util.cache.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.concierge.util.cache.entry.Entry;
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfigReader;
import org.eclipse.ditto.services.concierge.util.enforcement.EnforcementProvider;
import org.eclipse.ditto.services.concierge.util.enforcement.EnforcerActor;
import org.eclipse.ditto.services.concierge.util.enforcement.LiveSignalEnforcement;
import org.eclipse.ditto.services.concierge.util.enforcement.MessageCommandEnforcement;
import org.eclipse.ditto.services.concierge.util.enforcement.PolicyCommandEnforcement;
import org.eclipse.ditto.services.concierge.util.enforcement.ThingCommandEnforcement;
import org.eclipse.ditto.services.concierge.util.update.PolicyCacheUpdateActor;
import org.eclipse.ditto.services.concierge.util.update.ThingCacheUpdateActor;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.codahale.metrics.MetricRegistry;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ditto default implementation of {@link AuthorizationProxyPropsFactory}.
 */
public final class DefaultAuthorizationProxyPropsFactory extends AuthorizationProxyPropsFactory {

    private static final String ENFORCER_CACHE_METRIC_NAME_PREFIX = "ditto.authorization.enforcer.cache.";
    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto.authorization.id.cache.";

    @Override
    public ActorRef startActors(final ActorContext context, final ConciergeConfigReader configReader,
            final ActorRef pubSubMediator) {
        final Consumer<Map.Entry<String, MetricRegistry>> metricsReportingConsumer =
                namedMetricRegistry -> StatsdMetricsReporter.getInstance().add(namedMetricRegistry);
        final Duration askTimeout = configReader.caches().askTimeout();


        final ActorRef policiesShardRegionProxy = startProxy(context.system(), configReader.cluster().numberOfShards(),
                PoliciesMessagingConstants.SHARD_REGION, PoliciesMessagingConstants.CLUSTER_ROLE);

        final ActorRef thingsShardRegionProxy = startProxy(context.system(), configReader.cluster().numberOfShards(),
                ThingsMessagingConstants.SHARD_REGION, ThingsMessagingConstants.CLUSTER_ROLE);

        final ThingEnforcementIdCacheLoader thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<EntityId>> thingIdCache =
                CacheFactory.createCache(thingEnforcerIdCacheLoader, configReader.caches().id(),
                        ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE,
                        metricsReportingConsumer);

        // policies always refer to themselves in the cache.
        final Cache<EntityId, Entry<EntityId>> policyIdCache = new IdentityCache();

        final PolicyEnforcerCacheLoader policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache =
                CacheFactory.createCache(policyEnforcerCacheLoader, configReader.caches().enforcer(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "policy",
                        metricsReportingConsumer);

        final AclEnforcerCacheLoader aclEnforcerCacheLoader =
                new AclEnforcerCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache =
                CacheFactory.createCache(aclEnforcerCacheLoader, configReader.caches().enforcer(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "acl",
                        metricsReportingConsumer);

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegionProxy,
                policiesShardRegionProxy, thingIdCache, policyIdCache, policyEnforcerCache, aclEnforcerCache));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegionProxy,
                policyIdCache, policyEnforcerCache));
        enforcementProviders.add(new MessageCommandEnforcement.Provider(thingIdCache, policyEnforcerCache,
                aclEnforcerCache));
        enforcementProviders.add(new LiveSignalEnforcement.Provider(thingIdCache, policyEnforcerCache,
                aclEnforcerCache));

        final Props enforcerProps = EnforcerActor.props(pubSubMediator, enforcementProviders);
        final ActorRef enforcerShardRegion = startShardRegion(context.system(), configReader.cluster(), enforcerProps);

        // start cache updaters
        final int instanceIndex = configReader.instanceIndex();
        final Props thingCacheUpdateActorProps =
                ThingCacheUpdateActor.props(aclEnforcerCache, thingIdCache, pubSubMediator, instanceIndex);
        context.actorOf(thingCacheUpdateActorProps, ThingCacheUpdateActor.ACTOR_NAME);
        final Props policyCacheUpdateActorProps =
                PolicyCacheUpdateActor.props(policyEnforcerCache, pubSubMediator, instanceIndex);
        context.actorOf(policyCacheUpdateActorProps, PolicyCacheUpdateActor.ACTOR_NAME);

        context.actorOf(DispatcherActor.props(pubSubMediator, enforcerShardRegion), DispatcherActor.ACTOR_NAME);

        return enforcerShardRegion;
    }
}
