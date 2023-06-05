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

import java.util.Optional;

public class MainConfigurationBuilder {

    private Optional<String> segmentWriteKey;
    private Optional<String> segmentWriteKeyEndpoint;

    public MainConfigurationBuilder() {
        this.segmentWriteKey = Optional.empty();
        this.segmentWriteKeyEndpoint = Optional.empty();
    }

    public MainConfigurationBuilder segmentWriteKey(String segmentWriteKey) {
        this.segmentWriteKey = Optional.of(segmentWriteKey);
        return this;
    }

    public MainConfigurationBuilder segmentWriteKeyEndpoint(String segmentWriteKeyEndpoint) {
        this.segmentWriteKeyEndpoint = Optional.of(segmentWriteKeyEndpoint);
        return this;
    }

    public MainConfiguration build() {
        MainConfiguration config = new MainConfiguration();
        config.segmentWriteKey = this.segmentWriteKey;
        config.segmentWriteKeyEndpoint = this.segmentWriteKeyEndpoint;
        return config;
    }
}
