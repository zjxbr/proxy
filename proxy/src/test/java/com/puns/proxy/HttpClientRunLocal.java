package com.puns.proxy;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

/**
 * Hello world!
 *
 */
public class HttpClientRunLocal {
	public static void main(String[] args) throws InterruptedException {

		List<String> urList = new ArrayList<>();
		urList.add("http://www.amazon.co.jp/");
		// urList.add("http://www.amazon.co.jp/");
		// urList.add("http://www.amazon.co.jp/");
		// urList.add("http://kakaku.com/");
		// urList.add("http://tabelog.com/tokyo/");
		// urList.add("http://photohito.com/");

		for (int i = 0; i < urList.size(); i++) {

			Thread thread = new Thread(new HTTPRunnable(urList.get(i)));
			thread.start();
			Thread.sleep(1000);
		}
	}

	public static class HTTPRunnable implements Runnable {
		final private String url;

		public HTTPRunnable(String url) {
			this.url = url;
		}

		@Override
		public void run() {
			HttpClient httpClient = new HttpClient();
			// httpClient.getHostConfiguration().setProxy("218.251.240.91",
			// 3128);
			httpClient.getHostConfiguration().setProxy("localhost", 3128);
			// httpClient.getHostConfiguration().setProxy("proxysv", 80);

			DefaultHttpMethodRetryHandler retryhandler = new DefaultHttpMethodRetryHandler(
					0, true);
			httpClient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
					retryhandler);
			httpClient.getHttpConnectionManager().getParams()
					.setConnectionTimeout(1000000);
			httpClient.getHttpConnectionManager().getParams()
					.setSoTimeout(1000000);

			GetMethod method = new GetMethod(url);
			// method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
			// new DefaultHttpMethodRetryHandler(3, false));
			try {
				int statusCode = 0;
				try {
					statusCode = httpClient.executeMethod(method);
				} catch (SocketException e) {
					// 服务器负载过大，中止连接
					// TODO
				}

				Header[] headers = method.getResponseHeaders();

				for (Header header : headers) {
					System.out.println(header.getName() + ","
							+ header.getValue());
				}
				Header header = method.getResponseHeader("ProxyPuns");
				if (header != null) {
					System.out.println(header.getValue());
				}

				System.out.println(statusCode + ": " + method.getStatusLine());
				if (statusCode != HttpStatus.SC_OK) {
					System.out.println(statusCode + ": "
							+ method.getStatusLine());
				} else {
					// System.out.println(new String(method.getResponseBody(),
					// "GBK"));
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				method.releaseConnection();
			}
		}
	}

}
