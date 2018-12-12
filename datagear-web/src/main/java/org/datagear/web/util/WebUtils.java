/*
 * Copyright 2018 datagear.tech. All Rights Reserved.
 */

package org.datagear.web.util;

import java.util.Calendar;
import java.util.Locale;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.datagear.management.domain.User;
import org.datagear.persistence.support.UUID;
import org.datagear.web.OperationMessage;
import org.datagear.web.security.AuthUser;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Web工具集。
 * 
 * @author datagear@163.com
 *
 */
public class WebUtils
{
	public static final String SESSION_KEY_USER_ANONYMOUS = "USER_ANONYMOUS";

	public static final String COOKIE_USER_ID_ANONYMOUS = "USER_ID_ANONYMOUS";

	public static final String COOKIE_PAGINATION_SIZE = "PAGINATION_PAGE_SIZE";

	/** Servlet环境中存储操作消息的关键字 */
	public static final String KEY_OPERATION_MESSAGE = "operationMessage";

	/** 页面ID关键字 */
	public static final String KEY_PAGE_ID = "pageId";

	/** 父页面ID关键字 */
	public static final String KEY_PARENT_PAGE_ID = "parentPageId";

	/**
	 * 获取当前用户（认证用户或者匿名用户）。
	 * <p>
	 * 此方法不会返回{@code null}。
	 * </p>
	 * 
	 * @param request
	 * @param response
	 * @return
	 */
	public static User getUser(HttpServletRequest request, HttpServletResponse response)
	{
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication != null)
		{
			User user = getUser(authentication);

			if (user != null)
				return user;
		}

		HttpSession session = request.getSession();

		User anonymousUser = (User) request.getSession().getAttribute(SESSION_KEY_USER_ANONYMOUS);

		if (anonymousUser == null)
		{
			String anonymousUserId = getCookieValue(request, COOKIE_USER_ID_ANONYMOUS);

			if (anonymousUserId == null || anonymousUserId.isEmpty())
			{
				anonymousUserId = UUID.gen();

				Cookie cookie = new Cookie(COOKIE_USER_ID_ANONYMOUS, anonymousUserId);
				cookie.setPath(request.getContextPath() + "/");
				cookie.setMaxAge(60 * 60 * 24 * 365 * 10);

				response.addCookie(cookie);
			}

			anonymousUser = new User(anonymousUserId);
			anonymousUser.setName(anonymousUserId);
			anonymousUser.setAdmin(false);
			anonymousUser.setAnonymous(true);
			anonymousUser.setCreateTime(new java.util.Date());

			session.setAttribute(SESSION_KEY_USER_ANONYMOUS, anonymousUser);
		}

		return anonymousUser;
	}

	/**
	 * 获取认证用户。
	 * <p>
	 * 如果未认证，此方法将返回{@code null}。
	 * </p>
	 * 
	 * @param authentication
	 * @return
	 */
	public static User getUser(Authentication authentication)
	{
		Object principal = authentication.getPrincipal();

		if (principal instanceof User)
		{
			return (User) principal;
		}
		else if (principal instanceof AuthUser)
		{
			AuthUser ou = (AuthUser) principal;
			return ou.getUser();
		}
		else
			return null;
	}

	/**
	 * 获取操作消息。
	 * 
	 * @param request
	 * @return
	 */
	public static OperationMessage getOperationMessage(HttpServletRequest request)
	{
		return (OperationMessage) request.getAttribute(KEY_OPERATION_MESSAGE);
	}

	/**
	 * 设置异常操作消息。
	 * 
	 * @param request
	 * @param operationMessage
	 * @return
	 */
	public static void setOperationMessage(HttpServletRequest request, OperationMessage operationMessage)
	{
		request.setAttribute(KEY_OPERATION_MESSAGE, operationMessage);
	}

	/**
	 * 获取{@linkplain Cookie}。
	 * 
	 * @param request
	 * @param cookieName
	 * @return
	 */
	public static String getCookieValue(HttpServletRequest request, String cookieName)
	{
		Cookie[] cookies = request.getCookies();

		if (cookies == null)
			return null;

		for (Cookie cookie : cookies)
		{
			if (cookieName.equals(cookie.getName()))
				return cookie.getValue();
		}

		return null;
	}

	/**
	 * 获取请求{@linkplain Locale}。
	 * 
	 * @param request
	 * @return
	 */
	public static Locale getLocale(HttpServletRequest request)
	{
		return LocaleContextHolder.getLocale();
	}

	/**
	 * 转义HTML字符串。
	 * 
	 * @param s
	 * @return
	 */
	public static String escapeHtml(String s)
	{
		if (s == null || s.isEmpty())
			return s;

		StringBuilder sb = new StringBuilder();

		char[] cs = s.toCharArray();

		for (char c : cs)
		{
			if (c == '<')
				sb.append("&lt;");
			else if (c == '>')
				sb.append("&gt;");
			else if (c == '&')
				sb.append("&amp;");
			else if (c == '"')
				sb.append("&quot;");
			else
				sb.append(c);
		}

		return sb.toString();
	}

	/**
	 * 转换为JavaScript语法的字符串。
	 * 
	 * @param s
	 * @return
	 */
	public static String escapeJavaScriptStringValue(String s)
	{
		if (s == null)
			return "";

		StringBuilder sb = new StringBuilder();

		char[] cs = s.toCharArray();

		for (char c : cs)
		{
			if (c == '\\')
				sb.append("\\\\");
			else if (c == '\'')
				sb.append("\\\'");
			else if (c == '"')
				sb.append("\\\"");
			else if (c == '\t')
				sb.append("\\\t");
			else if (c == '\n')
				sb.append("\\\n");
			else if (c == '\r')
				sb.append("\\\r");
			else
				sb.append(c);
		}

		return sb.toString();
	}

	/**
	 * 以秒数滚动时间。
	 * 
	 * @param date
	 * @param seconds
	 * @return
	 */
	public static java.util.Date addSeconds(java.util.Date date, int seconds)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.add(Calendar.SECOND, seconds);

		return calendar.getTime();
	}

	/**
	 * 判断是否是JSON响应。
	 * 
	 * @param response
	 * @return
	 */
	public static boolean isJsonResponse(HttpServletResponse response)
	{
		String __contentType = response.getContentType();
		if (__contentType == null)
			__contentType = "";
		else
			__contentType = __contentType.toLowerCase();

		return (__contentType.indexOf("json") >= 0);
	}

	/**
	 * 获取页面ID。
	 * <p>
	 * 页面ID作为一次请求的客户端标识，用于为客户端定义页面对象。
	 * </p>
	 * 
	 * @param request
	 * @return
	 */
	public static String getPageId(HttpServletRequest request)
	{
		return (String) request.getAttribute(KEY_PAGE_ID);
	}

	/**
	 * 设置页面ID。
	 * <p>
	 * 设置后，在页面可以使用EL表达式<code>${pageId}</code>来获取。
	 * </p>
	 * 
	 * @param request
	 * @param pageId
	 */
	public static void setPageId(HttpServletRequest request, String pageId)
	{
		request.setAttribute(KEY_PAGE_ID, pageId);
	}

	/**
	 * 设置页面ID。
	 * 
	 * @param request
	 * @return
	 */
	public static String setPageId(HttpServletRequest request)
	{
		String pageId = generatePageId();
		setPageId(request, pageId);

		return pageId;
	}

	/**
	 * 生成页面ID。
	 * 
	 * @return
	 */
	public static String generatePageId()
	{
		return generatePageId("p");
	}

	/**
	 * 生成页面ID。
	 * 
	 * @param prefix
	 * @return
	 */
	public static String generatePageId(String prefix)
	{
		String uuid = java.util.UUID.randomUUID().toString();

		StringBuilder sb = new StringBuilder();

		sb.append(prefix);

		char[] cs = uuid.toCharArray();
		for (int i = 0; i < cs.length; i++)
		{
			char c = cs[i];

			if (c != '-')
				sb.append(c);
		}

		return sb.toString();
	}

	/**
	 * 获取父页面ID。
	 * <p>
	 * 如果没有定义，则返回空字符串。
	 * </p>
	 * 
	 * @param request
	 * @return
	 */
	public static String getParentPageId(HttpServletRequest request)
	{
		String parentPage = request.getParameter(KEY_PARENT_PAGE_ID);

		if (parentPage == null)
			parentPage = (String) request.getAttribute(KEY_PARENT_PAGE_ID);

		if (parentPage == null)
			parentPage = "";

		return parentPage;
	}

	/**
	 * 获取页面表单ID。
	 * 
	 * @param request
	 * @return
	 */
	public static String getPageFormId(HttpServletRequest request)
	{
		return getPageElementId(request, "form");
	}

	/**
	 * 获取页面表格ID。
	 * 
	 * @param request
	 * @return
	 */
	public static String getPageTableId(HttpServletRequest request)
	{
		return getPageElementId(request, "table");
	}

	/**
	 * 获取页面元素ID。
	 * 
	 * @param request
	 * @param elementId
	 * @return
	 */
	public static String getPageElementId(HttpServletRequest request, String elementId)
	{
		String pageId = getPageId(request);
		return pageId + "-" + elementId;
	}
}
