package com.ydpay.openapi;

import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public final class SecureChatSslContextFactory {

	private static final String PROTOCOL = "SSL";
	private static final SSLContext SERVER_CONTEXT;
	private static String SERVER_KEY_STORE = "api.chehuotong.com.net.pfx";
	private static String SERVER_KEY_STORE_PASSWORD = "214281613170257";


	static {

		SSLContext serverContext;
		try {

			KeyStore ks = KeyStore.getInstance("pkcs12");
			ks.load(SecureChatSslContextFactory.class.getClassLoader()
					.getResourceAsStream(SERVER_KEY_STORE),
					SERVER_KEY_STORE_PASSWORD.toCharArray());

			KeyManagerFactory kmf = KeyManagerFactory
					.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, SERVER_KEY_STORE_PASSWORD.toCharArray());

			serverContext = SSLContext.getInstance(PROTOCOL);
			serverContext.init(kmf.getKeyManagers(), null, null);

		} catch (Exception e) {
			throw new Error("Failed to initialize the server-side SSLContext",
					e);
		}

		SERVER_CONTEXT = serverContext;
	}

	public static SSLContext getServerContext() {
		return SERVER_CONTEXT;
	}

	private SecureChatSslContextFactory() {
		// Unused
	}
}
