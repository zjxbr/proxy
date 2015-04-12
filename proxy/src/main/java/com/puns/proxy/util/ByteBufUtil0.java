package com.puns.proxy.util;

import io.netty.buffer.ByteBuf;

public class ByteBufUtil0 {

	/**
	 * @function read bytebuf, from the reader index to writer index
	 * @param byteBuf
	 * @return
	 */
	public static String byteBufToString(ByteBuf byteBuf) {
		int writeIndex = byteBuf.writerIndex();
		int readIndex = byteBuf.readerIndex();
		byte[] request = new byte[writeIndex - readIndex];
		byteBuf.readBytes(request);
		byteBuf.resetReaderIndex();
		return new String(request);
	}
}
