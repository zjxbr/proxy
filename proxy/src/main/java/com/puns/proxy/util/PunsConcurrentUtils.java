package com.puns.proxy.util;

import java.util.concurrent.ConcurrentHashMap;

public class PunsConcurrentUtils {

	/**
	 * @function put to the concurrentHashMap, if exist , return self
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
