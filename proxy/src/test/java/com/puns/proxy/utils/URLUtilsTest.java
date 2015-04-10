package com.puns.proxy.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import com.puns.proxy.util.URLUtils;

public class URLUtilsTest {

	@Test
	public void test() {
		// fail("Not yet implemented");
		String domain = URLUtils
				.getDomain("GET http://www.amazon.co.jp/gp/aag/details//ref=aag_m_ss?ie=UTF8&seller=A001665895YWGQJVAG98 HTTP/1.1");
		
		assertEquals("www.amazon.co.jp", domain);
	}
	
	@Test
	public void sectest() {
		// fail("Not yet implemented");
		String domain = URLUtils
				.getDomain("GET http://www.amazon.co.jp");
		
		assertEquals("www.amazon.co.jp", domain);
	}

	@Test
	public void thirdtest() {
		// fail("Not yet implemented");
		String domain = URLUtils
				.getDomain("www.amazon.co.jp");
		assertNull(domain);
		
	}
	
}
