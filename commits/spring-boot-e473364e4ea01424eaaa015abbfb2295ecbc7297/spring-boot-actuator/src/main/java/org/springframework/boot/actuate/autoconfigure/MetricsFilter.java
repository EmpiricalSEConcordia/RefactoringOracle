/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.boot.actuate.autoconfigure;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.util.StopWatch;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * Filter that counts requests and measures processing times.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
final class MetricsFilter extends OncePerRequestFilter {

	private static final String ATTRIBUTE_STOP_WATCH = MetricsFilter.class.getName()
			+ ".StopWatch";

	private static final int UNDEFINED_HTTP_STATUS = 999;

	private static final String UNKNOWN_PATH_SUFFIX = "/unmapped";

	private static final Log logger = LogFactory.getLog(MetricsFilter.class);

	private final CounterService counterService;

	private final GaugeService gaugeService;

	private static final Set<PatternReplacer> STATUS_REPLACERS;
	static {
		Set<PatternReplacer> replacements = new LinkedHashSet<PatternReplacer>();
		replacements.add(new PatternReplacer("[{}]", 0, "-"));
		replacements.add(new PatternReplacer("**", Pattern.LITERAL, "-star-star-"));
		replacements.add(new PatternReplacer("*", Pattern.LITERAL, "-star-"));
		replacements.add(new PatternReplacer("/-", Pattern.LITERAL, "/"));
		replacements.add(new PatternReplacer("-/", Pattern.LITERAL, "/"));
		STATUS_REPLACERS = Collections.unmodifiableSet(replacements);
	}

	private static final Set<PatternReplacer> KEY_REPLACERS;
	static {
		Set<PatternReplacer> replacements = new LinkedHashSet<PatternReplacer>();
		replacements.add(new PatternReplacer("/", Pattern.LITERAL, "."));
		replacements.add(new PatternReplacer("..", Pattern.LITERAL, "."));
		KEY_REPLACERS = Collections.unmodifiableSet(replacements);
	}

	MetricsFilter(CounterService counterService, GaugeService gaugeService) {
		this.counterService = counterService;
		this.gaugeService = gaugeService;
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain chain)
					throws ServletException, IOException {
		StopWatch stopWatch = createStopWatchIfNecessary(request);
		String path = new UrlPathHelper().getPathWithinApplication(request);
		int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
		try {
			chain.doFilter(request, response);
			status = getStatus(response);
		}
		finally {
			if (!request.isAsyncStarted()) {
				stopWatch.stop();
				request.removeAttribute(ATTRIBUTE_STOP_WATCH);
				recordMetrics(request, path, status, stopWatch.getTotalTimeMillis());
			}
		}
	}

	private StopWatch createStopWatchIfNecessary(HttpServletRequest request) {
		StopWatch stopWatch = (StopWatch) request.getAttribute(ATTRIBUTE_STOP_WATCH);
		if (stopWatch == null) {
			stopWatch = new StopWatch();
			stopWatch.start();
			request.setAttribute(ATTRIBUTE_STOP_WATCH, stopWatch);
		}
		return stopWatch;
	}

	private int getStatus(HttpServletResponse response) {
		try {
			return response.getStatus();
		}
		catch (Exception ex) {
			return UNDEFINED_HTTP_STATUS;
		}
	}

	private void recordMetrics(HttpServletRequest request, String path, int status,
			long time) {
		String suffix = getFinalStatus(request, path, status);
		submitToGauge(getKey("response" + suffix), time);
		incrementCounter(getKey("status." + status + suffix));
	}

	private String getFinalStatus(HttpServletRequest request, String path, int status) {
		Object bestMatchingPattern = request
				.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (bestMatchingPattern != null) {
			return fixSpecialCharacters(bestMatchingPattern.toString());
		}
		Series series = getSeries(status);
		if (Series.CLIENT_ERROR.equals(series) || Series.REDIRECTION.equals(series)) {
			return UNKNOWN_PATH_SUFFIX;
		}
		return path;
	}

	private String fixSpecialCharacters(String value) {
		String result = value;
		for (PatternReplacer replacer : STATUS_REPLACERS) {
			result = replacer.apply(result);
		}
		if (result.endsWith("-")) {
			result = result.substring(0, result.length() - 1);
		}
		if (result.startsWith("-")) {
			result = result.substring(1);
		}
		return result;
	}

	private Series getSeries(int status) {
		try {
			return HttpStatus.valueOf(status).series();
		}
		catch (Exception ex) {
			return null;
		}

	}

	private String getKey(String string) {
		// graphite compatible metric names
		String key = string;
		for (PatternReplacer replacer : KEY_REPLACERS) {
			key = replacer.apply(key);
		}
		if (key.endsWith(".")) {
			key = key + "root";
		}
		if (key.startsWith("_")) {
			key = key.substring(1);
		}
		return key;
	}

	private void submitToGauge(String metricName, double value) {
		try {
			this.gaugeService.submit(metricName, value);
		}
		catch (Exception ex) {
			logger.warn("Unable to submit gauge metric '" + metricName + "'", ex);
		}
	}

	private void incrementCounter(String metricName) {
		try {
			this.counterService.increment(metricName);
		}
		catch (Exception ex) {
			logger.warn("Unable to submit counter metric '" + metricName + "'", ex);
		}
	}

	private static class PatternReplacer {

		private final Pattern pattern;

		private final String replacement;

		PatternReplacer(String regex, int flags, String replacement) {
			this.pattern = Pattern.compile(regex, flags);
			this.replacement = replacement;
		}

		public String apply(String input) {
			return this.pattern.matcher(input).replaceAll(
					Matcher.quoteReplacement(this.replacement));
		}

	}

}
