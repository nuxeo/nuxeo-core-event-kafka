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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DefaultKafkaServiceImpl implements DefaultKafkaService {

    private static final Log log = LogFactory.getLog(DefaultKafkaServiceImpl.class);

    private String mHost;
    private Properties mProducerProperties;
    private Properties mConsumerProperties;
    private List<String> mTopics = new ArrayList<>();

    @Override
    public String getHost() {
        return mHost;
    }

    @Override
    public void setHost(String host) {
        mHost = host;
    }

    @Override
    public Properties getProducerProperties() {
        if (mProducerProperties != null && mHost != null) {
            mProducerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, mHost);
        }
        return mProducerProperties;
    }

    @Override
    public void setProducerProperties(Properties producerProperties) {
        mProducerProperties = producerProperties;
    }

    @Override
    public Properties getConsumerProperties() {
        if (mConsumerProperties != null && mHost != null) {
            mConsumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, mHost);
        }
        return mConsumerProperties;
    }

    @Override
    public void setConsumerProperties(Properties consumerProperties) {
        mConsumerProperties = consumerProperties;
    }

    @Override
    public List<String> allTopics() {
        return mTopics;
    }

    @Override
    public void addTopic(String topic) {
        if (!mTopics.contains(topic)) {
            mTopics.add(topic);
        }
    }

    @Override
    public boolean removeTopic(String topic) {
        return mTopics.remove(topic);
    }
}
