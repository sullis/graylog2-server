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
package org.graylog.plugins.beats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.MessageFactory;
import org.graylog2.plugin.TestMessageFactory;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.inputs.failure.InputProcessingException;
import org.graylog2.plugin.journal.RawMessage;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeatsCodecTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Configuration configuration;
    private ObjectMapper objectMapper;
    private BeatsCodec codec;
    private final MessageFactory messageFactory = new TestMessageFactory();

    @Before
    public void setUp() throws Exception {
        objectMapper = new ObjectMapperProvider().get();
        codec = new BeatsCodec(configuration, objectMapper, messageFactory);
    }

    @Test
    public void decodeReturnsNullIfPayloadCouldNotBeDecoded() {
        assertThatThrownBy(() -> codec.decodeSafe(new RawMessage(new byte[0])))
                .isInstanceOf(InputProcessingException.class);
    }

    @Test
    public void decodeMessagesHandlesFilebeatMessages() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("filebeat.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("TEST");
        assertThat(message.getSource()).isEqualTo("example.local");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("filebeat");
        assertThat(message.getField("file")).isEqualTo("/tmp/test.log");
        assertThat(message.getField("type")).isEqualTo("log");
        assertThat(message.getField("count")).isEqualTo(1);
        assertThat(message.getField("offset")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        final List<String> tags = (List<String>) message.getField("tags");
        assertThat(tags).containsOnly("foobar", "test");
    }

    @Test
    public void decodeMessagesHandlesPacketbeatMessages() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("packetbeat-dns.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getSource()).isEqualTo("example.local");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("packetbeat");
        assertThat(message.getField("type")).isEqualTo("dns");
    }

    @Test
    public void decodeMessagesHandlesTopbeatMessages() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("topbeat-system.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getSource()).isEqualTo("example.local");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("topbeat");
        assertThat(message.getField("type")).isEqualTo("system");
    }

    @Test
    public void decodeMessagesHandlesWinlogbeatMessages() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("winlogbeat.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getSource()).isEqualTo("example.local");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 11, 24, 12, 13, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("winlogbeat");
        assertThat(message.getField("type")).isEqualTo("wineventlog");
        assertThat(message.getField("winlogbeat_level")).isEqualTo("Information");
        assertThat(message.getField("winlogbeat_event_id")).isEqualTo(5024);
        assertThat(message.getField("winlogbeat_process_id")).isEqualTo(500);
        assertThat(message.getField("winlogbeat_log_name")).isEqualTo("Security");
    }

    @Test
    public void decodeMessagesHandleGenericBeatMessages() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic.json")).get();

        assertThat(message).isNotNull();
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
    }

    @Test
    public void decodeMessagesHandleGenericBeatMessagesWithFields() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-fields.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("foo_field")).isEqualTo("bar");
    }

    @Test
    public void decodeMessagesHandlesMetricbeatMessages() throws Exception {
        final String[] testFiles = {
                "metricbeat-docker-container.json",
                "metricbeat-docker-cpu.json",
                "metricbeat-docker-diskio.json",
                "metricbeat-docker-info.json",
                "metricbeat-docker-memory.json",
                "metricbeat-docker-network.json",
                "metricbeat-mongodb-status.json",
                "metricbeat-mysql-status.json",
                "metricbeat-system-core.json",
                "metricbeat-system-cpu.json",
                "metricbeat-system-filesystem.json",
                "metricbeat-system-fsstat.json",
                "metricbeat-system-load.json",
                "metricbeat-system-memory.json",
                "metricbeat-system-network.json",
                "metricbeat-system-process.json"
        };

        for (String testFile : testFiles) {
            final Message message = codec.decodeSafe(messageFromJson(testFile)).get();
            assertThat(message).isNotNull();
            assertThat(message.getSource()).isEqualTo("example.local");
            assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 12, 14, 12, 0, DateTimeZone.UTC));
            assertThat(message.getField("facility")).isEqualTo("metricbeat");
        }
    }

    @Test
    public void decodeMessagesHandlesGenericBeatWithDocker() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-docker.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("null");
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("beat_docker_id")).isEqualTo("123");
        assertThat(message.getField("beat_docker_name")).isEqualTo("container-1");
        assertThat(message.getField("beat_docker_labels_docker-kubernetes-pod")).isEqualTo("hello");
    }

    @Test
    public void decodeMessagesHandlesGenericBeatWithKubernetes() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-kubernetes.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("null");
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("beat_kubernetes_pod_name")).isEqualTo("testpod");
        assertThat(message.getField("beat_kubernetes_namespace")).isEqualTo("testns");
        assertThat(message.getField("beat_kubernetes_labels_labelkey")).isEqualTo("labelvalue");
    }

    @Test
    public void decodeMessagesHandlesGenericBeatWithCloudAlibaba() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-cloud-alibaba.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("null");
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("beat_meta_cloud_provider")).isEqualTo("ecs");
        assertThat(message.getField("beat_meta_cloud_instance_id")).isEqualTo("i-wz9g2hqiikg0aliyun2b");
        assertThat(message.getField("beat_meta_cloud_availability_zone")).isEqualTo("cn-shenzhen");
        assertThat(message.getField("beat_meta_cloud_region")).isEqualTo("cn-shenzhen-a");
    }

    @Test
    public void decodeMessagesHandlesGenericBeatWithCloudDigitalOcean() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-cloud-digital-ocean.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("null");
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("beat_meta_cloud_provider")).isEqualTo("digitalocean");
        assertThat(message.getField("beat_meta_cloud_instance_id")).isEqualTo("1234567");
        assertThat(message.getField("beat_meta_cloud_region")).isEqualTo("nyc2");
    }

    @Test
    public void decodeMessagesHandlesGenericBeatWithCloudEC2() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-cloud-ec2.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("null");
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("beat_meta_cloud_provider")).isEqualTo("ec2");
        assertThat(message.getField("beat_meta_cloud_machine_type")).isEqualTo("t2.medium");
        assertThat(message.getField("beat_meta_cloud_instance_id")).isEqualTo("i-4e123456");
        assertThat(message.getField("beat_meta_cloud_region")).isEqualTo("us-east-1");
        assertThat(message.getField("beat_meta_cloud_availability_zone")).isEqualTo("us-east-1c");
    }

    @Test
    public void decodeMessagesHandlesGenericBeatWithCloudGCE() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-cloud-gce.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("null");
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("beat_meta_cloud_provider")).isEqualTo("gce");
        assertThat(message.getField("beat_meta_cloud_machine_type")).isEqualTo("projects/1234567890/machineTypes/f1-micro");
        assertThat(message.getField("beat_meta_cloud_instance_id")).isEqualTo("1234556778987654321");
        assertThat(message.getField("beat_meta_cloud_project_id")).isEqualTo("my-dev");
        assertThat(message.getField("beat_meta_cloud_availability_zone")).isEqualTo("projects/1234567890/zones/us-east1-b");
    }

    @Test
    public void decodeMessagesHandlesGenericBeatWithCloudTencent() throws Exception {
        final Message message = codec.decodeSafe(messageFromJson("generic-with-cloud-tencent.json")).get();
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("null");
        assertThat(message.getSource()).isEqualTo("unknown");
        assertThat(message.getTimestamp()).isEqualTo(new DateTime(2016, 4, 1, 0, 0, DateTimeZone.UTC));
        assertThat(message.getField("facility")).isEqualTo("genericbeat");
        assertThat(message.getField("beat_foo")).isEqualTo("bar");
        assertThat(message.getField("beat_meta_cloud_provider")).isEqualTo("qcloud");
        assertThat(message.getField("beat_meta_cloud_instance_id")).isEqualTo("ins-qcloudv5");
        assertThat(message.getField("beat_meta_cloud_region")).isEqualTo("china-south-gz");
        assertThat(message.getField("beat_meta_cloud_availability_zone")).isEqualTo("gz-azone2");
    }

    private RawMessage messageFromJson(String resourceName) throws IOException {
        final URL resource = Resources.getResource(this.getClass(), resourceName);
        final byte[] json = Resources.toByteArray(resource);
        return new RawMessage(json);
    }
}
