package com.ydpay.openapi.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.alibaba.dubbo.common.utils.StringUtils;

public class HttpServerUtil {

	public static boolean isEmpty(String s) {
		return (s == null) || ("".equals(s.trim()));
	}

	public static String getServiceName(String method) {
		String result = "";

		String[] strs = method.split("\\.");
		if (strs.length > 2) {
			result = strs[1];
			result = result + "Service";
		}

		return result;
	}

	public static String getMethod(String[] urlsplit) throws Exception {
		String result = "";

		result = "ydpay.";
		result = result + StringUtils.join(urlsplit, ".");

		return result;
	}

	public static Object invokeMethodGernaral(Object owner, String methodName,
			Object[] args) {
		// a.先获取对象所属的类
		Class<? extends Object> ownerClass = owner.getClass();
		Method method = null;
		Object result = null;
		// b.获取需要调用的方法
		for (Method m : ownerClass.getDeclaredMethods()) {
			if (m.getName().equalsIgnoreCase(methodName)) {
				method = m;
				break;
			}
		}
		try {
			// c.调用该方法
			result = method.invoke(owner, args);// 调用方法
		} catch (IllegalAccessException e) {
		} catch (IllegalArgumentException e) {
		} catch (InvocationTargetException e) {
		}
		return result;
	}
}
