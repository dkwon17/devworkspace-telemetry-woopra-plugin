/*
 * Copyright (c) 2022 Red Hat, Inc.
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

import com.segment.analytics.Analytics;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.eclipse.che.incubator.workspace.telemetry.base.AbstractAnalyticsManager;
import org.eclipse.che.incubator.workspace.telemetry.base.AnalyticsEvent;
import org.eclipse.che.incubator.workspace.telemetry.finder.DevWorkspaceFinder;
import org.eclipse.che.incubator.workspace.telemetry.finder.UsernameFinder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

@QuarkusTest
class AbstractAnalyticsManagerEventTest {

    @InjectSpy
    AbstractAnalyticsManager analyticsManager;

    @Test
    public void testOnEventNotCalledAfterWorkspaceStart() {
        String ownerId = "/default-theia-plugins/telemetry_plugin";
        String ip = "192.0.0.0";
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)";
        String resolution = "2560x1440";

        Map<String, Object> properties = Collections.emptyMap();

        analyticsManager.doSendEvent(AnalyticsEvent.WORKSPACE_STARTED, ownerId, ip, userAgent, resolution, properties);
        analyticsManager.doSendEvent(AnalyticsEvent.EDITOR_USED, ownerId, ip, userAgent, resolution, properties);

        Mockito.verify(analyticsManager, Mockito.times(1))
                .onEvent(any(), any(), any(), any(), any(), any());
    }
}

@Mock
class MockUsernameFinder implements UsernameFinder {
    @Override
    public String findUsername() {
        return "test-username";
    }
}

@Mock
class MockDevworkspaceFinder implements DevWorkspaceFinder {
    @Override
    public GenericKubernetesResource findDevWorkspace(String devworkspaceId) {
        return null;
    }
}

@Mock
class MockAnalyticsProvider {
    public Analytics getAnalytics(String segmentWriteKey, ExecutorService networkExecutor) {
        return mock(Analytics.class);
    }
}

@Mock
class MockHttpUrlConnectionProvider {
    public HttpURLConnection getHttpUrlConnection(String uri)
            throws MalformedURLException, IOException, URISyntaxException {
        return mock(HttpURLConnection.class);
    }
}

