/*
 * Copyright 2012-2014 the original author or authors.
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

package org.springframework.boot.actuate.metrics.jdbc;

import javax.sql.DataSource;

/**
 * Provide a {@link DataSourceMetadata} based on a {@link DataSource}.
 *
 * @author Stephane Nicoll
 * @since 1.2.0
 */
public interface DataSourceMetadataProvider {

	/**
	 * Return the {@link DataSourceMetadata} instance able to manage the
	 * specified {@link DataSource} or {@code null} if the given data
	 * source could not be handled.
	 */
	DataSourceMetadata getDataSourceMetadata(DataSource dataSource);

}
