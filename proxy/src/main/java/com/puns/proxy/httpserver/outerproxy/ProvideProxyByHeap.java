package com.puns.proxy.httpserver.outerproxy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zjx
 * @function 用java内存来存储管理proxy
 * @howtouse 单例模式
 */
public class ProvideProxyByHeap implements ProvideProxy {

	private static ProvideProxyByHeap instance;

	private static final Object[] initLocker = new Object[0];

	// 总proxy 列表，持有所有proxy引用
	private List<OuterProxy> outerProxies = new ArrayList<>();

	// key是每个域名，integer是该域名访问到第几个proxy
	private ConcurrentHashMap<String, AtomicInteger> domainProxyPos = new ConcurrentHashMap<>();

	public static ProvideProxyByHeap getInstance() {
		if (instance == null) {
			synchronized (initLocker) {
				if (instance == null) {
					// 初始化
					instance = new ProvideProxyByHeap();
				}
			}
		}
		return instance;
	}

	private void init() {

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader("/tmp/proxys100.txt"));

			String line;
			String[] cells;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (!line.isEmpty()) {
					cells = line.split(" ");
					System.out.println(cells[0] + "," + cells[1]);
					// 初始化 outerProxies
					OuterProxy outerProxy = new OuterProxy(cells[0],
							Integer.valueOf(cells[1]));

					outerProxies.add(outerProxy);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private ProvideProxyByHeap() {
		init();
	}

	@Override
	public OuterProxy getProxy(String domain) {
		// 获取代理，走10次
		return getProxy(domain, 10);
	}

	/**
	 * @function 获取代理
	 * @param domain
	 *            域名
	 * @param tryCnt
	 *            重试次数
	 * @return
	 */
	private OuterProxy getProxy(String domain, int tryCnt) {
		// 0.如果重试次数为0,则返回。
		if (tryCnt == 0) {
			return null;
		}

		// 1.获取轮寻到哪个position
		int pos = getPos(domain);

		// 2.判断代理是否可用,不可用,则返回空，继续调用getProxy
		OuterProxy proxyBean = outerProxies.get(pos).getProxyBean(domain);
		if (proxyBean == null) {
			return getProxy(domain, --tryCnt);
		} else {
			return proxyBean;
		}
	}

	/**
	 * @function 获取该域名的position
	 * @param domain
	 * @return
	 */
	private int getPos(String domain) {
		// 1.取出域中，目前论寻到第几个代理了
		// 然后获取该代理，并且获取值+1返回。
		// 利用concurrent hashmap 的putIfAbsent，保证put操作的原子性：如果不存在则put，存在返回存在的值
		AtomicInteger atomicInteger = domainProxyPos.get(domain);
		if (atomicInteger == null) {
			atomicInteger = new AtomicInteger(0);
			AtomicInteger hasBeenPut = domainProxyPos.putIfAbsent(domain,
					atomicInteger);
			if (hasBeenPut != null) {
				// 如果不为空,则代表已经存在元素，所以需要再set一下
				atomicInteger = hasBeenPut;
			}
		}
		// 利用******自旋锁******保证原子性,减少同步代码块
		int oldPos;
		int newPos;
		while (true) {
			oldPos = atomicInteger.get();
			newPos = oldPos + 1;
			if (newPos >= outerProxies.size()) {
				newPos = 0;
			}
			if (atomicInteger.compareAndSet(oldPos, newPos)) {
				// 设置成功
				break;
			}
		}
		return newPos;
	}

	@Override
	public void releaseProxy(OuterProxy outerProxy) {
		outerProxy.releaseProxy();
	}

}
