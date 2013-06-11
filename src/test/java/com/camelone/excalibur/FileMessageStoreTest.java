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

import junit.framework.Assert;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.Test;

import com.camelone.claimcheck.MessageStore;
import com.camelone.excalibur.types.Reservation;


public class FileMessageStoreTest extends CamelTestSupport {

	@Test
	public void testPersistence() throws Exception {
		JaxbDataFormat jaxb = new JaxbDataFormat("com.camelone.excalibur.types");
		jaxb.setCamelContext(context());
		jaxb.start();

		File bay = new File("/x1/camelone/excalibur/test");
		bay.mkdirs();

		String id = "co2013-one";
		MessageStore store = new FileMessageStore(bay, jaxb, context(), ReservationBuilder.excaliburIdReader());

		Exchange exchange = new DefaultExchange(context());
		Reservation reservation = new Reservation();
		reservation.setId(id);
		reservation.setName("Hadrian");
		reservation.setOrigin("RDU");
		reservation.setDestination("BOS");
		reservation.setRequest("flight,hotel");
		exchange.getIn().setBody(reservation);
		store.put(exchange.getExchangeId(), exchange);

		MessageStore restore = new FileMessageStore(bay, jaxb, context(), ReservationBuilder.excaliburIdReader());
		Assert.assertEquals(1, restore.size());
		Assert.assertTrue(restore.containsKey(id));

		removeDir(bay);
	}


    private static void removeDir(File d) {
        String[] list = d.list();
        if (list == null) {
            list = new String[0];
        }
        for (String s : list) {
            File f = new File(d, s);
            if (f.isDirectory()) {
                removeDir(f);
            } else {
                f.delete();
            }
        }
        d.delete();
    }

}
