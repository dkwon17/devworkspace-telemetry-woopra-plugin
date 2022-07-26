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

import com.redhat.devworkspace.services.telemetry.woopra.exception.WoopraCredentialException;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import org.eclipse.che.incubator.workspace.telemetry.finder.DevWorkspaceFinder;
import org.eclipse.che.incubator.workspace.telemetry.finder.UsernameFinder;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AnalyticsManagerStartupTest {

    @Test
    public void willNotStartWithoutWriteKey() {
        MainConfiguration config = createMainConfiguration(null, null);

        assertThrows(WoopraCredentialException.class, () -> {
            AnalyticsManager am = new AnalyticsManager(config,
                    new MockDevworkspaceFinder(),
                    new MockUsernameFinder(),
                    null,
                    null);
        });
    }

    @Test
    public void willNotStartWithoutWoopraDomain() {
        MainConfiguration config = createMainConfiguration("segmentWriteKey", null);

        assertThrows(WoopraCredentialException.class, () -> {
            AnalyticsManager am = new AnalyticsManager(config,
                    new MockDevworkspaceFinder(),
                    new MockUsernameFinder(),
                    null,
                    null);
        });
    }

    private MainConfiguration createMainConfiguration(String segmentWriteKey, String woopraDomain) {
        MainConfiguration config = new MainConfiguration();
        config.segmentWriteKey = segmentWriteKey == null ? Optional.empty() : Optional.of(segmentWriteKey);
        config.woopraDomain = woopraDomain == null ? Optional.empty() : Optional.of(woopraDomain);
        config.segmentWriteKeyEndpoint = Optional.empty();
        config.woopraDomainEndpoint = Optional.empty();
        return config;
    }

    private class MockDevworkspaceFinder implements DevWorkspaceFinder {
        @Override
        public GenericKubernetesResource findDevWorkspace(String devworkspaceId) {
            return null;
        }
    }

    private class MockUsernameFinder implements UsernameFinder {
        @Override
        public String findUsername() {
            return "test-username";
        }
    }
}