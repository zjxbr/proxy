package com.puns.proxy.util;

public class URLUtils {

	/**
	 * @function 获取域名
	 * @param urlOrSomething
	 * @return
	 */
	public static String getDomain(String urlOrSomething) {
		int fromIndex = urlOrSomething.indexOf("//");
		if (fromIndex > 0) {
			fromIndex = fromIndex + 2;
			int endIndex = urlOrSomething.indexOf("/", fromIndex);
			if (endIndex > 0) {
				return urlOrSomething.substring(fromIndex, endIndex);
			} else {
				return urlOrSomething.substring(fromIndex);
			}
		} else {
			return null;
		}
	}
}
