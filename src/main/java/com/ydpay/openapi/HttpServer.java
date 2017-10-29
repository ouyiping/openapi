package com.ydpay.openapi;

import java.util.List;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ydpay.openapi.config.HttpServerConfig;
import com.ydpay.openapi.entity.ServerConfigEntity;

public final class HttpServer {

	static Log log = LogFactory.getLog(HttpServer.class);

	public static void main(String[] args) throws Exception {

		/* 初始化配置 */
		HttpServerConfig.getConfig().loadPropertiesFromSrc();
		HttpServerSpringContext.getContext();

		List<ServerConfigEntity> serverConfigEntities = HttpServerConfig
				.getConfig().getServerConfigEntityList();
		for (ServerConfigEntity row : serverConfigEntities) {
			new HttpServer().run(row.getPort(), row.isSsl(),
					row.isSupportKeepalive(), row.isNeedClientAuth());
		}

	}

	public void run(final int port, final boolean ssl,
			final boolean supportkeepalive,
			final boolean server_need_client_auth) throws Exception {
		new Thread(new Runnable() {
			public void run() {
				EventLoopGroup bossGroup = new NioEventLoopGroup(1);
				EventLoopGroup workerGroup = new NioEventLoopGroup();

				try {
					ServerBootstrap b = new ServerBootstrap();
					b.group(bossGroup, workerGroup)
							.channel(NioServerSocketChannel.class)
							.childHandler(
									new HttpServerInitializer(ssl,
											supportkeepalive,
											server_need_client_auth));

					Channel ch = b.bind(port).sync().channel();

					String httpstr = "";
					if (ssl) {
						httpstr = "https";
					} else {
						httpstr = "http";
					}
					log.info(httpstr + " server start sucessful bind at port "
							+ port + '.');
					log.info("Open your browser and navigate to " + httpstr
							+ "://localhost:" + port + '/');

					ch.closeFuture().sync();

				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					bossGroup.shutdownGracefully();
					workerGroup.shutdownGracefully();
				}
			}
		}).start();
	}

}
