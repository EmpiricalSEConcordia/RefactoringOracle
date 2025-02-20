/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Apache Flink project (http://flink.incubator.apache.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **********************************************************************************************************************/

package eu.stratosphere.nephele.client;

/**
 * This exception is thrown by the {@link JobClient} if a Nephele job has been aborted either as a result of a user
 * request or an error which occurred during the execution.
 * 
 */
public class JobExecutionException extends Exception {

	/**
	 * The generated serial UID.
	 */
	private static final long serialVersionUID = 2818087325120827525L;

	/**
	 * Indicates whether the job has been aborted as a result of user request.
	 */
	private final boolean canceledByUser;

	/**
	 * Constructs a new job execution exception.
	 * 
	 * @param msg
	 *        the message that shall be encapsulated by this exception
	 * @param canceledByUser
	 *        <code>true</code> if the job has been aborted as a result of a user request, <code>false</code> otherwise
	 */
	public JobExecutionException(final String msg, final boolean canceledByUser) {
		super(msg);

		this.canceledByUser = canceledByUser;
	}

	/**
	 * Returns <code>true</code> if the job has been aborted as a result of a user request, <code>false</code>
	 * otherwise.
	 * 
	 * @return <code>true</code> if the job has been aborted as a result of a user request, <code>false</code> otherwise
	 */
	public boolean isJobCanceledByUser() {

		return this.canceledByUser;
	}
}
