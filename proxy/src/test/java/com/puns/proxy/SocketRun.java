package com.puns.proxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */
public class SocketRun {
	public static void main(String[] args) throws InterruptedException {

		List<String> urList = new ArrayList<>();
		urList.add("http://www.amazon.co.jp/");
		// urList.add("http://kakaku.com/");
		// urList.add("http://tabelog.com/tokyo/");
		// urList.add("http://photohito.com/");

		for (int i = 0; i < urList.size(); i++) {

			Thread thread = new Thread(new SocketRunable(urList.get(i)));
			thread.start();
		}
	}

	public static class SocketRunable implements Runnable {
		final private String url;

		public SocketRunable(String url) {
			this.url = url;
		}

		@Override
		public void run() {
			System.out.println(url);
			Socket socket;
			try {
				socket = new Socket("localhost", 3128);
//				socket = new Socket("localhost", 3128);
				socket.getOutputStream()
						.write("GET http://www.amazon.co.jp/gp/aag/details/ref=aag_m_ss?ie=UTF8&seller=A001665895YWGQJVAG98 HTTP/1.1\n"
								.getBytes());
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(socket.getInputStream()));
				String str;
				while ((str = reader.readLine()) != null) {
					System.out.println(str);
				}
				socket.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}

		}
	}

}
