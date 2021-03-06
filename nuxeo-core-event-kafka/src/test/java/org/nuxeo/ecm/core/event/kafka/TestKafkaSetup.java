/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     tiry
 *     anechaev
 */
package org.nuxeo.ecm.core.event.kafka;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.event.kafka.helper.KafkaConfigHandler;
import org.nuxeo.ecm.core.event.kafka.test.KafkaFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import java.io.IOException;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;


@RunWith(FeaturesRunner.class)
@Features({ KafkaFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@LocalDeploy("org.nuxeo.ecm.core.event.kafka.test:test-kafka-service-contrib.xml")
public class TestKafkaSetup {

    private static final Log log = LogFactory.getLog(TestKafkaSetup.class);
    private final String topic = "test-topic";

    @Before
    public void setup() throws IOException {
        String host = KafkaFeature.BROKER_HOST + ":" + KafkaFeature.BROKER_PORT;
        KafkaConfigHandler.propagateTopics(host, Collections.singletonList(topic));
    }

    @Test
    public void testTopicReadWrite() throws InterruptedException{
        // setup producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties());
        // setup consumer
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties());

        consumer.subscribe(Collections.singletonList(topic));

        // send something
        ProducerRecord<String, String> data = new ProducerRecord<>(
                topic,
                "T",
                "testMessage");
        producer.send(data);
        producer.flush();


        // check consumer !
        ConsumerRecords<String, String> records = consumer.poll(2000);

        assertEquals(1, records.count());

        producer.close();
        consumer.close();
    }

    private Properties producerProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 4194304);
        props.put("linger.ms",0);
        props.put("max.block.ms", 1000);
        props.put("compression.type", "none");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        return props;
    }

    private Properties consumerProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "testGroup");
        props.put("enable.auto.commit", true);
        props.put("auto.offset.reset", "earliest");
        props.put("auto.commit.interval.ms", 1000);
        props.put("heartbeat.interval.ms", 3000);
        props.put("session.timeout.ms", 10000);
        props.put("request.timeout.ms", 15000);
        props.put("max.partition.fetch.bytes", 30720);
        props.put("max.poll.records", 100);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        return props;
    }
}
