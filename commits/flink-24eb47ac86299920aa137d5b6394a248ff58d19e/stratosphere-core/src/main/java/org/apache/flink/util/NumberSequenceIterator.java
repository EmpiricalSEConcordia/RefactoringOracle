/***********************************************************************************************************************
 *
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
 *
 **********************************************************************************************************************/
package org.apache.flink.util;

import java.util.Iterator;
import java.util.NoSuchElementException;



/**
 *
 */
/**
 *
 */
public class NumberSequenceIterator implements SplittableIterator<Long> {
	
	private static final long serialVersionUID = 1L;

	private final long to;
	
	private long current;
	
	
	
	public NumberSequenceIterator(long from, long to) {
		if (from > to) {
			throw new IllegalArgumentException("The 'to' value must not be smaller than the 'from' value.");
		}
		
		this.current = from;
		this.to = to;
	}
	
	
	/**
	 * Internal constructor to allow for empty iterators.
	 * 
	 * @param from
	 * @param to
	 * @param mark
	 */
	private NumberSequenceIterator(long from, long to, boolean mark) {
		this.current = from;
		this.to = to;
	}
	
	public long getCurrent() {
		return this.current;
	}
	
	public long getTo() {
		return this.to;
	}

	@Override
	public boolean hasNext() {
		return current <= to;
	}

	@Override
	public Long next() {
		if (current <= to) {
			return current++;
		} else {
			throw new NoSuchElementException();
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public NumberSequenceIterator[] split(int numPartitions) {
		if (numPartitions < 1) {
			throw new IllegalArgumentException("The number of partitions must be at least 1.");
		}
		
		if (numPartitions == 1) {
			return new NumberSequenceIterator[] { new NumberSequenceIterator(current, to) };
		}
		
		// here, numPartitions >= 2 !!!
		
		long elementsPerSplit;
		
		if (to - current + 1 >= 0) {
			elementsPerSplit = (to - current + 1) / numPartitions;
		}
		else {
			// long overflow of the range.
			// we compute based on half the distance, to prevent the overflow.
			// in most cases it holds that: current < 0 and to > 0, except for: to == 0 and current == Long.MIN_VALUE
			// the later needs a special case
			final long halfDiff; // must be positive
			
			if (current == Long.MIN_VALUE) {
				// this means to >= 0
				halfDiff = (Long.MAX_VALUE/2+1) + to/2;
			} else {
				long posFrom = -current;
				if (posFrom > to) {
					halfDiff = to + ((posFrom - to) / 2);
				} else {
					halfDiff = posFrom + ((to - posFrom) / 2);
				}
			}
			elementsPerSplit = halfDiff / numPartitions * 2;
		}
		
		if (elementsPerSplit < Long.MAX_VALUE) {
			// figure out how many get one in addition
			long numWithExtra = -(elementsPerSplit * numPartitions) + to - current + 1;
			
			// based on rounding errors, we may have lost one)
			if (numWithExtra > numPartitions) {
				elementsPerSplit++;
				numWithExtra -= numPartitions;
				
				if (numWithExtra > numPartitions) {
					throw new RuntimeException("Bug in splitting logic. To much rounding loss.");
				}
			}
			
			NumberSequenceIterator[] iters = new NumberSequenceIterator[numPartitions];
			long curr = current;
			int i = 0;
			for (; i < numWithExtra; i++) {
				long next = curr + elementsPerSplit + 1;
				iters[i] = new NumberSequenceIterator(curr, next-1);
				curr = next;
			}
			for (; i < numPartitions; i++) {
				long next = curr + elementsPerSplit;
				iters[i] = new NumberSequenceIterator(curr, next-1, true);
				curr = next;
			}
			
			return iters;
		}
		else {
			// this can only be the case when there are two partitions
			if (numPartitions != 2) {
				throw new RuntimeException("Bug in splitting logic.");
			}
			
			return new NumberSequenceIterator[] {
				new NumberSequenceIterator(current, current + elementsPerSplit),
				new NumberSequenceIterator(current + elementsPerSplit, to)
			};
		}
	}
	
	@Override
	public Iterator<Long> getSplit(int num, int numPartitions) {
		if (numPartitions < 1 || num < 0 || num >= numPartitions) {
			throw new IllegalArgumentException();
		}
		
		return split(numPartitions)[num];
	}

	@Override
	public int getMaximumNumberOfSplits() {
		if (to >= Integer.MAX_VALUE || current <= Integer.MIN_VALUE || to-current+1 >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		else {
			return (int) (to-current+1);
		}
	}
}
