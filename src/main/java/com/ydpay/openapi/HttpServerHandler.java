package com.ydpay.openapi;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ydpay.openapi.entity.RetResponse;
import com.ydpay.openapi.enums.RedirectFlag;
import com.ydpay.openapi.util.Base64Method;
import com.ydpay.openapi.util.HttpServerUtil;

public class HttpServerHandler extends SimpleChannelInboundHandler<Object> {

	static Log log = LogFactory.getLog(HttpServerHandler.class);
	Gson gson = new Gson();

	private static final HttpDataFactory factory = new DefaultHttpDataFactory(
			DefaultHttpDataFactory.MINSIZE); // Disk
	private HttpPostRequestDecoder decoder;

	private static final String routeMethod = "ydpay.gateway.com.gateway.router";

	private static final String createShortUrlMethod = "ydpay.base.com.shorturl.create";
	private static final String expandShortUrlMethod = "ydpay.base.com.shorturl.expand";

	private boolean supportkeepalive;

	public HttpServerHandler(boolean supportkeepalive) {
		this.supportkeepalive = supportkeepalive;
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		if (decoder != null) {
			decoder.cleanFiles();
		}
		ctx.flush();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		HttpRequest request = (HttpRequest) msg;

		printRequestData(request);
		// 判断流程
		if (request.getDecoderResult().isFailure()) {
			writeResponse(request, ctx, true,
					"{\"ret\":1,\"message\":\"http解码失败\"}",
					RedirectFlag.NO.getCode());
			return;
		}

		int callMethod = 1;// 调用方式 1-正常的API调用 2-独立加密方式的API调用

		URI uri = new URI(request.getUri());
		String urlPath = uri.getPath();
		String method = "";
		if ("/openapi/rest".equals(urlPath)) {
			callMethod = 1;
		}else if ("/t/create".equals(urlPath)) {
			log.info("-----进入创建短链接处理流程----");
			String query = uri.getQuery();
			String longurl = query.substring("longurl=".length());
			String result = doPostForOtherSystem(createShortUrlMethod,
					"{\"longurl\":\"" + longurl + "\"}");
			log.info("-----创建短链接完成----result=" + result);
			writeResponse(request, ctx, true, result, RedirectFlag.NO.getCode());
			return;
		} else if (urlPath.startsWith("/t/")) {
			// 解析短链接
			log.info("-----进入短链接处理流程----");
			String shorturl = urlPath.substring("/t/".length());
			String result = doPostForOtherSystem(expandShortUrlMethod,
					"{\"shortkey\":\"" + shorturl + "\"}");
			log.info("-----解析短链接完成----result=" + result);
			JSONObject resultObj = JSONObject.parseObject(result);
			if (resultObj.getInteger("ret") == 0) {
				log.info("---成功获取长链接，现在进行重定向--" + result);
				sendRedirect(ctx,
						resultObj.getJSONObject("data").getString("longurl"));
			} else {
				log.info("--接口处理失败或者没有满足条件的短链接--重定向至404----");
				sendError(ctx, HttpResponseStatus.NOT_FOUND);
			}
			return;
		}else {
			// 独立API加密方式调用
			String urlpath = urlPath.substring(1);
			String[] urlsplits = urlpath.toLowerCase().split("/");
			if (urlsplits.length < 4) {
				writeResponse(request, ctx, true,
						gson.toJson(new RetResponse(2, "url路径错误")),
						RedirectFlag.NO.getCode());
				return;
			}
			method = HttpServerUtil.getMethod(urlsplits);
			callMethod = 2;
		}

		Map<String, String> origin_params = null;
		String callback = ""; // 保存callback函数
		int redirectflag = 0; // 保存重定向标志

		String contentParams = "";
		// http Get请求
		if (request.getMethod().equals(HttpMethod.GET)) {
			origin_params = getHttpGetParams(request);
		} else if (request.getMethod().equals(HttpMethod.POST)) {// http POST 请求
			try {
				Map<String, String> url_params = getHttpGetParams(request);
				if (msg instanceof HttpContent) {
					HttpContent httpContent = (HttpContent) msg;
					ByteBuf content = httpContent.content();
					String params = content.toString(CharsetUtil.UTF_8);
					log.info(params);
					origin_params = getJsonParams(params);
					if (origin_params == null) {
						origin_params = makeQueryToMap(params);
						if (origin_params == null) {
							contentParams = params;
						}
					}
				} else {
					origin_params = getHttpPostParams(request);
				}
				if (origin_params == null)
					origin_params = new HashMap<String, String>();
				if (origin_params != null && url_params != null) {
					origin_params.putAll(url_params);
				}
			} catch (Exception e) {
				log.error("解释HTTP POST协议出错:" + e.getMessage(), e);
				writeResponse(request, ctx, true,
						"{\"ret\":3,\"message\":\"解释HTTP POST协议出错\"}",
						RedirectFlag.NO.getCode());
				return;
			}

		} else {
			writeResponse(request, ctx, true,
					"{\"ret\":3,\"message\":\"接口只支持HTTP/HTTPS GET/POST协议\"}",
					RedirectFlag.NO.getCode());
			return;
		}
		log.info("------------------------END REQUEST---------------------\r\n");

		callback = getHttpCallback(origin_params);
		redirectflag = getHttpRedirectflag(origin_params);

		String resultData = "";
		if (callMethod == 1) {
			// 正常的API调用
			if (!"".equals(contentParams))
				origin_params = gson.fromJson(contentParams,
						new TypeToken<Map<String, String>>() {
						}.getType());

			if (origin_params.containsKey("target_appid")
					&& origin_params.containsKey("appid")
					&& StringUtils
							.isNotEmpty(origin_params.get("target_appid"))
					&& StringUtils.isNotEmpty(origin_params.get("appid"))
					&& !origin_params.get("appid").equals(
							origin_params.get("target_appid"))) {

				resultData = doPostForOtherSystem(routeMethod,
						gson.toJson(origin_params));
			} else {
				resultData = doPost(origin_params);
			}
		} else {
			if (!"".equals(contentParams))
				resultData = doPostForOtherSystem(method, contentParams);
			else
				resultData = doPostForOtherSystem(method, origin_params);
		}
		writeResponse(request, ctx, true, resultData, redirectflag, callback);
	}

	@SuppressWarnings("deprecation")
	public static Map<String, String> makeQueryToMap(String str) {
		String[] strArray = str.split("&");
		Map<String, String> paramsObj = new HashMap<String, String>();
		for (String subStr : strArray) {
			int index = subStr.indexOf("=");
			if (index != -1) {
				String key = subStr.substring(0, index);
				String value = subStr.substring(index + 1);
				if (key != null && !"".equals(key) && value != null
						&& !"".equals(value)) {
					paramsObj.put(key, URLDecoder.decode(value));
				}
			}
		}
		if(paramsObj.isEmpty())
			return null;
		return paramsObj;
	}

	protected static void sendRedirect(ChannelHandlerContext ctx, String newUri) {
		FullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
		response.headers().set("location", newUri);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	protected static void sendError(ChannelHandlerContext ctx,
			HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer("Failure: "
						+ status.toString() + "\r\n", CharsetUtil.UTF_8));
		response.headers().set(CONTENT_TYPE, "text/html;charset=UTF-8");
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	protected static void sendRedirect(ChannelHandlerContext ctx,
			HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private Map<String, String> getJsonParams(String params) {
		try {
			return gson.fromJson(params, new TypeToken<Map<String, String>>() {
			}.getType());
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 打印请求信息
	 * 
	 * @param request
	 * @return
	 */
	private void printRequestData(HttpRequest request) {
		StringBuilder requestbuffer = new StringBuilder();
		/* 打印请求信息 */
		requestbuffer
				.append("\r\n######################BEGIN REQUEST####################\r\n");
		requestbuffer.append("VERSION: ").append(request.getProtocolVersion())
				.append("\r\n");
		requestbuffer.append("HOSTNAME: ")
				.append(HttpHeaders.getHost(request, "unknown")).append("\r\n");
		requestbuffer.append("REQUEST_URI: ").append(request.getUri())
				.append("\r\n");
		HttpHeaders headers = request.headers();

		if (!headers.isEmpty()) {
			for (Map.Entry<String, String> h : headers) {
				String key = h.getKey();
				Object value = h.getValue();
				requestbuffer.append("HEADER: ").append(key).append(" = ")
						.append(value).append("\r\n");
			}
			requestbuffer.append("\r\n");
		}
		log.info(requestbuffer.toString());
	}

	private Map<String, String> getHttpGetParams(HttpRequest request) {
		return getQueryParams(request.getUri());
	}

	private Map<String, String> getQueryParams(String params) {
		QueryStringDecoder queryStringDecoder = new QueryStringDecoder(params);
		Map<String, String> paramsMap = new HashMap<String, String>();
		for (Entry<String, List<String>> p : queryStringDecoder.parameters()
				.entrySet()) {
			String key = p.getKey().trim();
			List<String> vals = p.getValue();
			if (vals.size() > 0) {
				String value = vals.get(0);
				log.info(key + ":" + value);
				paramsMap.put(key, value);
			}
		}
		return paramsMap;
	}

	private String getHttpCallback(Map<String, String> origin_params) {
		String callback = "";
		if (origin_params != null && origin_params.containsKey("callback")) {
			callback = (String) origin_params.get("callback");
		}
		return callback;
	}

	private int getHttpRedirectflag(Map<String, String> origin_params) {
		int redirectflag = RedirectFlag.NO.getCode();
		if (origin_params != null && !origin_params.isEmpty()) {
			if (origin_params.containsKey("redirectflag")) {
				redirectflag = Integer.parseInt((String) origin_params
						.get("redirectflag"));
			}
		}
		return redirectflag;
	}

	private Map<String, String> getHttpPostParams(HttpRequest request)
			throws Exception {
		Map<String, String> origin_params = new HashMap<String, String>();
		boolean decodeflag = false;
		decoder = new HttpPostRequestDecoder(factory, request);
		try {
			while (decoder.hasNext()) {
				InterfaceHttpData interfaceHttpData = decoder.next();
				if (interfaceHttpData != null) {
					try {
						/**
						 * HttpDataType有三种类型 Attribute, FileUpload,
						 * InternalAttribute
						 */
						if (interfaceHttpData.getHttpDataType() == HttpDataType.Attribute) {
							Attribute attribute = (Attribute) interfaceHttpData;
							String value = attribute.getValue();
							String key = attribute.getName();
							log.info(key + ":" + value);
							origin_params.put(key, value);
						}
					} catch (Exception e) {
						log.info("获取POST请求参数异常", e);
					} finally {
						interfaceHttpData.release();
					}
				}
			}
		} catch (EndOfDataDecoderException e1) {
			decodeflag = true;
		} catch (Exception e) {
			log.error("解释HTTP POST协议出错:" + e.getMessage(), e);
			throw e;
		}
		if (decodeflag)
			return origin_params;
		return null;
	}

	private void writeResponse(HttpRequest request, ChannelHandlerContext ctx,
			boolean closehttp, String result, int redirectflag) {
		writeResponse(request, ctx, closehttp, result, redirectflag, "");
	}

	private void writeResponse(HttpRequest request, ChannelHandlerContext ctx,
			boolean closehttp, String result, int redirectflag, String callback) {
		/* 当不是主动关闭http通道的时候检测 */
		if (!closehttp) {
			if (request.headers().contains(CONNECTION,
					HttpHeaders.Values.CLOSE, true)) {
				closehttp = true;
			} else if (request.getProtocolVersion()
					.equals(HttpVersion.HTTP_1_0)) {
				closehttp = true;
			}
		}
		boolean keepAlive = false;
		if (!closehttp) {
			keepAlive = HttpHeaders.isKeepAlive(request);
		}
		if (!callback.isEmpty()) {
			result = callback + "(" + result + ")";
		}

		FullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,
				request.getDecoderResult().isSuccess() ? HttpResponseStatus.OK
						: HttpResponseStatus.BAD_REQUEST,
				Unpooled.copiedBuffer(result.toString(), CharsetUtil.UTF_8));

		if (redirectflag == 1) {
			response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
		} else {
			response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
		}

		if (keepAlive && supportkeepalive) {
			response.headers().set(CONTENT_LENGTH,
					response.content().readableBytes());
			response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
		}

		log.info("ydpay Server Response String Is:" + result.toString());
		/* 是否支持http的keep-alive */
		if (!supportkeepalive) {
			closehttp = true;
		}

		ChannelFuture future = ctx.writeAndFlush(response);
		if (closehttp) {
			future.addListener(ChannelFutureListener.CLOSE);
			log.info("服务器主动关闭远程链接");
		}
	}

	private String doPost(Map<String, String> postparams) {
		return doPost(postparams, "doPost");
	}

	private String doPostForOtherSystem(String method,
			Map<String, String> origin_params) {
		try {
			Map<String, String> dataParam = new HashMap<String, String>();
			dataParam.put("method", method);
			dataParam.put("data",
					Base64Method.EncryptBase64(gson.toJson(origin_params)));
			return doPost(dataParam, "doPostForOtherSystem");
		} catch (Exception e) {
			return "{\"ret\":41,\"message\":\"数据加密失败\"}";
		}
	}

	private String doPostForOtherSystem(String method, String origin_params) {
		try {
			Map<String, String> dataParam = new HashMap<String, String>();
			dataParam.put("method", method);
			dataParam.put("data", Base64Method.EncryptBase64(origin_params));
			return doPost(dataParam, "doPostForOtherSystem");
		} catch (Exception e) {
			return "{\"ret\":41,\"message\":\"数据加密失败\"}";
		}
	}

	/**
	 * 传递到dubbo服务器，等待结果返回
	 * 
	 * @return
	 */
	private String doPost(Map<String, String> postparams, String doPostMethod) {
		String data = "";
		if (postparams.isEmpty())
			return gson.toJson(new RetResponse(4, "参数不能为空"));

		if (!postparams.containsKey("method")) {
			return gson.toJson(new RetResponse(6, "参数并不包含method，请检查提交的参数"));
		}

		String method = postparams.get("method").toString().toLowerCase();
		String serviceName = HttpServerUtil.getServiceName(method);
		if (HttpServerUtil.isEmpty(serviceName)) {
			return gson.toJson(new RetResponse(7, "接口method参数出错，请检查提交的参数"));
		}

		Object client = HttpServerSpringContext.getContext().getBean(
				serviceName);
		if (client == null) {
			return gson.toJson(new RetResponse(8, "没有该方法的服务，请联系后台管理员"));
		}

		if (doPostMethod.equals("doPost")) {
			data = (String) HttpServerUtil.invokeMethodGernaral(client,
					doPostMethod, new Object[] { postparams });
		} else {
			data = (String) HttpServerUtil.invokeMethodGernaral(client,
					doPostMethod, new Object[] { postparams.get("method"),
							postparams.get("data") });
		}
		if (data == null) {
			data = gson.toJson(new RetResponse(9, "调用远程方法失败"));
		}
		return data;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		log.info("Request Is Exception:" + cause.getMessage());
		ctx.close();
	}

}
