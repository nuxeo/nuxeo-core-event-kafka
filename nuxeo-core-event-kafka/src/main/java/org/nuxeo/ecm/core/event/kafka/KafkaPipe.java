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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventServiceAdmin;
import org.nuxeo.ecm.core.event.impl.AsyncEventExecutor;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.ecm.core.event.impl.EventListenerList;
import org.nuxeo.ecm.core.event.kafka.helper.KafkaConfigHandler;
import org.nuxeo.ecm.core.event.kafka.service.DefaultKafkaService;
import org.nuxeo.ecm.core.event.pipe.AbstractEventBundlePipe;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @since 8.4
 */
public class KafkaPipe extends AbstractEventBundlePipe<String> {

    private static final Log log = LogFactory.getLog(KafkaPipe.class);
    private static final int MAX_WORKERS = 5;
    private List<String> topics;

    private KafkaProducer<String, String> producer;

    private KafkaConsumer<String, String> consumer;

    private volatile boolean canStop = false;

    private ThreadPoolExecutor consumerTPE;
    private AsyncEventExecutor asyncExec;

    private EventBundleJSONIO io = new EventBundleJSONIO();

    @Override
    public void initPipe(String name, Map<String, String> params) {
        super.initPipe(name, params);
        DefaultKafkaService service = Framework.getService(DefaultKafkaService.class);

        topics = service.allTopics();

        producer = new KafkaProducer<>(service.getProducerProperties());

        consumer = new KafkaConsumer<>(service.getConsumerProperties());
        consumer.subscribe(topics);
        try {
            KafkaConfigHandler.propagateTopics(service.getHost(), topics);
        } catch (IOException e) {
            log.error(e);
        }
        initConsumerThread();
    }

    private void initConsumerThread() {
        asyncExec = new AsyncEventExecutor();
        consumerTPE = new ThreadPoolExecutor(1, 1, 60, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        consumerTPE.prestartCoreThread();
        consumerTPE.execute(new ConsumerExecutor());
    }

    @Override
    public void shutdown() throws InterruptedException {
        consumerTPE.shutdown();
        asyncExec.shutdown(2000L);
        waitForCompletion(2000L);
        producer.close();
    }

    @Override
    public boolean waitForCompletion(long timeoutMillis) throws InterruptedException {
        canStop = true;
        consumerTPE.awaitTermination(5, TimeUnit.SECONDS);
        asyncExec.waitForCompletion(timeoutMillis);
        return true;
    }

    @Override
    protected String marshall(EventBundle events) {
        return io.marshall(events);
    }

    @Override
    protected void send(String message) {
        log.info("Sending " + message);
        int rand = new Random().nextInt(topics.size());
        ProducerRecord<String, String> data = new ProducerRecord<>(topics.get(rand), null, message);
        producer.send(data);
        producer.flush();
    }


    public class ConsumerExecutor implements Runnable {

        private void process(ConsumerRecord<String, String> record) {
            String message = record.value();

            EventBundle bundle = io.unmarshal(message);
            EventServiceAdmin eventService = Framework.getService(EventServiceAdmin.class);
            EventListenerList listeners = eventService.getListenerList();
            List<EventListenerDescriptor> postCommitAsync = listeners.getEnabledAsyncPostCommitListenersDescriptors();
            log.debug("About to process event bundle: " + message);
            try {
                asyncExec.run(postCommitAsync, bundle);
            } catch (Exception e) {
                log.error(e);
            }
        }

        @Override
        public void run() {
            final WorkManager workManager = Framework.getService(WorkManager.class);
            boolean isEmpty = false;
            while (!canStop && !isEmpty) {
                if (workManager.getMetrics("default").getRunning().intValue() > MAX_WORKERS) {
                    try {
                        Thread.sleep(200);
                        continue;
                    } catch (InterruptedException e) {
                        log.error("Consumer thread interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }

                ConsumerRecords<String, String> records = consumer.poll(2000);
                isEmpty = canStop && records.isEmpty();
                for (ConsumerRecord<String, String> record : records) {
                    log.debug("Getting " + record.value());
                    process(record);
                }
            }
            consumer.close();
        }
    }
}
