package com.puns.proxy.httpserver;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.lang.reflect.Constructor;

public class HttpReponseStatusExpand extends HttpResponseStatus {

	public HttpReponseStatusExpand(int code, String reasonPhrase) {
		super(code, reasonPhrase);
	}

	public static final HttpResponseStatus PROXY_CONN_FAIL;
	static {
		Constructor<HttpResponseStatus> con;
		HttpResponseStatus proxyNotEnough = null;
		try {
			con = HttpResponseStatus.class.getDeclaredConstructor(
					Integer.TYPE, String.class, Boolean.TYPE);
			if (con == null) {
				System.err.println("不应该为空");
			} else {
				con.setAccessible(true);
				proxyNotEnough = con.newInstance(988, "ProxyConnFailed", true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		PROXY_CONN_FAIL = proxyNotEnough;
	}

}
