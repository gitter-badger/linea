package com.srotya.linea;

import org.junit.Test;

import com.srotya.linea.Event;
import com.srotya.linea.network.InternalTCPTransportServer.KryoObjectEncoder;

public class TestKryoSerialization {
	
	@Test
	public void testSerializationSize() throws Exception {
		Event event = new Event();
		event.getHeaders().put("host", "xyz.srotya.com");
		event.getHeaders().put("message", "ix-dc9-19.ix.netcom.com - - [04/Sep/1995:00:00:28 -0400] \"GET /html/cgi.html HTTP/1.0\" 200 2217\r\n");
		event.getHeaders().put("value", 10);
		event.setEventId(13123134234L);
		byte[] ary = KryoObjectEncoder.eventToByteArray(event, false);
		System.out.println("Without Compression Array Length:"+ary.length);
		ary = KryoObjectEncoder.eventToByteArray(event, true);
		System.out.println("With Compression Array Length:"+ary.length);
	}

}
