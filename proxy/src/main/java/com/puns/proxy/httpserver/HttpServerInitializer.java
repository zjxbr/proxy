package com.puns.proxy.httpserver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

	public HttpServerInitializer() {
	}

	@Override
	public void initChannel(SocketChannel ch) {
		// ch.pipeline().addLast(new HttpServerCodec());
		ch.pipeline().addLast(
		// 不显示log，打印log需要在这里打开注释
				new HttpServerFrontendHandler());

	}
}