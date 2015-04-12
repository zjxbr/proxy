package com.puns.proxy.httpserver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

	public HttpServerInitializer() {
	}

	@Override
	public void initChannel(SocketChannel ch) {
		ch.pipeline().addLast(new HttpServerFrontendHandler());

	}
}