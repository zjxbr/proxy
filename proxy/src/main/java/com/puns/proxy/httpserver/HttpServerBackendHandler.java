package com.puns.proxy.httpserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puns.proxy.httpserver.outerproxy.OuterProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxyByHeap;

public class HttpServerBackendHandler extends ChannelInboundHandlerAdapter {

	private static final Logger LOG = LoggerFactory
			.getLogger(HttpServerFrontendHandler.class);

	private final Channel inboundChannel;
	// channel激活时间
	private long channelActiveTime;

	private boolean firstLine = true;

	private OuterProxy outerProxy;

	private static final ProvideProxyByHeap instance = ProvideProxyByHeap
			.getInstance();

	public HttpServerBackendHandler(Channel inboundChannel,
			OuterProxy outerProxy) {
		this.inboundChannel = inboundChannel;
		this.outerProxy = outerProxy;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		channelActiveTime = System.currentTimeMillis();

		LOG.info("回写信息通道激活:" + outerProxy.getHost() + ","
				+ outerProxy.getPort());
		ctx.read();
		ctx.write(Unpooled.EMPTY_BUFFER);
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) {
		final ByteBuf byteBuf = (ByteBuf) msg;
		if (firstLine) {

			// 获取第一行
			int indexCRLF = byteBuf.forEachByte(ByteBufProcessor.FIND_CRLF);
			if (indexCRLF > 0) {
				byte[] responseFirstLine = new byte[indexCRLF];
				byteBuf.readBytes(responseFirstLine);
				byteBuf.resetReaderIndex();
				String rtnLine = new String(responseFirstLine);
				// HTTP/1.1 200 OK
				// 1.HTTP/1.1
				// 2.返回code
				// 3.OK 信息
				String[] cells = rtnLine.split(" ");
				System.out.println("返回值:" + rtnLine);
				if (cells.length < 3) {
					System.err.println("error，返回值小于3列");
					return;
				} else {
					if (!cells[1].equals("200")) {
						System.err.println("返回值不为200");
					} else {
						// OK
					}
				}

				// 设置以后读取不是第一行
				firstLine = false;

				inboundChannel.write(byteBuf.readBytes(indexCRLF));
				
				// // 设置代理header
				// 代理写入进去
				String proxyPuns = "\nProxyPuns: " + this.outerProxy.getHost()
						+ "," + this.outerProxy.getPort();
				// 分配堆内存，不要分配直接内存，容易内存溢出
				ByteBuf bf1 = ctx.alloc().heapBuffer(
						proxyPuns.getBytes().length);
				bf1.writeBytes(proxyPuns.getBytes());
				inboundChannel.write(bf1);

			}
		}
		inboundChannel.writeAndFlush(msg).addListener(
				new ChannelFutureListener() {
					@Override
					public void operationComplete(ChannelFuture future) {
						if (future.isSuccess()) {
							ctx.channel().read();
						} else {
							future.channel().close();
						}
					}
				});

	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		System.err.println("outbound channel inactive");
		// 释放代理
		instance.releaseProxy(outerProxy);
		long endReadTime = System.currentTimeMillis();
		LOG.info("完成写入到客户端，耗时:" + (endReadTime - channelActiveTime) + "ms."
				+ outerProxy.getHost() + "," + outerProxy.getPort());
		HttpServerFrontendHandler.closeOnFlush(inboundChannel);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		// 如果是IOException，判断是什么原因
		if(cause instanceof java.io.IOException){
			// 认为代理出错
			// TODO
			System.out.println("发生了" + cause.getMessage());
		}
		System.out.println("发生了Backend异常");
		cause.printStackTrace();
		HttpServerFrontendHandler.closeOnFlush(ctx.channel());
	}
}