package com.puns.proxy.httpserver;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puns.proxy.httpserver.outerproxy.OuterProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxyByHeap;
import com.puns.proxy.util.ByteBufUtil0;
import com.puns.proxy.util.URLUtils;

/**
 * @author zjx
 */
public class HttpServerFrontendHandler extends ChannelInboundHandlerAdapter {

	private static final int PROXY_CONNECT_TIMEOUT = 4000;

	private static final Logger LOG = LoggerFactory
			.getLogger(HttpServerFrontendHandler.class);

	private static final String HTTP_987_OVER_MAXCONN = "HTTP/1.1 987 OVERMAXCONN\r\n";

	private static final String HTTP_988_PROXYMAXTRY = "HTTP/1.1 988 PROXYMAXTRY\r\n";

	private static AtomicInteger channelInWorkingCnt = new AtomicInteger();
	private static final int maxActive = 100;

	private static final int PROXY_MAXRETRY_CNT;

	private static final ProvideProxy provideProxy = ProvideProxyByHeap
			.getInstance();

	private volatile Channel outboundChannel;

	static {
		// init resource
		ClassLoader.getSystemResourceAsStream("property.properties");
		Properties properties = new Properties();
		try {
			properties.load(ClassLoader
					.getSystemResourceAsStream("property.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		String proxyMaxRetrCnt = properties.getProperty("proxy.maxretry.cnt");
		PROXY_MAXRETRY_CNT = Integer.valueOf(proxyMaxRetrCnt.trim());
	}

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {

		// if over the max connection, return 987 code.
		// this is design for spider.
		if (channelInWorkingCnt.get() >= maxActive) {

			LOG.warn("Too many connections.");

			ByteBuf byteBuf = ctx.alloc().heapBuffer(
					HTTP_987_OVER_MAXCONN.getBytes().length);
			byteBuf.writeBytes(HTTP_987_OVER_MAXCONN.getBytes());

			ctx.channel().writeAndFlush(byteBuf);
			ctx.channel().close();
		} else {
			channelInWorkingCnt.incrementAndGet();
			ctx.channel().read();
		}

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		// active http request --
		channelInWorkingCnt.decrementAndGet();
		if (outboundChannel != null) {
			closeOnFlush(outboundChannel);
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("inbound channel inactive");
		}
	}

	/**
	 * Closes the specified channel after alexl queued write requests are
	 * flushed.
	 */
	static void closeOnFlush(Channel ch) {
		if (ch.isActive()) {
			ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
					ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, final Object msg)
			throws Exception {
		final ByteBuf byteBuf = (ByteBuf) msg;
		if (LOG.isDebugEnabled()) {
			// print the http request
			LOG.debug(ByteBufUtil0.byteBufToString(byteBuf));
		}

		// if the outboundchannel is active,
		// continueours write to the outboundchannel.
		if (outboundChannel != null && outboundChannel.isActive()) {
			outboundChannel.writeAndFlush(msg);
			return;
		}

		// get the domain
		byteBuf.markReaderIndex();
		int indexCRLF = byteBuf.forEachByte(ByteBufProcessor.FIND_CRLF);
		byte[] requestURLBytes = new byte[indexCRLF];
		byteBuf.readBytes(requestURLBytes);
		byteBuf.resetReaderIndex();
		String firstLine = new String(requestURLBytes);
		String domain = URLUtils.getDomain(firstLine);
		LOG.info("request to: " + firstLine);

		tryToConnectProxy(ctx, byteBuf, domain, PROXY_MAXRETRY_CNT);

	}

	/**
	 * @param ctx
	 *            inboundChannel ctx
	 * @param request
	 * @param domain
	 * @param retryCntRemain
	 */
	private void tryToConnectProxy(ChannelHandlerContext ctx, ByteBuf request,
			String domain, int retryCntRemain) {

		// if over max retry count
		// report fail and return
		if (retryCntRemain-- < 0) {
			LOG.warn("Try to connect to proxy over times!");
			request.clear();
			request.writeBytes(HTTP_988_PROXYMAXTRY.getBytes());
			ctx.writeAndFlush(request).addListener(ChannelFutureListener.CLOSE);
			return;
		}

		// get the proxy
		final OuterProxy outerProxy = provideProxy.getProxy(domain);
		// get proxy failed, then retry
		if (outerProxy == null) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Get proxy fail , remain retry count :"
						+ retryCntRemain);
			}
			tryToConnectProxy(ctx, request, domain, retryCntRemain);
			return;
		}
		final Bootstrap b = new Bootstrap();
		final Channel inboundChannel = ctx.channel();

		b.group(inboundChannel.eventLoop())
				.channel(inboundChannel.getClass())
				.handler(
						new HttpServerBackendHandler(inboundChannel, outerProxy))
				.option(ChannelOption.AUTO_READ, true)
				// set the connection time out MILLIS
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
						PROXY_CONNECT_TIMEOUT);

		final ChannelFuture f = b.connect(outerProxy.getHost(),
				outerProxy.getPort());

		outboundChannel = f.channel();
		f.addListener(new ConnectListener(ctx, outboundChannel, request, this,
				outerProxy, domain, retryCntRemain));

	}

	/**
	 * @author zjx
	 * @function if connection to proxy failed, there will be a retry move.
	 */
	public class ConnectListener implements ChannelFutureListener {
		private final ChannelHandlerContext ctx;
		private final Channel connectedChannel;
		private final ByteBuf request;
		private final HttpServerFrontendHandler httpServerFrontendHandler;
		private final OuterProxy outerProxy;
		private final String domain;
		private final int retryCntRemain;

		public ConnectListener(ChannelHandlerContext ctx,
				Channel connectedChannel, ByteBuf request,
				HttpServerFrontendHandler httpServerFrontendHandler,
				OuterProxy outerProxy, String domain, int retryCntRemain) {
			this.ctx = ctx;
			this.connectedChannel = connectedChannel;
			this.request = request;
			this.httpServerFrontendHandler = httpServerFrontendHandler;
			this.outerProxy = outerProxy;
			this.domain = domain;
			this.retryCntRemain = retryCntRemain;
		}

		@Override
		public void operationComplete(ChannelFuture future) {
			if (future.isSuccess()) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Connected to the proxy succuss : "
							+ outerProxy.toString());
				}
				// write to the outbound
				connectedChannel.writeAndFlush(request).addListener(
						new ChannelFutureListener() {
							@Override
							public void operationComplete(ChannelFuture future) {
								if (future.isSuccess()) {
									if (LOG.isDebugEnabled()) {
										LOG.debug("Write to the proxy success! "
												+ outerProxy.toString());
									}
									// inbound channel continue reading.
									ctx.channel().read();
								} else {
									LOG.error("Write to the proxy failed :"
											+ outerProxy.toString());
									// if write to proxy failed, do not retry.
									// just close the channel.
									// I think there should be more task to do.
									future.channel().close();
								}
							}
						});
			} else {
				// If connect to the proxy fail, should close the channel, or
				// there will be exception rise.
				// I haven't know the reason yet.
				LOG.warn("Connected to the proxy failed : "
						+ outerProxy.toString());
				connectedChannel.close();
				// retry
				httpServerFrontendHandler.tryToConnectProxy(ctx, request,
						domain, retryCntRemain);
			}
		}
	}

}