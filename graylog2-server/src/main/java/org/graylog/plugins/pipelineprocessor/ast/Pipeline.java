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
package org.graylog.plugins.pipelineprocessor.ast;

import com.codahale.metrics.Meter;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Sets;
import org.graylog.plugins.pipelineprocessor.processors.PipelineMetricRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.SortedSet;
import java.util.stream.Stream;

@AutoValue
public abstract class Pipeline {

    private transient Meter executed;

    @Nullable
    public abstract String id();

    public abstract String name();

    public abstract SortedSet<Stage> stages();

    public static Builder builder() {
        return new AutoValue_Pipeline.Builder();
    }

    public static Pipeline empty(String name) {
        return builder().name(name).stages(Sets.<Stage>newTreeSet()).build();
    }

    public abstract Builder toBuilder();

    public Pipeline withId(String id) {
        return toBuilder().id(id).build();
    }

    @Override
    @Memoized
    public abstract int hashCode();

    /**
     * Register the metrics attached to this pipeline.
     *
     * @param metricRegistry the registry to add the metrics to
     */
    public void registerMetrics(PipelineMetricRegistry metricRegistry) {
        if (id() != null) {
            executed = metricRegistry.registerPipelineMeter(id(), "executed");
        }
    }

    public void markExecution() {
        if (executed != null) {
            executed.mark();
        }
    }

    public boolean containsRule(@Nonnull String ruleName) {
        return stages()
                .stream()
                .flatMap(stage -> stage.ruleReferences() == null ? Stream.empty() : stage.ruleReferences().stream())
                .anyMatch(ruleName::equals);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Pipeline build();

        public abstract Builder id(String id);

        public abstract Builder name(String name);

        public abstract Builder stages(SortedSet<Stage> stages);
    }

    @Override
    public String toString() {
        return "Pipeline '" + name() + "' (" + id() + ")";
    }
}
