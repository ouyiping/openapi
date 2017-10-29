package com.ydpay.openapi.entity;

public class ServerConfigEntity {
	private int port;
	private boolean ssl;
	private boolean supportKeepalive;
	private boolean needClientAuth;

	
	public ServerConfigEntity(int port, boolean ssl, boolean supportKeepalive, boolean needClientAuth){
		this.port = port;
		this.ssl = ssl;
		this.supportKeepalive = supportKeepalive;
		this.needClientAuth = needClientAuth;
	}
	
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isSsl() {
		return ssl;
	}

	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public boolean isSupportKeepalive() {
		return supportKeepalive;
	}

	public void setSupportKeepalive(boolean supportKeepalive) {
		this.supportKeepalive = supportKeepalive;
	}

	public boolean isNeedClientAuth() {
		return needClientAuth;
	}

	public void setNeedClientAuth(boolean needClientAuth) {
		this.needClientAuth = needClientAuth;
	}

}
