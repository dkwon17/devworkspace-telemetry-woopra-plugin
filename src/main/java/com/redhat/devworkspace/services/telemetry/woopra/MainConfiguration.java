/*
 * Copyright (c) 2020-2022 Red Hat, Inc.
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

import org.eclipse.che.api.core.rest.DefaultHttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.incubator.workspace.telemetry.base.BaseConfiguration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Produces;
import java.util.Optional;

@Dependent
@Alternative
public class MainConfiguration extends BaseConfiguration {
    @ConfigProperty(name = "segment.write.key")
    Optional<String> segmentWriteKey;

    @ConfigProperty(name = "woopra.domain")
    Optional<String> woopraDomain;

    @ConfigProperty(name = "segment.write.key.endpoint")
    Optional<String> segmentWriteKeyEndpoint;

    @ConfigProperty(name = "woopra.domain.endpoint")
    Optional<String> woopraDomainEndpoint;
}
