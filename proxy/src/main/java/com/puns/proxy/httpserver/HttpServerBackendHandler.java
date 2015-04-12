package com.puns.proxy.httpserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puns.proxy.httpserver.outerproxy.OuterProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxyByHeap;

public class HttpServerBackendHandler extends ChannelInboundHandlerAdapter {

	private static final Logger LOG = LoggerFactory
			.getLogger(HttpServerFrontendHandler.class);

	private final Channel inboundChannel;

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
		// mark this moment as channel active time
		channelActiveTime = System.currentTimeMillis();
		if (LOG.isDebugEnabled()) {
			LOG.debug("Active proxy channel :" + outerProxy.toString());
		}
		ctx.read();
		ctx.write(Unpooled.EMPTY_BUFFER);
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) {
		final ByteBuf byteBuf = (ByteBuf) msg;
		if (firstLine) {
			// this logic only for once
			firstLine = false;
			// get the returen code
			int indexCRLF = byteBuf.forEachByte(ByteBufProcessor.FIND_CRLF);
			if (indexCRLF > 0) {
				byte[] responseFirstLine = new byte[indexCRLF];
				byteBuf.readBytes(responseFirstLine);
				byteBuf.resetReaderIndex();
				String rtnLine = new String(responseFirstLine);

				// HTTP/1.1 200 OK
				String[] cells = rtnLine.split(" ");
				if (cells.length < 3) {
					// format wrong
					LOG.error("Response first line error : " + rtnLine);
					return;
				} else {
					if (!cells[1].equals("200")) {
						// return code wrong
						LOG.warn("Return code is not 200 : " + rtnLine);
					} else {
						LOG.info("Response first line : " + rtnLine);
					}
				}

				// write this first line to client
				inboundChannel.write(byteBuf.readBytes(indexCRLF));

				// write the proxy as response header to client
				String proxyPuns = "\nProxyPuns: " + this.outerProxy.getHost()
						+ "," + this.outerProxy.getPort();
				ByteBuf bf1 = ctx.alloc().heapBuffer(
						proxyPuns.getBytes().length);
				bf1.writeBytes(proxyPuns.getBytes());
				inboundChannel.write(bf1);

			}
		}
		// write the rest bytebuf to client
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
		if (LOG.isDebugEnabled()) {
			LOG.debug("outbound channel inactive");
			LOG.debug("Write to client complete , cost "
					+ (System.currentTimeMillis() - channelActiveTime) + "ms."
					+ outerProxy.getHost() + "," + outerProxy.getPort());
		}
		instance.releaseProxy(outerProxy);
		HttpServerFrontendHandler.closeOnFlush(inboundChannel);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		// if (cause instanceof java.io.IOException) {
		// System.out.println( + cause.getMessage());
		// }
		// cause.printStackTrace();
		LOG.error(ExceptionUtils.getStackTrace(cause));
		// !! close the frontend handler
		HttpServerFrontendHandler.closeOnFlush(ctx.channel());
	}
}