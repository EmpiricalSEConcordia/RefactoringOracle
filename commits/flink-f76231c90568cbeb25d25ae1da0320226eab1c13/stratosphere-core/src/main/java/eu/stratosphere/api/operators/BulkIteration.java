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

package eu.stratosphere.api.operators;

import eu.stratosphere.api.InvalidJobException;
import eu.stratosphere.api.functions.AbstractFunction;
import eu.stratosphere.api.functions.aggregators.AggregatorRegistry;
import eu.stratosphere.api.operators.util.UserCodeClassWrapper;
import eu.stratosphere.api.operators.util.UserCodeWrapper;
import eu.stratosphere.util.Visitor;

/**
 * 
 */
public class BulkIteration extends SingleInputOperator<AbstractFunction> implements IterationOperator {
	
	private static String DEFAULT_NAME = "<Unnamed Bulk Iteration>";
	
	private Operator iterationResult;
	
	private Operator inputPlaceHolder = new PartialSolutionPlaceHolder(this);
	
	private final AggregatorRegistry aggregators = new AggregatorRegistry();
	
	private int numberOfIterations = -1;
	
	// --------------------------------------------------------------------------------------------
	
	/**
	 * 
	 */
	public BulkIteration() {
		this(DEFAULT_NAME);
	}
	
	/**
	 * @param name
	 */
	public BulkIteration(String name) {
		super(new UserCodeClassWrapper<AbstractFunction>(AbstractFunction.class), name);
	}

	// --------------------------------------------------------------------------------------------
	
	/**
	 * @return The contract representing the partial solution.
	 */
	public Operator getPartialSolution() {
		return this.inputPlaceHolder;
	}
	
	/**
	 * @param result
	 */
	public void setNextPartialSolution(Operator result) {
		if (result == null) {
			throw new NullPointerException("Operator producing the next partial solution must not be null.");
		}
		this.iterationResult = result;
	}
	
	/**
	 * @return The contract representing the next partial solution.
	 */
	public Operator getNextPartialSolution() {
		return this.iterationResult;
	}
	
	/**
	 * @return The contract representing the termination criterion.
	 */
	public Operator getTerminationCriterion() {
		return null;
	}
	
	/**
	 * @param criterion
	 */
	public void setTerminationCriterion(Operator criterion) {
		throw new UnsupportedOperationException("Termination criterion support is currently not implemented.");
	}
	
	/**
	 * @param num
	 */
	public void setMaximumNumberOfIterations(int num) {
		if (num < 1) {
			throw new IllegalArgumentException("The number of iterations must be at least one.");
		}
		this.numberOfIterations = num;
	}
	
	public int getMaximumNumberOfIterations() {
		return this.numberOfIterations;
	}
	
	@Override
	public AggregatorRegistry getAggregators() {
		return this.aggregators;
	}
	
	/**
	 * @throws Exception
	 */
	public void validate() throws InvalidJobException {
		if (this.input == null || this.input.isEmpty()) {
			throw new RuntimeException("Operator for initial partial solution is not set.");
		}
		if (this.iterationResult == null) {
			throw new InvalidJobException("Operator producing the next version of the partial " +
					"solution (iteration result) is not set.");
		}
		if (this.numberOfIterations <= 0) {
			throw new InvalidJobException("No termination condition is set " +
					"(neither fix number of iteration nor termination criterion).");
		}
//		if (this.terminationCriterion != null && this.numberOfIterations > 0) {
//			throw new Exception("Termination condition is ambiguous. " +
//				"Both a fix number of iteration and a termination criterion are set.");
//		}
	}
	
	// --------------------------------------------------------------------------------------------
	
	/**
	 * Specialized contract to use as a recognizable place-holder for the input to the
	 * step function when composing the nested data flow.
	 */
	// Integer is only a dummy here but this whole placeholder shtick seems a tad bogus.
	public static class PartialSolutionPlaceHolder extends Operator {
		
		private final BulkIteration containingIteration;
		
		public PartialSolutionPlaceHolder(BulkIteration container) {
			super("Partial Solution Place Holder");
			this.containingIteration = container;
		}
		
		public BulkIteration getContainingBulkIteration() {
			return this.containingIteration;
		}
		
		@Override
		public void accept(Visitor<Operator> visitor) {
			visitor.preVisit(this);
			visitor.postVisit(this);
		}

		@Override
		public UserCodeWrapper<?> getUserCodeWrapper() {
			return null;
		}
	}
}
