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
    private static final int MAX_WORKERS = 100;
    private static final long AWAIT_TIME_MS = 2000L;

    private volatile boolean canStop = false;

    private List<String> topics;
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;

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
            log.error("Couldn't propagate topics", e);
        }
        initConsumerThread();
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
    }

    @Override
    public void sendEventBundle(EventBundle events) {
        super.sendEventBundle(events);
        producer.flush();
    }

    @Override
    public void shutdown() throws InterruptedException {
        consumerTPE.shutdown();
        asyncExec.shutdown(AWAIT_TIME_MS);
        waitForCompletion(AWAIT_TIME_MS);
        producer.close();
    }

    @Override
    public boolean waitForCompletion(long timeoutMillis) throws InterruptedException {
        canStop = true;
        consumerTPE.awaitTermination(5, TimeUnit.SECONDS);
        asyncExec.waitForCompletion(timeoutMillis);
        return true;
    }

    private void initConsumerThread() {
        asyncExec = new AsyncEventExecutor();
        consumerTPE = new ThreadPoolExecutor(1, 1, 60, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        consumerTPE.prestartCoreThread();
        consumerTPE.execute(new ConsumerExecutor());
    }

    public class ConsumerExecutor implements Runnable {

        private final int IDLE_TIMEOUT_MS = 200;

        private void process(ConsumerRecord<String, String> record) throws Exception {
            String message = record.value();

            EventBundle bundle = io.unmarshal(message);
            EventServiceAdmin eventService = Framework.getService(EventServiceAdmin.class);
            EventListenerList listeners = eventService.getListenerList();
            List<EventListenerDescriptor> postCommitAsync = listeners.getEnabledAsyncPostCommitListenersDescriptors();
            log.debug("About to process event bundle: " + message);
            asyncExec.run(postCommitAsync, bundle);
        }

        @Override
        public void run() {
            log.debug("ConsumerExecutor started");
            long processed = 0;
            long exceptions = 0;

            final WorkManager workManager = Framework.getService(WorkManager.class);
            while (!canStop) {
                if (workManager.getMetrics("default").getRunning().intValue() > MAX_WORKERS) {
                    try {
                        Thread.sleep(IDLE_TIMEOUT_MS);
                        continue;
                    } catch (InterruptedException e) {
                        log.error("Consumer thread interrupted", e);
                        consumer.close();
                        Thread.currentThread().interrupt();
                    }
                }

                ConsumerRecords<String, String> records = consumer.poll(AWAIT_TIME_MS);
                for (ConsumerRecord<String, String> record : records) {
                    log.debug("Getting " + record.value());
                    try {
                        process(record);
                        processed++;
                    } catch (Exception e) {
                        log.error("Couldn't process the event " + record.value(), e);
                        exceptions++;
                    }
                }
                consumer.commitSync();
            }
            consumer.close();

            String formatted = String.format("ConsumerExecutor finished its job. Processed %d events. %d threw exceptions", processed, exceptions);
            log.debug(formatted);
        }
    }
}
