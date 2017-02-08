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
package org.nuxeo.ecm.core.event.kafka.test;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.MockTime;
import kafka.utils.TestUtils;
import kafka.utils.Time;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import kafka.zk.EmbeddedZookeeper;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Simple test feature used to deploy a Kafka/Zookeeper infrastructure
 *
 * @since 8.4
 */
@Deploy({
        "org.nuxeo.ecm.core.event.kafka"
})
public class KafkaFeature extends SimpleFeature {

    private static final String ZK_HOST = "127.0.0.1";

    public static final String BROKER_HOST = "127.0.0.1";

    public static final String BROKER_PORT = "9092";

    protected KafkaServer kafkaServer;

    protected ZkClient zkClient;

    protected EmbeddedZookeeper zkServer;

    protected static final Log log = LogFactory.getLog(KafkaFeature.class);

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        super.beforeRun(runner);
        log.debug("**** Starting Kafka test environment");

        // setup ZooKeeper
        zkServer = new EmbeddedZookeeper();
        String zkConnect = ZK_HOST + ":" + zkServer.port();
        zkClient = new ZkClient(zkConnect, 30000, 30000, ZKStringSerializer$.MODULE$);
        ZkUtils zkUtils = ZkUtils.apply(zkClient, false);

        // setup Broker
        Properties brokerProps = setupProperties(zkConnect);
        KafkaConfig config = new KafkaConfig(brokerProps);

        Time mock = new MockTime();
        kafkaServer = TestUtils.createServer(config, mock);
        kafkaServer.startup();

        assertEquals(1, zkUtils.getAllBrokersInCluster().size());
        log.debug("**** Kafka test environment Started");
    }

    @Override
    public void afterRun(FeaturesRunner runner) throws Exception {
        log.debug("**** Shutting down Kafka test environment");
        kafkaServer.shutdown();
        zkClient.close();
        zkServer.shutdown();
        log.debug("**** Kafka test environment Stopped");
    }

    private Properties setupProperties(String zk) throws IOException {
        Properties props = new Properties();
        props.put("broker.id", 0);
        props.put("host.name", BROKER_HOST);
        props.put("port", BROKER_PORT);
        props.put("num.partitions", 4);
        props.put("default.replication.factor", 1);
        props.put("replica.fetch.max.bytes", 4194304);
        props.put("message.max.bytes", 30100);
        props.put("zookeeper.connect", zk);
        props.put("zookeeper.connection.timeout.ms", 3000);
        props.setProperty("log.dirs", Files.createTempDirectory("kafka-").toAbsolutePath().toString());

        return props;
    }
}
