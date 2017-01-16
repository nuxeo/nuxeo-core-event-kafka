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

import com.google.inject.Inject;
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
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import java.security.Principal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @since TODO
 */

@RunWith(FeaturesRunner.class)
@Features({ KafkaEventBusFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@LocalDeploy({
        "org.nuxeo.ecm.core.event.kafka.test:test-KafkaPipes.xml",
        "org.nuxeo.ecm.core.event.kafka.test:test-async-listeners.xml",
        "org.nuxeo.ecm.core.event.kafka.test:test-kafka-service-contrib.xml"
})
public class TestKafkaPipe {

    @Inject
    private EventService eventService;

    @Inject
    private CoreSession mSession;

    @Test
    public void sendEventViaKafka() throws Exception {

        EventBundleDispatcher dispatcher = ((EventServiceImpl) eventService).getEventBundleDispatcher();
        // check that kafka pipe is indeed deployed !
        assertNotNull(dispatcher);

        DummyEventListener.init();

        Principal principal = new SimplePrincipal("titi");
        assertNotNull(principal);
        UnboundEventContext ctx = new UnboundEventContext(principal, null);
        ctx.setCoreSession(mSession);
        assertNotNull(ctx.getCoreSession());
        eventService.fireEvent(ctx.newEvent("Test1"));
        eventService.fireEvent(ctx.newEvent("Test2"));
        eventService.waitForAsyncCompletion();

        assertEquals(2, DummyEventListener.events.size());
    }
}
