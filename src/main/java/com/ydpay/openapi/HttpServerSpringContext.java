package com.ydpay.openapi;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class HttpServerSpringContext {

	static HttpServerSpringContext springContext = null;
	static ClassPathXmlApplicationContext applicationContext = null;
	static Log log = LogFactory.getLog(HttpServer.class);

	public static HttpServerSpringContext getContext() {
		if (springContext == null) {
			springContext = new HttpServerSpringContext();
		}
		return springContext;
	}

	public HttpServerSpringContext() {
		log.info("start init dubbo consumer.........");
		applicationContext = new ClassPathXmlApplicationContext(
				"applicationContext.xml");
		applicationContext.start();
		log.info("start dubbo finish and sucessful");
	}

	public Object getBean(String service) {
		Object obj = null;
		try {
			obj = applicationContext.getBean(service);
		} catch (Exception ex) {
			obj = null;
			log.info(ex.getMessage());
		}
		return obj;
	}
}
