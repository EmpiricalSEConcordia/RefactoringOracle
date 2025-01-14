/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.nephele.jobgraph;

/**
 * An abstract base class for output vertices in Nephele.
 * 
 * @author warneke
 */
public abstract class JobOutputVertex extends AbstractJobVertex {

	/**
	 * Constructs a new job output vertex with the given name.
	 * 
	 * @param name
	 *        the name of the new job output vertex
	 * @param id
	 *        the ID of this vertex
	 * @param jobGraph
	 *        the job graph this vertex belongs to
	 */
	protected JobOutputVertex(String name, JobVertexID id, JobGraph jobGraph) {
		super(name, id, jobGraph);

		jobGraph.addVertex(this);
	}
}
