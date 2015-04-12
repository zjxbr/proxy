package com.puns.proxy.httpserver.outerproxy;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puns.proxy.util.PunsConcurrentUtils;

public class OuterProxy {
	private static final Logger LOG = LoggerFactory.getLogger(OuterProxy.class);
	// proxy信息
	private String host;
	// 端口
	private int port;
	// 外部引用
	private AtomicInteger reference = new AtomicInteger(0);
	// 域名访问信息
	private ConcurrentHashMap<String, DomainInfo> domainInfoMap = new ConcurrentHashMap<>();

	public OuterProxy(String host, int port) {
		super();
		this.host = host;
		this.port = port;
	}

	public class DomainInfo {
		// 平均访问时间
		private int avgVisitTime;
		// 上次访问时间
		private long lastVisitTime;
		// 上次完成时间
		private long lastFinishTime;
		// 访问成功与否记录（记录10次内）
		private Queue<Boolean> queue = new LinkedList<Boolean>();

		public DomainInfo() {
			super();
			this.avgVisitTime = 0;
			this.lastVisitTime = 0;
			this.lastFinishTime = 0;
		}

		public int getAvgVisitTime() {
			return avgVisitTime;
		}

		public long getLastVisitTime() {
			return lastVisitTime;
		}

		public long getLastFinishTime() {
			return lastFinishTime;
		}

		public Queue<Boolean> getQueue() {
			return queue;
		}

		public void setAvgVisitTime(int avgVisitTime) {
			this.avgVisitTime = avgVisitTime;
		}

		public void setLastVisitTime(long lastVisitTime) {
			this.lastVisitTime = lastVisitTime;
		}

		public void setLastFinishTime(long lastFinishTime) {
			this.lastFinishTime = lastFinishTime;
		}

	}

	/**
	 * @step1 判断代理是否可用
	 * @step2 获取该代理引用，则计数器+1
	 * @step3
	 * @return
	 */
	public OuterProxy getProxyBean(String domain) {
		// 1.判断代理可用与否
		if (!canUseProxy(domain)) {
			return null;
		}

		// 2.获取该代理引用，计数器加1
		reference.incrementAndGet();

		return this;
	}

	private boolean canUseProxy(final String domain) {
		// 1.代理引用超过3,则失败
		if (reference.get() > 3) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("该代理同时访问次数已经超过3个，无法获取该代理:" + host + "," + port);
			}
			return false;
		}
		// 2.1秒内，该代理不能处理相同域名
		DomainInfo domainInfo = domainInfoMap.get(domain);
		if (domainInfo == null) {
			domainInfo = PunsConcurrentUtils.putAndGetValueFromHashMap(
					domainInfoMap, domain, new DomainInfo());
		}
		long currentVisitTime = System.currentTimeMillis();
		if (currentVisitTime - domainInfo.getLastVisitTime() < 1000) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("1秒内，不能多次用该代理访问域名:" + host + "," + port);
			}
			return false;
		}
		// 成功
		// 1.设置时间为当前访问时间
		domainInfo.setLastVisitTime(System.currentTimeMillis());
		return true;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	/**
	 * @function 释放
	 */
	protected void releaseProxy() {
		reference.decrementAndGet();
	}

}
