/*
 * Copyright (c) 2016-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.devworkspace.services.telemetry.woopra;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.redhat.devworkspace.services.telemetry.woopra.exception.SegmentDomainNotFoundException;
import com.segment.analytics.Analytics;
import com.segment.analytics.messages.TrackMessage;
import org.eclipse.che.incubator.workspace.telemetry.base.AbstractAnalyticsManager;
import org.eclipse.che.incubator.workspace.telemetry.base.AnalyticsEvent;
import org.eclipse.che.incubator.workspace.telemetry.finder.DevWorkspaceFinder;
import org.eclipse.che.incubator.workspace.telemetry.finder.UsernameFinder;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Alternative;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.eclipse.che.incubator.workspace.telemetry.base.AnalyticsEvent.WORKSPACE_STOPPED;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Send event to Segment.com and regularly ping Woopra. For now the events are
 * provided by the {@link UrlToEventFilter}.
 *
 * @author David Festal
 */
@SuppressWarnings("JavadocReference")
@Alternative
@ApplicationScoped
public class AnalyticsManager extends AbstractAnalyticsManager {

    private static final Logger LOG = getLogger(AnalyticsManager.class);
    private static final long startEventDebounceTime = 2000L;

    private final Analytics analytics;

    private long startEventTime;

    String segmentWriteKey;

    @VisibleForTesting
    ScheduledExecutorService networkExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("Analytics Network Request Submitter").build());

    @VisibleForTesting
    LoadingCache<String, EventDispatcher> dispatchers;

    @VisibleForTesting
    HttpUrlConnectionProvider httpUrlConnectionProvider = null;

    String sessionId;

    public AnalyticsManager(MainConfiguration config,
                            DevWorkspaceFinder devworkspaceFinder,
                            UsernameFinder usernameFinder,
                            AnalyticsProvider analyticsProvider,
                            HttpUrlConnectionProvider httpUrlConnectionProvider) {
        super(config, devworkspaceFinder, usernameFinder);

        setSegmentWriteKey(config, httpUrlConnectionProvider);
        
        this.sessionId = String.valueOf(System.currentTimeMillis());

        if (isEnabled()) {
            this.httpUrlConnectionProvider = httpUrlConnectionProvider;
            analytics = analyticsProvider.getAnalytics(segmentWriteKey, networkExecutor);
        } else {
            analytics = null;
        }

        dispatchers = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).maximumSize(10)
                .removalListener((RemovalNotification<String, EventDispatcher> n) -> {
                    EventDispatcher dispatcher = n.getValue();
                    if (dispatcher != null) {
                        dispatcher.close();
                    }
                }).build(CacheLoader.<String, EventDispatcher>from(userId -> newEventDispatcher(userId, this.sessionId)));
    }

    private void setSegmentWriteKey(MainConfiguration config, HttpUrlConnectionProvider httpUrlConnectionProvider) {
        if (config.segmentWriteKey.isEmpty() && config.segmentWriteKeyEndpoint.isEmpty()) {
            throw new SegmentDomainNotFoundException("Requires a Segment write key or the URL of an endpoint that will return the Segment write key. " +
                    "Set either SEGMENT_WRITE_KEY or SEGMENT_WRITE_KEY_ENDPOINT");
        } else if (config.segmentWriteKey.isEmpty()) {
            segmentWriteKey = tryRequestFromUrl(config.segmentWriteKeyEndpoint.get(), httpUrlConnectionProvider);
        } else {
            segmentWriteKey = config.segmentWriteKey.get();
        }
    }

    private String tryRequestFromUrl(String endpoint, HttpUrlConnectionProvider httpUrlConnectionProvider) {
        BufferedReader br = null;
        try {
            HttpURLConnection connection = httpUrlConnectionProvider.getHttpUrlConnection(endpoint);
            br = new BufferedReader(new InputStreamReader((connection.getInputStream())));
            return br.lines().collect(Collectors.joining()).strip();
        } catch (Exception e) {
            throw new RuntimeException("Can't access endpoint: " + endpoint, e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private EventDispatcher newEventDispatcher(String userId, String sessionId) {
        return new EventDispatcher(userId, String.valueOf(sessionId));
    }

    @Override
    public boolean isEnabled() {
        return !segmentWriteKey.isEmpty();
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    @Override
    public void onActivity() {
        try {
            dispatchers.get(userId).onActivity();
        } catch (ExecutionException e) {
            LOG.warn("", e);
        }
    }

    @Override
    public void doSendEvent(AnalyticsEvent event, String ownerId, String ip, String userAgent, String resolution,
                            Map<String, Object> properties) {
        if (!isWithinWorkspaceStartDebounceTime(event)) {
            super.doSendEvent(event, ownerId, ip, userAgent, resolution, properties);
        }
    }

    private boolean isWithinWorkspaceStartDebounceTime(AnalyticsEvent event) {
        long currentTime = System.currentTimeMillis();
        if (event == AnalyticsEvent.WORKSPACE_STARTED) {
            this.startEventTime = currentTime;
            return false;
        }
        return currentTime - this.startEventTime < startEventDebounceTime;
    }

    @Override
    public void onEvent(AnalyticsEvent event, String ownerId, String ip, String userAgent, String resolution,
                        Map<String, Object> properties) {
        try {
            dispatchers.get(userId).sendTrackEvent(event, properties, ip, userAgent, resolution);
        } catch (ExecutionException e) {
            LOG.warn("", e);
        }
    }

    @Override
    public void increaseDuration(AnalyticsEvent event, Map<String, Object> properties) {
    }

    @VisibleForTesting
    class EventDispatcher {

        @VisibleForTesting
        String userId;

        private AnalyticsEvent lastEvent;
        @VisibleForTesting
        Map<String, Object> lastEventProperties;
        private long lastActivityTime;
        private long lastEventTime;
        private String lastIp;
        private String lastUserAgent;
        private String lastResolution;
        private String sessionId;

        EventDispatcher(String userId, String sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
        }

        void onActivity() {
            lastActivityTime = System.currentTimeMillis();
        }

        private boolean areEventsEqual(AnalyticsEvent event, Map<String, Object> properties) {
            if (lastEvent == null || lastEvent != event) {
                return false;
            }

            if (lastEventProperties == null) {
                return false;
            }

            for (String propToCheck : event.getPropertiesToCheck()) {
                Object lastValue = lastEventProperties.get(propToCheck);
                Object newValue = properties.get(propToCheck);
                if (lastValue != null && newValue != null && lastValue.equals(newValue)) {
                    continue;
                }
                if (lastValue == null && newValue == null) {
                    continue;
                }
                return false;
            }

            return true;
        }

        String sendTrackEvent(AnalyticsEvent event, final Map<String, Object> properties, String ip, String userAgent,
                              String resolution) {
            return sendTrackEvent(event, properties, ip, userAgent, resolution, false);
        }

        String sendTrackEvent(AnalyticsEvent event, final Map<String, Object> properties, String ip, String userAgent,
                              String resolution, boolean force) {
            String eventId;
            lastIp = ip;
            lastUserAgent = userAgent;
            lastResolution = resolution;
            final String theIp = ip != null ? ip : "0.0.0.0";
            synchronized (this) {
                lastEventTime = System.currentTimeMillis();
                if (!force && areEventsEqual(event, properties)) {
                    LOG.debug("Skipping event " + event.toString() + " since it is the same as the last one");
                    return null;
                }

                eventId = UUID.randomUUID().toString();
                TrackMessage.Builder messageBuilder = TrackMessage.builder(event.toString()).userId(userId).messageId(eventId);

                this.setupAmplitudeIntegration(messageBuilder);

                ImmutableMap.Builder<String, Object> propertiesBuilder = ImmutableMap.<String, Object>builder()
                        .putAll(properties);
                messageBuilder.properties(propertiesBuilder.build());

                ImmutableMap.Builder<String, Object> contextBuilder = ImmutableMap.<String, Object>builder().put("ip", ip);
                if (userAgent != null) {
                    contextBuilder.put("userAgent", userAgent);
                }
                if (event.getExpectedDurationSeconds() == 0) {
                    contextBuilder.put("duration", 0);
                }
                messageBuilder.context(contextBuilder.build());

                LOG.debug("sending " + event.toString() + " (ip=" + theIp + " - userAgent=" + userAgent + ") with properties: "
                        + properties);

                analytics.enqueue(messageBuilder);

                lastEvent = event;
                lastEventProperties = properties;

            }
            return eventId;
        }

        long getLastActivityTime() {
            return lastActivityTime;
        }

        String getLastIp() {
            return lastIp;
        }

        String getLastUserAgent() {
            return lastUserAgent;
        }

        String getLastResolution() {
            return lastResolution;
        }

        String getUserId() {
            return userId;
        }

        AnalyticsEvent getLastEvent() {
            return lastEvent;
        }

        long getLastEventTime() {
            return lastEventTime;
        }

        void setupAmplitudeIntegration(TrackMessage.Builder messageBuilder) {
            ImmutableMap.Builder<String, Object> integrationBuilder = ImmutableMap.<String, Object>builder()
                .put("session_id", this.sessionId);
            messageBuilder.integrationOptions("Actions Amplitude", integrationBuilder.build());
        }

        void close() {
        }
    }

    @Override
    public void destroy() {
        if (getUserId() != null) {
            EventDispatcher dispatcher;
            try {
                dispatcher = dispatchers.get(getUserId());
                dispatcher.sendTrackEvent(WORKSPACE_STOPPED, commonProperties, dispatcher.getLastIp(),
                        dispatcher.getLastUserAgent(), dispatcher.getLastResolution());
            } catch (ExecutionException e) {
            }
        }
        shutdown();
    }

    void shutdown() {
        if (analytics != null) {
            analytics.flush();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                LOG.warn("Thread.sleep() interrupted", e);
            }
            analytics.shutdown();
        }
    }
}

/**
 * Returns an {@link Analytics} object from a Segment write Key.
 *
 * @author David Festal
 */
@SuppressWarnings("JavadocReference")
@Dependent
class AnalyticsProvider {
    public Analytics getAnalytics(String segmentWriteKey, ExecutorService networkExecutor) {
        return Analytics.builder(segmentWriteKey).networkExecutor(networkExecutor).flushQueueSize(1).build();
    }
}

/**
 * Returns a {@link HttpURLConnection} object from a {@link URI}.
 *
 * @author David Festal
 */
@Dependent
class HttpUrlConnectionProvider {
    public HttpURLConnection getHttpUrlConnection(String uri)
            throws IOException, URISyntaxException {
        return (HttpURLConnection) new URI(uri).toURL().openConnection();
    }
}
