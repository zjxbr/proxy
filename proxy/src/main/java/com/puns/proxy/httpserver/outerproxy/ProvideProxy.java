package com.puns.proxy.httpserver.outerproxy;


/**
 * @author zjx
 */
public interface ProvideProxy {

	/**
	 * @function 根据域名返回代理
	 * @param domain
	 *            域名
	 * @param tryCnt
	 *            尝试次数
	 * @return
	 */
	public OuterProxy getProxy(String domain);

	public void releaseProxy(OuterProxy outerProxy);

}
