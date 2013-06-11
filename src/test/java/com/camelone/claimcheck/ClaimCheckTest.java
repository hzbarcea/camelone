/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.camelone.claimcheck;

import static com.camelone.claimcheck.ClaimCheck.exchangeId;
import static com.camelone.claimcheck.ClaimCheck.tag;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

import com.camelone.claimcheck.ClaimCheck;


public class ClaimCheckTest extends CamelTestSupport {
	private static final String DEMO_HEADER = "CamelDemo";

	@Before
	public void setUp() throws Exception {
		super.setUp();
		deleteDirectory("target/camelone/checkin");
		deleteDirectory("target/camelone/arrival");
	}

	@Test
    public void testSimpleClaimCheck() throws Exception {
		MockEndpoint exit = context.getEndpoint("mock:exit", MockEndpoint.class);
		exit.expectedMessageCount(1);
		exit.expectedBodiesReceived("HELLO WORLD");

		// claimcheck only makes sense for in-only mep
		template.sendBodyAndHeader("direct:simple", "Hello world", DEMO_HEADER, "camelone2013");
		
		Thread.sleep(2000);
		context.startRoute("baggage");
		
		assertMockEndpointsSatisfied();
	}

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:simple")
                    .process(ClaimCheck.checkin()
                        .at(constant("direct:checkin"))
                        .attach(tag(exchangeId()))
                        .keep(header(DEMO_HEADER))
                        .ttl(10000))
                    .to("seda:queue")
                    .setHeader(Exchange.FILE_NAME, property(ClaimCheck.CLAIMCHECK_TAG_HEADER))
                    .to("file:target/camelone/messages");
                
                from("direct:checkin")
                    .setHeader(Exchange.FILE_NAME, property(ClaimCheck.CLAIMCHECK_TAG_HEADER))
                    .to("file:target/camelone/checkin");

                from("seda:queue")
                    // .delay(5000)
                    .process(ClaimCheck.co()
                        .bay("Boston")
                        .aggregate(baggageToUpper())
                        .check(null)
                        .proceed("direct:exit"));

                from("direct:exit")
                    .to("log:exit")
                    .to("mock:exit");

                from("file:target/camelone/arrival")
                    .convertBodyTo(String.class)
                    .setProperty(ClaimCheck.CLAIMCHECK_TAG_HEADER, header("Exchange.FILE_NAME"))
                    .to("seda:arrival");

                ClaimCheck
                    .arrival(this)
                    .unload("seda:arrival")
            	    .bay("Boston");
                
                // test route; start this route after some delay to simulate some long processing
                from("file:target/camelone/checkin").routeId("baggage").autoStartup(false)
                    .to("file:target/camelone/arrival");
            }
        };
    }
    
    private AggregationStrategy baggageToUpper() {
    	return new AggregationStrategy() {
            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                if (oldExchange == null || newExchange == null) {
                    throw new RuntimeCamelException("Can only aggregate when claimcheck exchanges are paired");
                }
                String body = newExchange.getIn().getBody(String.class);

                oldExchange.getIn().setBody(body.toUpperCase());
                return oldExchange;
             }
    	};
    }

}
