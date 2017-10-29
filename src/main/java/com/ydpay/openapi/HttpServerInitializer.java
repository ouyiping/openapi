package com.ydpay.openapi;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import javax.net.ssl.SSLEngine;

public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

	private boolean ssl;
	private boolean supportkeepalive;
	private boolean server_need_client_auth;
	
	public HttpServerInitializer(boolean ssl, boolean supportkeepalive, boolean server_need_client_auth){
		super();
		this.ssl = ssl;
		this.supportkeepalive = supportkeepalive;
		this.server_need_client_auth = server_need_client_auth;
	}
	
	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		// Create a default pipeline implementation.
		ChannelPipeline pipeline = ch.pipeline();

		if (ssl) {
			SSLEngine engine = SecureChatSslContextFactory.getServerContext()
					.createSSLEngine();
			engine.setNeedClientAuth(server_need_client_auth);
			engine.setUseClientMode(false);
			engine.setWantClientAuth(false);
			engine.setEnabledProtocols(new String[] { "SSLv2Hello", "SSLv3", "TLSv1",
					"TLSv1.1", "TLSv1.2" });
			pipeline.addLast("ssl", new SslHandler(engine));
		}

		// 40秒没有数据读入，发生超时机制
		pipeline.addLast(new ReadTimeoutHandler(40));

		// 40秒没有输入写入，发生超时机制
		pipeline.addLast(new WriteTimeoutHandler(40));

		/**
		 * http-request解码器 http服务器端对request解码
		 */
		pipeline.addLast("decoder", new HttpRequestDecoder());

		/**
		 * http-response解码器 http服务器端对response编码
		 */
		pipeline.addLast("encoder", new HttpResponseEncoder());

		/**
		 * HttpObjectAggregator会把多个消息转换为一个单一的FullHttpRequest或是FullHttpResponse。
		 */
		// 1048576
		// 1024*1024*64
		pipeline.addLast("aggregator",
				new HttpObjectAggregator(1024 * 1024 * 8));

		/**
		 * 压缩 Compresses an HttpMessage and an HttpContent in gzip or deflate
		 * encoding while respecting the "Accept-Encoding" header. If there is
		 * no matching encoding, no compression is done.
		 */
		pipeline.addLast("deflater", new HttpContentCompressor());

		pipeline.addLast("handler", new HttpServerHandler(supportkeepalive));
	}

}
