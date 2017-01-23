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
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.requests.CreateTopicsRequest;
import org.apache.kafka.common.requests.CreateTopicsResponse;
import org.apache.kafka.common.requests.RequestHeader;
import org.apache.kafka.common.requests.ResponseHeader;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventServiceAdmin;
import org.nuxeo.ecm.core.event.impl.AsyncEventExecutor;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.ecm.core.event.impl.EventListenerList;
import org.nuxeo.ecm.core.event.kafka.service.DefaultKafkaService;
import org.nuxeo.ecm.core.event.pipe.AbstractEventBundlePipe;
import org.nuxeo.runtime.api.Framework;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @since 8.4
 */
public class KafkaPipe extends AbstractEventBundlePipe<String> {

    private static final Log log = LogFactory.getLog(KafkaPipe.class);
    private static final short apiKey = ApiKeys.CREATE_TOPICS.id;
    public static final short version = 0;
    private static final short correlationId = -1;

    protected String brokerHost;
    protected List<String> topics;

    protected KafkaProducer<String, String> producer;

    protected KafkaConsumer<String, String> consumer;

    protected boolean stop = false;

    protected ThreadPoolExecutor consumerTPE;

    protected EventBundleJSONIO io = new EventBundleJSONIO();

    @Override
    public void initPipe(String name, Map<String, String> params) {
        super.initPipe(name, params);
        DefaultKafkaService service = Framework.getService(DefaultKafkaService.class);
        brokerHost = service.getHost();

        topics = service.allTopics();

        producer = new KafkaProducer<>(service.getProducerProperties());

        consumer = new KafkaConsumer<>(service.getConsumerProperties());
        consumer.subscribe(topics);
        try {
            propagateTopics(10000, service.getHost());
        } catch (IOException e) {
            log.error(e);
        }
        initConsumerThread();
    }

    protected void initConsumerThread() {

        AsyncEventExecutor asyncExec = new AsyncEventExecutor();
        consumerTPE = new ThreadPoolExecutor(1, 1, 60, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        consumerTPE.prestartCoreThread();
        consumerTPE.execute(new Runnable() {

            protected void process(ConsumerRecord<String, String> record) {
                String message = record.value();

                EventBundle bundle = io.unmarshal(message);

                // direct exec ?!
                EventServiceAdmin eventService = Framework.getService(EventServiceAdmin.class);
                EventListenerList listeners = eventService.getListenerList();
                List<EventListenerDescriptor> postCommitAsync = listeners.getEnabledAsyncPostCommitListenersDescriptors();
                asyncExec.run(postCommitAsync, bundle);
            }

            @Override
            public void run() {

                while (!stop) {
                    ConsumerRecords<String, String> records = consumer.poll(2000);
                    for (ConsumerRecord<String, String> record : records) {
                        process(record);
                    }
                }

                consumer.close();
            }
        });

    }

    @Override
    public void shutdown() throws InterruptedException {
        stop = true;
        waitForCompletion(5000L);
        consumerTPE.shutdown();
        producer.close();
    }

    @Override
    public boolean waitForCompletion(long timeoutMillis) throws InterruptedException {
        producer.flush();
        Thread.sleep(2000); // XXX
        return true;
    }

    @Override
    protected String marshall(EventBundle events) {
        return io.marshall(events);
    }

    @Override
    protected void send(String message) {
        int rand = new Random().nextInt(topics.size());
        ProducerRecord<String, String> data = new ProducerRecord<>(topics.get(rand), null, message);
        producer.send(data);
    }

    private List<String> propagateTopics(int timeout, String host) throws IOException {
        CreateTopicsRequest.TopicDetails topicDetails = new CreateTopicsRequest.TopicDetails(1, (short)1);
        Map<String, CreateTopicsRequest.TopicDetails> topicConfig = topics.stream()
                .collect(Collectors.toMap(k -> k, v -> topicDetails));

        CreateTopicsRequest request = new CreateTopicsRequest(topicConfig, timeout);

        List<String> errors = new ArrayList<>();
        try {
            CreateTopicsResponse response = createTopic(request, host);
            return response.errors().entrySet().stream()
//                    .filter(error -> error.getValue() == Errors.NONE)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error(e);
        }

        return errors;
    }

    private static CreateTopicsResponse createTopic(CreateTopicsRequest request, String client) throws IllegalArgumentException, IOException {
        String[] comp = client.split(":");
        if (comp.length != 2) {
            throw new IllegalArgumentException("Wrong client directive");
        }
        String address = comp[0];
        int port = Integer.parseInt(comp[1]);

        RequestHeader header = new RequestHeader(apiKey, version, client, correlationId);
        ByteBuffer buffer = ByteBuffer.allocate(header.sizeOf() + request.sizeOf());
        header.writeTo(buffer);
        request.writeTo(buffer);

        byte byteBuf[] = buffer.array();

        byte[] resp = requestAndReceive(byteBuf, address, port);
        ByteBuffer respBuffer = ByteBuffer.wrap(resp);
        ResponseHeader.parse(respBuffer);

        return CreateTopicsResponse.parse(respBuffer);
    }

    private static byte[] requestAndReceive(byte[] buffer, String address, int port) throws IOException {
        try(Socket socket = new Socket(address, port);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream())
        ) {
            dos.writeInt(buffer.length);
            dos.write(buffer);
            dos.flush();

            byte resp[] = new byte[dis.readInt()];
            dis.readFully(resp);

            return resp;
        } catch (IOException e) {
            log.error(e);
        }

        return new byte[0];
    }

}
