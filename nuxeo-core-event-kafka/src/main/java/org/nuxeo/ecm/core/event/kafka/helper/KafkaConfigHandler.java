/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.event.kafka.helper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.requests.CreateTopicsRequest;
import org.apache.kafka.common.requests.CreateTopicsResponse;
import org.apache.kafka.common.requests.RequestHeader;
import org.apache.kafka.common.requests.ResponseHeader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KafkaConfigHandler {

    private static final Log log = LogFactory.getLog(KafkaConfigHandler.class);
    private static final Integer DEFAULT_TIMEOUT = 5000;

    private static final short apiKey = ApiKeys.CREATE_TOPICS.id;
    public static final short version = 0;
    private static final short correlationId = -1;

    public static List<String> propagateTopics(String host, List<String> topics) throws IOException {
        CreateTopicsRequest.TopicDetails topicDetails = new CreateTopicsRequest.TopicDetails(1, (short)1);
        Map<String, CreateTopicsRequest.TopicDetails> topicConfig = topics.stream()
                .collect(Collectors.toMap(k -> k, v -> topicDetails));

        CreateTopicsRequest request = new CreateTopicsRequest(topicConfig, DEFAULT_TIMEOUT);

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
