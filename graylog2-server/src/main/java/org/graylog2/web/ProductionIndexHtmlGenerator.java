/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog2.web;

import com.floreysoft.jmte.Engine;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import org.graylog2.configuration.HttpConfiguration;
import org.graylog2.rest.RestTools;
import org.graylog2.web.customization.CustomizationConfig;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Production implementation of the {@link IndexHtmlGenerator} interface that provides an "index.html" page
 * including the production web interface assets.
 *
 * This implementation throws an error when the web interface assets cannot be found in the classpath.
 */
public class ProductionIndexHtmlGenerator implements IndexHtmlGenerator {
    private final String template;
    private final List<String> cssFiles;
    private final List<String> sortedJsFiles;
    private final Engine templateEngine;
    private final HttpConfiguration httpConfiguration;
    private final CustomizationConfig customizationConfig;

    @Inject
    public ProductionIndexHtmlGenerator(final PluginAssets pluginAssets,
                                        final Engine templateEngine,
                                        final HttpConfiguration httpConfiguration,
                                        final CustomizationConfig customizationConfig) throws IOException {
        this(
                Resources.toString(Resources.getResource("web-interface/index.html.template"), StandardCharsets.UTF_8),
                pluginAssets.cssFiles(),
                pluginAssets.sortedJsFiles(),
                templateEngine,
                httpConfiguration,
                customizationConfig);
    }

    private ProductionIndexHtmlGenerator(final String template,
                                         final List<String> cssFiles,
                                         final List<String> sortedJsFiles,
                                         final Engine templateEngine,
                                         final HttpConfiguration httpConfiguration,
                                         final CustomizationConfig customizationConfig) {
        this.template = requireNonNull(template, "template");
        this.cssFiles = requireNonNull(cssFiles, "cssFiles");
        this.sortedJsFiles = requireNonNull(sortedJsFiles, "sortedJsFiles");
        this.templateEngine = requireNonNull(templateEngine, "templateEngine");
        this.httpConfiguration = requireNonNull(httpConfiguration, "httpConfiguration");
        this.customizationConfig = customizationConfig;
    }

    @Override
    public String get(MultivaluedMap<String, String> headers, String nonce) {
        final URI relativePath = RestTools.buildRelativeExternalUri(headers, httpConfiguration.getHttpExternalUri());
        final Map<String, Object> model = ImmutableMap.<String, Object>builder()
                .put("title", customizationConfig.productName() + " Web Interface")
                .put("cssFiles", cssFiles)
                .put("jsFiles", sortedJsFiles)
                .put("appPrefix", relativePath)
                .put("nonce", nonce)
                .build();
        return templateEngine.transform(template, model);
    }
}
