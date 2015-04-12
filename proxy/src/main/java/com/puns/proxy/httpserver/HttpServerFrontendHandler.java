package com.puns.proxy.httpserver;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puns.proxy.httpserver.outerproxy.OuterProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxy;
import com.puns.proxy.httpserver.outerproxy.ProvideProxyByHeap;
import com.puns.proxy.util.ByteBufUtil0;
import com.puns.proxy.util.URLUtils;

/**
 * @author zjx
 * @warning 继承自SimpleChannelInboundHandler，不能覆盖channelread方法。必须卸载channelread0中
 */
public class HttpServerFrontendHandler extends ChannelInboundHandlerAdapter {

	private static final Logger LOG = LoggerFactory
			.getLogger(HttpServerFrontendHandler.class);

	private static final String HTTP_987_OVER_MAXCONN = "HTTP/1.1 987 OVERMAXCONN\r\n";

	private static final String HTTP_988_PROXYMAXTRY = "HTTP/1.1 988 PROXYMAXTRY\r\n";

	private static volatile int channelInWorkingCnt = 0;
	private static final int maxActive = 100;

	private static final int GET_PROXY_MAX_TRY_CNT = 5;

	private static final ProvideProxy provideProxy = ProvideProxyByHeap
			.getInstance();

	private volatile Channel outboundChannel;

	// 连接数超过限制，则直接回写msg
	// 否则去做channel read 处理
	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {

		// System.out.println("Thread cnt : " + Thread.activeCount());
		// 判断在工作的channel是否已经超过限制了，如果超过了，就返回LOCKED并且closechannel
		if (channelInWorkingCnt >= maxActive) {

			LOG.warn("too many connections.");

			ByteBuf byteBuf = ctx.alloc().heapBuffer(
					HTTP_987_OVER_MAXCONN.getBytes().length);
			byteBuf.writeBytes(HTTP_987_OVER_MAXCONN.getBytes());

			ctx.channel().writeAndFlush(byteBuf);
			ctx.channel().close();
		} else {
			channelInWorkingCnt++;
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
		if (outboundChannel != null) {
			closeOnFlush(outboundChannel);
		}
		System.out.println("inbound channel inactive");
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
		// if the outboundchannel is active,
		// continueours write to the outboundchannel.
		if (outboundChannel != null && outboundChannel.isActive()) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(ByteBufUtil0.byteBufToString(byteBuf));
			}
			outboundChannel.writeAndFlush(msg);
			return;
		}

		byteBuf.markReaderIndex();
		int indexCRLF = byteBuf.forEachByte(ByteBufProcessor.FIND_CRLF);
		byte[] requestURLBytes = new byte[indexCRLF];
		byteBuf.readBytes(requestURLBytes);
		byteBuf.resetReaderIndex();
		String firstLine = new String(requestURLBytes);
		String domain = URLUtils.getDomain(firstLine);
		LOG.info("request  : " + firstLine);

		if (LOG.isDebugEnabled()) {
			// print the http request
			LOG.debug(ByteBufUtil0.byteBufToString(byteBuf));
		}

		tryToConnectProxy(ctx, byteBuf, domain, 3);

		channelInWorkingCnt--;
	}

	/**
	 * @function 试图连接代理服务器
	 * @param inboundChannel
	 * @param request
	 * @param domain
	 *            试图连接的域名
	 * @param retryCntRemain
	 *            重试次数
	 */
	private void tryToConnectProxy(ChannelHandlerContext ctx, ByteBuf request,
			String domain, int retryCntRemain) {

		// retry次数到达，则返回
		if (retryCntRemain-- <= 0) {
			LOG.warn("重试次数到达上限，连接到代理失败.");
			// 做失败处理
			// 回写特殊return code
			// 把request clear，然后写入内容
			request.clear();

			request.writeBytes(HTTP_988_PROXYMAXTRY.getBytes());
			ctx.writeAndFlush(request).addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future)
						throws Exception {
					if (LOG.isDebugEnabled()) {
						LOG.debug("重试次数到达上限，连接到代理失败,写入回client: "
								+ future.isSuccess());
					}
					future.channel().close();
				}
			});
			return;
		}
		// 获得代理
		OuterProxy outerProxy = provideProxy.getProxy(domain);
		// 如果获取代理失败，则返回
		if (outerProxy == null) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("获取代理失败,剩余重试次数:" + retryCntRemain);
			}
			tryToConnectProxy(ctx, request, domain, retryCntRemain);
			return;
		}
		final Bootstrap b = new Bootstrap();
		final Channel inboundChannel = ctx.channel();

		b.group(inboundChannel.eventLoop())
				// 不显示log，打印log需要在这里打开注释
				// .handler(new LoggingHandler(LogLevel.INFO))
				.channel(inboundChannel.getClass())
				// http 解压缩的handler
				.handler(
						(new HttpServerBackendHandler(inboundChannel,
								outerProxy)))
				.option(ChannelOption.AUTO_READ, true)
				// 设置connect time out 为4s
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000);

		// System.out.println("connecting to " + proxyBean);
		ChannelFuture f = b.connect(outerProxy.getHost(), outerProxy.getPort());

		outboundChannel = f.channel();
		f.addListener(new ConnectListener(ctx, outboundChannel, request, this,
				outerProxy, domain, retryCntRemain));

	}

	public class ConnectListener implements ChannelFutureListener {
		// private final Channel fromChannel;
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
			// this.fromChannel = ctx.channel();
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
			// System.out.println("连接到代理:" + future.isSuccess());
			if (future.isSuccess()) {
				// 连接成功，设置为true
				// 连接到代理成功，设置保存连接时间
				LOG.info("连接到代理成功:" + outerProxy.getHost() + ","
						+ outerProxy.getPort());
				connectedChannel.writeAndFlush(request).addListener(
						new ChannelFutureListener() {
							@Override
							public void operationComplete(ChannelFuture future) {
								if (future.isSuccess()) {
									// was able to flush out data, start to
									// read the
									// next chunk
									LOG.info("请求写入到代理成功:"
											+ outerProxy.getHost() + ","
											+ outerProxy.getPort());
									ctx.channel().read();
								} else {
									LOG.error("！！！请求写入到代理失败！！！:"
											+ outerProxy.getHost() + ","
											+ outerProxy.getPort());
									future.channel().close();
								}
							}
						});
			} else {
				// 连接不成功，一定要关掉连接的channel，否则会发生异常。
				// 异常是，connectd channel 绑定到了bootstrap，
				// 然而boot strap 要去连接其他channel，
				// 这样就会造成channel promise 失效，具体原因不太明白。
				LOG.warn("连接到代理失败:" + outerProxy.getHost() + ","
						+ outerProxy.getPort());
				connectedChannel.close();
				// System.out.println("连接失败，重新链接");
				httpServerFrontendHandler.tryToConnectProxy(ctx, request,
						domain, retryCntRemain);
			}
		}
	}

}