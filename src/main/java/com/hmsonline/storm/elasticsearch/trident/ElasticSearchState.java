//
// Copyright (c) 2013 Health Market Science, Inc.
//
package com.hmsonline.storm.elasticsearch.trident;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import storm.trident.state.State;
import storm.trident.tuple.TridentTuple;

import com.hmsonline.storm.elasticsearch.StormElasticSearchConstants;
import com.hmsonline.storm.elasticsearch.StormElasticSearchUtils;
import com.hmsonline.storm.elasticsearch.mapper.TridentElasticSearchMapper;

public class ElasticSearchState implements State {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchState.class);

    private Client client;

    @SuppressWarnings("rawtypes")
    public ElasticSearchState(Map config) {
        LOGGER.debug("Initialize ElasticSearchState");
        String clusterName = (String) config.get(StormElasticSearchConstants.ES_CLUSTER_NAME);
        String host = (String) config.get(StormElasticSearchConstants.ES_HOST);
        Integer port = 9300;
        try {
            port = Integer.parseInt(config.get(StormElasticSearchConstants.ES_PORT).toString());
        } catch (Exception e) {
            LOGGER.warn("Cannot get elastic search port from config file. Use default value: 9300");
        }

        Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", clusterName).build();
        client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress(host, port));
        LOGGER.debug("Initialization completed with [clusterName=" + clusterName + ", host=" + host + ", port=" + port
                + "]");
    }

    public ElasticSearchState(Client client) {
        this.client = client;
    }

    @Override
    public void beginCommit(Long txid) {
        LOGGER.debug("Begin to commit ES: " + txid);
    }

    @Override
    public void commit(Long txid) {
        LOGGER.debug("Commit ES: " + txid);
    }

    public void createIndices(TridentElasticSearchMapper mapper, List<TridentTuple> tuples) {
        BulkRequestBuilder bulkRequest = client.prepareBulk();

        for (TridentTuple tuple : tuples) {
            String indexName = mapper.mapToIndex(tuple);
            String type = mapper.mapToType(tuple);
            String key = mapper.mapToKey(tuple);
            Map<String, Object> data = mapper.mapToData(tuple);
            String parentId = mapper.mapToParentId(tuple);

            if (StringUtils.isBlank(parentId)) {
                bulkRequest.add(client.prepareIndex(indexName, type, key).setSource(data));
            } else {
                LOGGER.debug("parent: " + parentId);
                bulkRequest.add(client.prepareIndex(indexName, type, key).setSource(data).setParent(parentId));
            }
        }

        try {
            BulkResponse bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                //TODO index failed. Figure out a better way to determine when to retry this message
                LOGGER.error("Cannot execute bulk request: " + bulkResponse.buildFailureMessage());
            }
        } catch (ElasticSearchException e) {
            StormElasticSearchUtils.handleElasticSearchException(getClass(), e);
        }
    }
}
