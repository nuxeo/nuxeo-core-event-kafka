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
 */
package org.nuxeo.ecm.core.event.kafka;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.event.kafka.service.DefaultKafkaService;
import org.nuxeo.ecm.core.event.kafka.test.KafkaFeature;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;


@RunWith(FeaturesRunner.class)
@Features({ KafkaFeature.class, CoreFeature.class})
@RepositoryConfig(cleanup = Granularity.METHOD)
@LocalDeploy("org.nuxeo.ecm.core.event.kafka.test:test-kafka-service-contrib.xml")
public class TestKafkaSetup {

    protected static final Log log = LogFactory.getLog(TestKafkaSetup.class);

    @Test
    public void testTopicReadWrite() throws InterruptedException{
        DefaultKafkaService service = Framework.getService(DefaultKafkaService.class);
        // setup producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(service.getProducerProperties());
        // setup consumer
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(service.getConsumerProperties());

        List<String> topics = service.allTopics();
        consumer.subscribe(service.allTopics());

        // send something
        ProducerRecord<String, String> data = new ProducerRecord<>(
                topics.get(new Random().nextInt(topics.size())),
                "T",
                "testMessage");
        producer.send(data);
        producer.flush();

        // check consumer !
        ConsumerRecords<String, String> records = consumer.poll(2000); // 2000 is the min !

        assertEquals(1, records.count());

        producer.close();
        consumer.close();
    }

}
