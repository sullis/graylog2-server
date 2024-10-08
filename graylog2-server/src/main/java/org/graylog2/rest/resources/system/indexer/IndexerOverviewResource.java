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
package org.graylog2.rest.resources.system.indexer;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.indexer.IndexSet;
import org.graylog2.indexer.IndexSetRegistry;
import org.graylog2.indexer.MongoIndexSet;
import org.graylog2.indexer.cluster.Cluster;
import org.graylog2.indexer.counts.Counts;
import org.graylog2.indexer.indices.Indices;
import org.graylog2.indexer.indices.IndicesAdapter;
import org.graylog2.indexer.indices.TooManyAliasesException;
import org.graylog2.indexer.indices.util.NumberBasedIndexNameComparator;
import org.graylog2.rest.models.count.responses.MessageCountResponse;
import org.graylog2.rest.models.system.deflector.responses.DeflectorSummary;
import org.graylog2.rest.models.system.indexer.responses.IndexRangeSummary;
import org.graylog2.rest.models.system.indexer.responses.IndexSizeSummary;
import org.graylog2.rest.models.system.indexer.responses.IndexSummary;
import org.graylog2.rest.models.system.indexer.responses.IndexerClusterOverview;
import org.graylog2.rest.models.system.indexer.responses.IndexerOverview;
import org.graylog2.rest.resources.system.DeflectorResource;
import org.graylog2.rest.resources.system.IndexRangesResource;
import org.graylog2.shared.rest.resources.RestResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.graylog2.rest.models.system.indexer.responses.IndexSummary.TierType.HOT;
import static org.graylog2.rest.models.system.indexer.responses.IndexSummary.TierType.WARM;
import static org.graylog2.shared.rest.documentation.generator.Generator.CLOUD_VISIBLE;

@RequiresAuthentication
@Api(value = "Indexer/Overview", description = "Indexing overview", tags = {CLOUD_VISIBLE})
@Path("/system/indexer/overview")
public class IndexerOverviewResource extends RestResource {
    private final DeflectorResource deflectorResource;
    private final IndexerClusterResource indexerClusterResource;
    private final IndexRangesResource indexRangesResource;
    private final Counts counts;
    private final IndexSetRegistry indexSetRegistry;
    private final Indices indices;
    private final Cluster cluster;
    private final IndicesAdapter indicesAdapter;

    @Inject
    public IndexerOverviewResource(DeflectorResource deflectorResource,
                                   IndexerClusterResource indexerClusterResource,
                                   IndexRangesResource indexRangesResource,
                                   Counts counts,
                                   IndexSetRegistry indexSetRegistry,
                                   Indices indices,
                                   Cluster cluster,
                                   IndicesAdapter indicesAdapter) {
        this.deflectorResource = deflectorResource;
        this.indexerClusterResource = indexerClusterResource;
        this.indexRangesResource = indexRangesResource;
        this.counts = counts;
        this.indexSetRegistry = indexSetRegistry;
        this.indices = indices;
        this.cluster = cluster;
        this.indicesAdapter = indicesAdapter;
    }

    @GET
    @Timed
    @ApiOperation(value = "Get overview of current indexing state, including deflector config, cluster state, index ranges & message counts.")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    public IndexerOverview index() throws TooManyAliasesException {
        if (!cluster.isConnected()) {
            throw new ServiceUnavailableException("Elasticsearch cluster is not available, check your configuration and logs for more information.");
        }

        try {
            return getIndexerOverview(indexSetRegistry.getDefault());
        } catch (IllegalStateException e) {
            throw new NotFoundException("Default index set not found");
        }
    }

    @GET
    @Timed
    @Path("/{indexSetId}")
    @ApiOperation(value = "Get overview of current indexing state for the given index set, including deflector config, cluster state, index ranges & message counts.")
    @Produces(MediaType.APPLICATION_JSON)
    public IndexerOverview index(@ApiParam(name = "indexSetId") @PathParam("indexSetId") String indexSetId) throws TooManyAliasesException {
        if (!cluster.isConnected()) {
            throw new ServiceUnavailableException("Elasticsearch cluster is not available, check your configuration and logs for more information.");
        }

        final IndexSet indexSet = getIndexSet(indexSetRegistry, indexSetId);

        return getIndexerOverview(indexSet);
    }

    private IndexerOverview getIndexerOverview(IndexSet indexSet) throws TooManyAliasesException {
        final String indexSetId = indexSet.getConfig().id();

        final DeflectorSummary deflectorSummary = deflectorResource.deflector(indexSetId);
        final List<IndexRangeSummary> indexRanges = indexRangesResource.list().ranges();
        final JsonNode indexStats = indices.getIndexStats(indexSet);
        final List<String> indexNames = new ArrayList<>();
        indexStats.fieldNames().forEachRemaining(indexNames::add);
        final Map<String, Boolean> areReopened = indices.areReopened(indexNames);
        final List<IndexSummary> indicesSummaries = buildIndexSummaries(deflectorSummary, indexSet, indexRanges, indexStats, areReopened);

        return IndexerOverview.create(deflectorSummary,
                IndexerClusterOverview.create(indexerClusterResource.clusterHealth(), indexerClusterResource.clusterName().name()),
                MessageCountResponse.create(counts.total(indexSet)),
                indicesSummaries);
    }

    private List<IndexSummary> buildIndexSummaries(DeflectorSummary deflectorSummary, IndexSet indexSet, List<IndexRangeSummary> indexRanges, JsonNode indexStats, Map<String, Boolean> areReopened) {
        final Iterator<Map.Entry<String, JsonNode>> fields = indexStats.fields();
        final List<IndexSummary> indexSummaries = new ArrayList<>();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> entry = fields.next();
            indexSummaries.add(buildIndexSummary(entry.getKey(), entry, indexRanges, deflectorSummary, areReopened));

        }
        indices.getClosedIndices(indexSet).forEach(indexName ->
                indexSummaries.add(buildClosedIndexSummary(indexName, indexRanges, deflectorSummary)));
        indexSummaries.sort(Comparator.comparing(IndexSummary::indexName, new NumberBasedIndexNameComparator(MongoIndexSet.SEPARATOR)));
        return indexSummaries;
    }

    private IndexSummary buildIndexSummary(String indexName,
                                           Map.Entry<String, JsonNode> indexStats,
                                           List<IndexRangeSummary> indexRanges,
                                           DeflectorSummary deflectorSummary,
                                           Map<String, Boolean> areReopened) {
        final String index = indexStats.getKey();
        final JsonNode primaries = indexStats.getValue().path("primaries");
        final JsonNode docs = primaries.path("docs");
        final long count = docs.path("count").asLong();
        final long deleted = docs.path("deleted").asLong();
        final JsonNode store = primaries.path("store");
        final long sizeInBytes = store.path("size_in_bytes").asLong();
        final long shardCount = indicesAdapter.getShardsInfo(indexName).size();

        final Optional<IndexRangeSummary> range = indexRanges.stream()
                .filter(indexRangeSummary -> indexRangeSummary.indexName().equals(index))
                .findFirst();
        final boolean isDeflector = index.equals(deflectorSummary.currentTarget());
        final boolean isReopened = areReopened.get(index);

        return IndexSummary.create(
                indexName,
                IndexSizeSummary.create(count, deleted, sizeInBytes),
                range.orElse(null),
                isDeflector,
                false,
                isReopened,
                getTierType(indexName),
                shardCount);
    }

    private IndexSummary.TierType getTierType(String indexName) {
        return indicesAdapter.getWarmIndexInfo(indexName).isPresent() ? WARM : HOT;
    }

    private IndexSummary buildClosedIndexSummary(String indexName, List<IndexRangeSummary> indexRanges, DeflectorSummary deflectorSummary) {
        return IndexSummary.create(
                indexName,
                null,
                indexRanges.stream().filter(indexRangeSummary -> indexRangeSummary.indexName().equals(indexName)).findFirst().orElse(null),
                indexName.equals(deflectorSummary.currentTarget()),
                true,
                false,
                getTierType(indexName),
                0L);
    }

}
