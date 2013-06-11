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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import com.camelone.excalibur.types.Request;
import com.camelone.excalibur.types.Reservation;


public class ReservationProcessTest extends CamelTestSupport {

	@Test
	public void testReservation() throws Exception {
		String id = "camelone-001";
		String name = "Hadrian";

		Reservation reservation = new Reservation();
		reservation.setId(id);
		reservation.setName(name);
		reservation.setOrigin("RDU");
		reservation.setDestination("BOS");
		reservation.setRequest("flight,hotel");

		template.sendBody("direct:reservations", reservation);
		Thread.sleep(1000);

		Request request = new Request();
		request.setId(id);
		request.setName(name);
		request.setType("flight");
		request.setValue("ACME Airlines");
		template.sendBody("file:/x1/camelone/excalibur/reply", request);
		Thread.sleep(1000);

		request = new Request();
		request.setId(id);
		request.setName(name);
		request.setType("hotel");
		request.setValue("Hilton");
		template.sendBody("file:/x1/camelone/excalibur/reply", request);

		Thread.sleep(5000);
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
	    return new ReservationBuilder();
	}
	
}
