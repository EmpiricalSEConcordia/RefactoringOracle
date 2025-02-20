/*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.support;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.multiaction.NoSuchRequestHandlingMethodException;

/**
 * Default implementation of the {@link org.springframework.web.servlet.HandlerExceptionResolver
 * HandlerExceptionResolver} interface that resolves standard Spring exceptions. <p>Default implementations typically
 * set the response status to a corresponding HTTP status code. <p>This exception resolver is enabled by default in the
 * {@link org.springframework.web.servlet.DispatcherServlet}.
 *
 * @author Arjen Poutsma
 * @see #handleNoSuchRequestHandlingMethod
 * @see #handleHttpRequestMethodNotSupported
 * @see #handleHttpMediaTypeNotSupported
 * @see #handleMissingServletRequestParameter
 * @see #handleTypeMismatch
 * @see #handleHttpMessageNotReadable
 * @see #handleHttpMessageNotWritable
 * @since 3.0
 */
public class DefaultHandlerExceptionResolver extends AbstractHandlerExceptionResolver {

	/**
	 * Log category to use when no mapped handler is found for a request.
	 *
	 * @see #pageNotFoundLogger
	 */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Additional logger to use when no mapped handler is found for a request.
	 *
	 * @see #PAGE_NOT_FOUND_LOG_CATEGORY
	 */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	/** Sets the {@linkplain #setOrder(int) order} to {@link #LOWEST_PRECEDENCE}. */
	public DefaultHandlerExceptionResolver() {
		setOrder(Ordered.LOWEST_PRECEDENCE);
	}

	@Override
	protected ModelAndView doResolveException(HttpServletRequest request,
			HttpServletResponse response,
			Object handler,
			Exception ex) {
		try {
			if (ex instanceof NoSuchRequestHandlingMethodException) {
				return handleNoSuchRequestHandlingMethod((NoSuchRequestHandlingMethodException) ex, request, response,
						handler);
			}
			else if (ex instanceof HttpRequestMethodNotSupportedException) {
				return handleHttpRequestMethodNotSupported((HttpRequestMethodNotSupportedException) ex, request,
						response, handler);
			}
			else if (ex instanceof HttpMediaTypeNotSupportedException) {
				return handleHttpMediaTypeNotSupported((HttpMediaTypeNotSupportedException) ex, request, response,
						handler);
			}
			else if (ex instanceof MissingServletRequestParameterException) {
				return handleMissingServletRequestParameter((MissingServletRequestParameterException) ex, request,
						response, handler);
			}
			else if (ex instanceof ConversionNotSupportedException) {
				return handleConversionNotSupported((ConversionNotSupportedException) ex, request, response, handler);
			}
			else if (ex instanceof TypeMismatchException) {
				return handleTypeMismatch((TypeMismatchException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMessageNotReadableException) {
				return handleHttpMessageNotReadable((HttpMessageNotReadableException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMessageNotWritableException) {
				return handleHttpMessageNotWritable((HttpMessageNotWritableException) ex, request, response, handler);
			}
		}
		catch (Exception handlerException) {
			logger.warn("Handling of [" + ex.getClass().getName() + "] resulted in Exception", handlerException);
		}
		return null;
	}

	/**
	 * Handle the case where no request handler method was found. <p>The default implementation logs a warning, sends an
	 * HTTP 404 error, and returns an empty {@code ModelAndView}. Alternatively, a fallback view could be chosen, or the
	 * NoSuchRequestHandlingMethodException could be rethrown as-is.
	 *
	 * @param ex the NoSuchRequestHandlingMethodException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleNoSuchRequestHandlingMethod(NoSuchRequestHandlingMethodException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {

		pageNotFoundLogger.warn(ex.getMessage());
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
		return new ModelAndView();
	}

	/**
	 * Handle the case where no request handler method was found for the particular HTTP request method. <p>The default
	 * implementation logs a warning, sends an HTTP 405 error, sets the "Allow" header, and returns an empty {@code
	 * ModelAndView}. Alternatively, a fallback view could be chosen, or the HttpRequestMethodNotSupportedException could
	 * be rethrown as-is.
	 *
	 * @param ex the HttpRequestMethodNotSupportedException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {

		pageNotFoundLogger.warn(ex.getMessage());
		String[] supportedMethods = ex.getSupportedMethods();
		if (supportedMethods != null) {
			response.setHeader("Allow", StringUtils.arrayToDelimitedString(supportedMethods, ", "));
		}
		response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, ex.getMessage());
		return new ModelAndView();
	}

	/**
	 * Handle the case where no {@linkplain org.springframework.http.converter.HttpMessageConverter message converters}
	 * were found for the PUT or POSTed content. <p>The default implementation sends an HTTP 415 error, sets the "Allow"
	 * header, and returns an empty {@code ModelAndView}. Alternatively, a fallback view could be chosen, or the
	 * HttpMediaTypeNotSupportedException could be rethrown as-is.
	 *
	 * @param ex the HttpMediaTypeNotSupportedException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {

		response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
		List<MediaType> mediaTypes = ex.getSupportedMediaTypes();
		if (mediaTypes != null) {
			response.setHeader("Accept", MediaType.toString(mediaTypes));
		}
		return new ModelAndView();
	}

	/**
	 * Handle the case when a required parameter is missing. <p>The default implementation sends an HTTP 400 error, and
	 * returns an empty {@code ModelAndView}. Alternatively, a fallback view could be chosen, or the
	 * MissingServletRequestParameterException could be rethrown as-is.
	 *
	 * @param ex the MissingServletRequestParameterException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * Handle the case when a {@link org.springframework.web.bind.WebDataBinder} conversion cannot occur. <p>The default
	 * implementation sends an HTTP 500 error, and returns an empty {@code ModelAndView}. Alternatively, a fallback view
	 * could be chosen, or the TypeMismatchException could be rethrown as-is.
	 *
	 * @param ex the ConversionNotSupportedException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleConversionNotSupported(ConversionNotSupportedException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		return new ModelAndView();
	}

	/**
	 * Handle the case when a {@link org.springframework.web.bind.WebDataBinder} conversion error occurs. <p>The default
	 * implementation sends an HTTP 400 error, and returns an empty {@code ModelAndView}. Alternatively, a fallback view
	 * could be chosen, or the TypeMismatchException could be rethrown as-is.
	 *
	 * @param ex the TypeMismatchException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleTypeMismatch(TypeMismatchException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {
		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * Handle the case where a {@linkplain org.springframework.http.converter.HttpMessageConverter message converter} can
	 * not read from a HTTP request. <p>The default implementation sends an HTTP 400 error, and returns an empty {@code
	 * ModelAndView}. Alternatively, a fallback view could be chosen, or the HttpMediaTypeNotSupportedException could be
	 * rethrown as-is.
	 *
	 * @param ex the HttpMessageNotReadableException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * Handle the case where a {@linkplain org.springframework.http.converter.HttpMessageConverter message converter} can
	 * not write to a HTTP request. <p>The default implementation sends an HTTP 500 error, and returns an empty {@code
	 * ModelAndView}. Alternatively, a fallback view could be chosen, or the HttpMediaTypeNotSupportedException could be
	 * rethrown as-is.
	 *
	 * @param ex the HttpMessageNotWritableException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
	 * if multipart resolution failed)
	 * @return a ModelAndView to render, or <code>null</code> if handled directly
	 * @throws Exception an Exception that should be thrown as result of the servlet request
	 */
	protected ModelAndView handleHttpMessageNotWritable(HttpMessageNotWritableException ex,
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws Exception {

		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		return new ModelAndView();
	}

}
