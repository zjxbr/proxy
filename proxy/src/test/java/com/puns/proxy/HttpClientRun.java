package com.puns.proxy;

import java.io.IOException;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

/**
 * Hello world!
 *
 */
public class HttpClientRun {
	public static void main(String[] args) throws InterruptedException {
		for (int i = 0; i < 10; i++) {

			Thread thread = new Thread(new HTTPRunnable());
			thread.start();
		}
		Thread.sleep(1000);
		for (int i = 0; i < 2; i++) {

			Thread thread = new Thread(new HTTPRunnable());
			thread.start();
		}
	}

	public static class  HTTPRunnable implements Runnable{
		@Override
		public void run() {
			HttpClient httpClient = new HttpClient();
			// httpClient.getHostConfiguration().setProxy("218.251.240.91",
			// 3128);
			httpClient.getHostConfiguration().setProxy("localhost", 3128);
			DefaultHttpMethodRetryHandler retryhandler = new DefaultHttpMethodRetryHandler(
					1, true);
			httpClient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
					retryhandler);

			GetMethod method = new GetMethod("http://www.baidu.com");
			// method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
			// new DefaultHttpMethodRetryHandler(3, false));
			try {
				int statusCode = httpClient.executeMethod(method);
				if (statusCode != HttpStatus.SC_OK) {
					System.out.println(statusCode + ": "
							+ method.getStatusLine());
				} else {
					System.out.println(new String(method.getResponseBody(),
							"GBK"));
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				method.releaseConnection();
			}
		}
	};

}
