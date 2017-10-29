package com.ydpay.openapi.enums;

public enum RedirectFlag {
	NO(0, "不重定向"), YES(1, "重定向");

	private final int code;
	private final String name;

	private RedirectFlag(int code, String name) {
		this.code = code;
		this.name = name;
	}

	public int getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public static RedirectFlag valueOfCode(int code) {
		for (RedirectFlag type : RedirectFlag.values()) {
			if (type.getCode() == code) {
				return type;
			}
		}
		throw new IllegalStateException("enums.type.invalidcode#" + code
				+ "#" + RedirectFlag.class.getName());
	}
}
