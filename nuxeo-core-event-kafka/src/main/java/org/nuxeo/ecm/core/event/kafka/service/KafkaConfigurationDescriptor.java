/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ecm.core.event.kafka.service;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@XObject("kafkaConfig")
public class KafkaConfigurationDescriptor {

    protected Properties producerProperties;

    protected Properties consumerProperties;

    @XNode("@bootstrapServer")
    protected String bootstrapServer;

    @XNode("producerConfigs")
    protected ProducerConfiguration producerConfiguration;

    @XNode("consumerConfigs")
    protected ConsumerConfiguration consumerConfiguration;

    @XNodeList(value = "topics/topic", type = ArrayList.class, componentType = String.class)
    protected List<String> topics = new ArrayList<>();

    public void setProducerProperties(Properties producerProperties) {
        this.producerProperties = producerProperties;
    }

    public void setConsumerProperties(Properties consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    public Properties getProducerProperties() {
        return producerConfiguration.properties;
    }

    public Properties getConsumerProperties() {
        return consumerConfiguration.properties;
    }

    public List<String> getTopics() {
        return topics;
    }

    @XObject("producerConfigs")
    public static class ProducerConfiguration {
        @XNodeMap(value = "property", key = "@name", type = Properties.class, componentType = String.class)
        protected Properties properties = new Properties();
    }

    @XObject("consumerConfigs")
    public static class ConsumerConfiguration {
        @XNodeMap(value = "property", key = "@name", type = Properties.class, componentType = String.class)
        protected Properties properties= new Properties();
    }
}
