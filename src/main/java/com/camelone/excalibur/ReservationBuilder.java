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
package com.camelone.excalibur;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camelone.claimcheck.ClaimCheck;
import com.camelone.claimcheck.ClaimCheck.BayInfo;
import com.camelone.excalibur.types.Request;
import com.camelone.excalibur.types.Reservation;


public class ReservationBuilder extends RouteBuilder {
	public static final String REQUESTS_RECIPIENTS = "CamelRecipients";
	public static final String DESTINATION_BAY = "excalibur";
	private static final Logger LOG = LoggerFactory.getLogger(ReservationBuilder.class);

	@Override
	public void configure() throws Exception {
		LOG.info("STARTING...");
		JaxbDataFormat jaxb = new JaxbDataFormat("com.camelone.excalibur.types");
		
		from("file:target/excalibur/reservations")
		    .unmarshal(jaxb)
		    .process(checkinReservation())
		    .process(generateRequests())
		    .recipientList(property(REQUESTS_RECIPIENTS).tokenize(","));
        from("direct:complete")
            .process(checkoutCompletion(jaxb));
        from("direct:exit")
            .marshal(jaxb)
            .to("file:target/excalibur/completed");

		// Mock routes sending actual requests asynchronously to make reservations
        from("seda:flight")
            .marshal(jaxb)
            .to("log:com.camelone.excalibur.FLIGHT");
        from("seda:hotel")
            .marshal(jaxb)
            .to("log:com.camelone.excalibur.HOTEL");
        from("seda:car")
            .marshal(jaxb)
            .to("log:com.camelone.excalibur.CAR");

        // Route(s) receiving replies asynchronously about reservations made
		from("file:target/excalibur/reply")
		    .unmarshal(jaxb)
            .setProperty(ClaimCheck.CLAIMCHECK_TAG_HEADER, reservationId())
		    .to("seda:reply");
		handleReply("seda:reply", DESTINATION_BAY);

        // test routes
		from("direct:reservations")
		    .marshal(jaxb)
		    .to("file:target/excalibur/reservations");
	}

	private Processor checkinReservation() {
	    return ClaimCheck.checkin()
            .at(constant("direct:complete"))
            .attach(reservationId())
            .keep(body())
            .ttl(10000);
	}

	private Processor checkoutCompletion(DataFormat df) {
		Processor completionHandler = ClaimCheck.co()
            .bay(DESTINATION_BAY)
            .aggregate(updateReservation())
            .check(processedAllRequests())
            .proceed("direct:exit");
		
		// Let's use a more persistent message store
		BayInfo bay = ClaimCheck.getBay(DESTINATION_BAY);
		File storageBay = new File("target/excalibur/store/");
		File main = new File(storageBay, "main");
		File carousel = new File(storageBay, "carousel");
		main.mkdirs();
		carousel.mkdirs();
		bay.main = new FileMessageStore(main, df, getContext(), excaliburIdReader());
		bay.carousel = new FileMessageStore(main, df, getContext(), excaliburIdReader());

		return completionHandler;
	}
	
	private Expression excaliburIdReader() {
		return new Expression() {
			@SuppressWarnings("unchecked")
			public <T> T evaluate(Exchange exchange, Class<T> type) {
				Message in = exchange.getIn();
				if (in != null && in.getBody() != null) {
					if (in.getBody() instanceof Reservation) {
						return (T)((Reservation)in.getBody()).getId();
					} else if (in.getBody() instanceof Request) {
						return (T)((Request)in.getBody()).getId();
					}
				}
				return (T)((DefaultExchange)exchange).getExchangeId();
			}
		};
	}

	private void handleReply(String uri, String bay) {
        ClaimCheck.arrival(this)
            .unload(uri)
	        .bay(bay);
	}

    private AggregationStrategy updateReservation() {
    	return new AggregationStrategy() {
            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                if (oldExchange == null || newExchange == null) {
                    throw new RuntimeCamelException("Can only aggregate when claimcheck exchanges are paired");
                }
                Reservation reservation = oldExchange.getIn().getBody(Reservation.class);
                Request reply = newExchange.getIn().getBody(Request.class);

                if ("flight".equalsIgnoreCase(reply.getType())) {
                	reservation.setFlight(reply.getValue());
                } else if ("hotel".equalsIgnoreCase(reply.getType())) {
                	reservation.setHotel(reply.getValue());
                } else if ("car".equalsIgnoreCase(reply.getType())) {
                	reservation.setCar(reply.getValue());
                }
                return oldExchange;
             }
    	};
    }

	private Processor generateRequests() {
		return new Processor() {
			@Override
			public void process(Exchange exchange) throws Exception {
				Reservation body = exchange.getIn().getBody(Reservation.class);
				if (body == null) {
					LOG.warn("Could not unmarshal reservation...");
					return;
				}
				exchange.setProperty(REQUESTS_RECIPIENTS, "");
				if (body.getRequest() != null) {
					String recipients = "";
			        String[] request = body.getRequest().split(",");
			        for (String r : request) {
			        	if (recipients.length() > 0) {
			        		recipients += ",";
			        	}
			        	recipients += "seda:" + r;
			        }
			        LOG.info("Sending reservation requests to followig systems: {}", recipients);
					exchange.setProperty(REQUESTS_RECIPIENTS, recipients);

			        Request req = new Request();
					req.setId(body.getId());
					req.setName(body.getName());
					exchange.getOut().setBody(req);
				}
			}
	    };
	}
	
	private static Expression reservationId() {
		return new Expression()  {
			@SuppressWarnings("unchecked")
			public <T> T evaluate(Exchange exchange, Class<T> type) {
				Object body = exchange.getIn().getBody();
				if (body == null) {
					return (T)"n/a";
				} else if (body instanceof Reservation) {
					return (T)((Reservation)body).getId();
				} else if (body instanceof Request) {
					return (T)((Request)body).getId();
				}
				return (T)"n/a";
			}
		};
	}

	private static Predicate processedAllRequests() {
		return new Predicate() {
			public boolean matches(Exchange exchange) {
				Reservation res = exchange.getIn().getBody(Reservation.class);
				if (res != null) {
			        String[] request = res.getRequest().split(",");
			        for (String r : request) {
			        	boolean all = "flight".equalsIgnoreCase(r) 
			        		? (res.getFlight() != null && res.getFlight().length() > 0)
			        	    : "hotel".equalsIgnoreCase(r)
			        		? (res.getHotel() != null && res.getHotel().length() > 0)
			        	    : "car".equalsIgnoreCase(r)
			        		? (res.getCar() != null && res.getCar().length() > 0) 
			        	    : false;
			        	if (!all) {
			        		LOG.info("Still waiting for replies on open requests");
			        		return false;
			        	}
			        }
	        		LOG.info("All open requests satisified - completing reservation");
			        return true;
				}
				return false;
			}
		};
	}
}
