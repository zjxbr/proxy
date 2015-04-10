package com.puns.proxy.util;

import java.util.concurrent.ConcurrentHashMap;

public class PunsConcurrentUtils {

	/**
	 * @function put到同步map，如果map中已经存在，则不put，并且返回已经存在的值，如果不存在，则put，返回自己
	 * @param concurrentHashMap
	 * @param k
	 * @param v
	 * @return
	 */
	public static <K, V> V putAndGetValueFromHashMap(
			ConcurrentHashMap<K, V> concurrentHashMap, K k, V v) {
		V exist = concurrentHashMap.putIfAbsent(k, v);
		if (exist != null) {
			return exist;
		} else {
			return v;
		}
	}
}
