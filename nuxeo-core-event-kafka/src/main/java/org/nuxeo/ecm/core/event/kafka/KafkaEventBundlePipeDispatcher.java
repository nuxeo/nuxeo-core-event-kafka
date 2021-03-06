package org.nuxeo.ecm.core.event.kafka;
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
 * Contributors:
 *     tiry
 *     anechaev
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.pipe.EventBundlePipe;
import org.nuxeo.ecm.core.event.pipe.EventPipeDescriptor;
import org.nuxeo.ecm.core.event.pipe.dispatch.EventBundleDispatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Basic implementation that simply forwards {@link EventBundle} to all underlying {@link EventBundlePipe}
 *
 * @since 8.4
 */
public class KafkaEventBundlePipeDispatcher implements EventBundleDispatcher {

    private static final Log log = LogFactory.getLog(KafkaEventBundlePipeDispatcher.class);
    protected List<EventBundlePipe> pipes = new ArrayList<>();

    @Override
    public void init(List<EventPipeDescriptor> pipeDescriptors, Map<String, String> parameters) {
        pipeDescriptors.sort(Comparator.comparing(EventPipeDescriptor::getPriority));

        for (EventPipeDescriptor descriptor : pipeDescriptors) {
            EventBundlePipe pipe = descriptor.getInstance();
            pipe.initPipe(descriptor.getName(), descriptor.getParameters());
            pipes.add(pipe);
        }
    }

    @Override
    public void sendEventBundle(EventBundle events) {
        if (events.isEmpty()) {
            return;
        }
        for (EventBundlePipe pipe : pipes) {
            pipe.sendEventBundle(events);
        }
    }

    @Override
    public boolean waitForCompletion(long timeoutMillis) throws InterruptedException {
        boolean res = true;
        for (EventBundlePipe pipe : pipes) {
            res = pipe.waitForCompletion(timeoutMillis) && res;
        }
        return res;
    }

    @Override
    public void shutdown() throws InterruptedException {
        for (EventBundlePipe pipe : pipes) {
            pipe.shutdown();
        }
    }
}

