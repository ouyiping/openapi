package com.ydpay.openapi.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ydpay.openapi.entity.ServerConfigEntity;
import com.ydpay.openapi.util.HttpServerUtil;

/**
 * HttpServer的配置
 * 
 * @author
 * 
 */
public class HttpServerConfig {

	static Log log = LogFactory.getLog(HttpServerConfig.class);
	static HttpServerConfig config;
	private Properties properties;

	private List<ServerConfigEntity> serverConfigEntityList;
	private int count;

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public List<ServerConfigEntity> getServerConfigEntityList() {
		return serverConfigEntityList;
	}

	public void setServerConfigEntityList(
			List<ServerConfigEntity> serverConfigEntityList) {
		this.serverConfigEntityList = serverConfigEntityList;
	}

	public static void setConfig(HttpServerConfig config) {
		HttpServerConfig.config = config;
	}

	public static HttpServerConfig getConfig() {
		if (config == null) {
			config = new HttpServerConfig();
		}
		return config;
	}

	/**
	 * 加载配置文件
	 */
	public void loadPropertiesFromSrc() {
		InputStream in = null;
		try {
			log.info("begin load httpserver.properties");
			in = HttpServerConfig.class.getClassLoader().getResourceAsStream(
					"httpserver.properties");
			if (in != null) {
				this.properties = new Properties();
				try {
					this.properties.load(in);
				} catch (IOException e) {
					throw e;
				}
			}
			loadProperties(this.properties);
			log.info("load httpserver.properties finished");
		} catch (IOException e) {
			log.info("load httpserver.properties exception " + e.getMessage());

			if (in != null)
				try {
					in.close();
				} catch (IOException ie) {
					log.info("close IOException:" + ie.getMessage());
				}
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					log.info("close IOException:" + e.getMessage());
				}
		}
	}

	/**
	 * 匹配属性文件
	 * 
	 * @param pro
	 */
	public void loadProperties(Properties pro) {
		String value = "";

		value = pro.getProperty("httpserver.count");
		if (!HttpServerUtil.isEmpty(value)) {
			this.count = Integer.parseInt(value.trim());
		}

		boolean ssl = false;
		int port = 8080;
		boolean supportkeepalive = false;
		boolean SERVER_NEED_CLINET_AUTH = false;

		if (serverConfigEntityList == null)
			serverConfigEntityList = new ArrayList<ServerConfigEntity>();

		for (int index = 1; index <= count; index++) {
			String strIndex = "";
			if (index != 1)
				strIndex = "" + index;

			value = pro.getProperty("httpserver" + strIndex + ".ssl");
			if (!HttpServerUtil.isEmpty(value)) {
				ssl = Boolean.parseBoolean(value.trim());
			}
			
			value = pro.getProperty("httpserver" + strIndex + ".port");
			if(value == null)
				continue;
			if (!HttpServerUtil.isEmpty(value)) {
				port = Integer.parseInt(value.trim());
			}

			value = pro.getProperty("httpserver" + strIndex
					+ ".supportkeepalive");
			if (!HttpServerUtil.isEmpty(value)) {
				supportkeepalive = Boolean.parseBoolean(value.trim());
			}

			value = pro.getProperty("httpserver" + strIndex
					+ ".SERVER_NEED_CLINET_AUTH");
			if (!HttpServerUtil.isEmpty(value)) {
				SERVER_NEED_CLINET_AUTH = Boolean.parseBoolean(value.trim());
			}

			serverConfigEntityList.add(new ServerConfigEntity(port, ssl,
					supportkeepalive, SERVER_NEED_CLINET_AUTH));
		}

	}
}
