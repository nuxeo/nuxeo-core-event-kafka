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

package org.nuxeo.ecm.core.event.kafka;

import com.google.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.SimplePrincipal;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventServiceImpl;
import org.nuxeo.ecm.core.event.impl.UnboundEventContext;
import org.nuxeo.ecm.core.event.kafka.test.KafkaEventBusFeature;
import org.nuxeo.ecm.core.event.pipe.dispatch.EventBundleDispatcher;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import java.security.Principal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({ KafkaEventBusFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@LocalDeploy({
        "org.nuxeo.ecm.core.event.kafka.test:test-kafka-service-contrib.xml",
        "org.nuxeo.ecm.core.event.kafka.test:test-async-listeners.xml"
})
public class TestKafkaPipeKeepingLimitedWorkers {

    private static final int MAX_WORKERS = 5;
    private static final int MAX_EVENTS = 1000;

    private Principal principal = new SimplePrincipal("kafkaEventPrincipal");

    @Inject
    private EventService eventService;

    @Inject
    private CoreSession mSession;

    @Before
    public void setup() {
        DummyEventListener.events.clear();
        assertNotNull(principal);
    }

    @After
    public void teardown() {
        DummyEventListener.events.clear();
    }

    @Test
    public void shouldKeepWorkersLowerThanMax() throws InterruptedException {
        EventBundleDispatcher dispatcher = ((EventServiceImpl) eventService).getEventBundleDispatcher();
        // check that kafka pipe is indeed deployed !
        assertNotNull(dispatcher);

        DummyEventListener.init();

        UnboundEventContext ctx = new UnboundEventContext(principal, null);
        ctx.setRepositoryName(mSession.getRepositoryName());
        ctx.setCoreSession(mSession);
        ctx.setProperty("sessionId", mSession.getSessionId());
        assertNotNull(ctx.getCoreSession());

        int counter = 0;
        for (; counter < MAX_EVENTS; counter++) {
            eventService.fireEvent(ctx.newEvent("Test1"));
        }

        Thread.sleep(300);

        WorkManager manager = Framework.getService(WorkManager.class);
        int activeWorkers = manager.getMetrics("default").getRunning().intValue();
        assertTrue("Running workers should be more than 0", activeWorkers > 0);
        assertTrue("Active workers should be less than the default amount", activeWorkers <= MAX_WORKERS);

        eventService.waitForAsyncCompletion();

        assertEquals(counter, manager.getMetrics("default").getCompleted().intValue());
    }
}
