package com.puns.proxy.httpserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puns.proxy.httpserver.outerproxy.OuterProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxyByHeap;

/**
 * @author zjx
 *
 */
public class HttpServer {

	private static final Logger LOG = LoggerFactory.getLogger(HttpServer.class);

	private static final int LOCAL_PORT = 3128;

	public static void main(String[] args) throws Exception {

		// need proxy file path
		if (args.length > 1) {
			System.out.println("useage : <proxyFiles>");
			return;
		}

		BufferedReader proxyReader = null;
		try {
			if (args.length == 1) {
				// use located proxy file
				proxyReader = new BufferedReader(new FileReader(args[0].trim()));
			} else {
				// use default proxy file
				proxyReader = new BufferedReader(new InputStreamReader(
						ClassLoader.getSystemResourceAsStream("proxys100.txt")));
			}
			// init proxy
			String line;
			String[] cells;
			ProvideProxy provideProxy = ProvideProxyByHeap.getInstance();
			while ((line = proxyReader.readLine()) != null) {
				line = line.trim();
				if (!line.isEmpty()) {
					cells = line.split(" ");
					LOG.debug(cells[0] + "," + cells[1]);
					// init proxy
					OuterProxy outerProxy = new OuterProxy(cells[0],
							Integer.valueOf(cells[1]));

					provideProxy.initProxy(outerProxy);
				}
			}

		} finally {
			IOUtils.closeQuietly(proxyReader);
		}

		// Configure the bootstrap.
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup(10);
		try {
			ServerBootstrap b = new ServerBootstrap();
			LOG.info("Proxy started!!!");
			b.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					// no log
					// .handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new HttpServerInitializer())
					.childOption(ChannelOption.AUTO_READ, false)
					.bind(LOCAL_PORT).sync().channel().closeFuture().sync();

		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
			LOG.info("Proxy ready to shut down!!!");
		}
	}
}
