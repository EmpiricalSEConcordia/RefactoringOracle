/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.datastream;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.RichFilterFunction;
import org.apache.flink.api.java.functions.RichFlatMapFunction;
import org.apache.flink.api.java.functions.RichGroupReduceFunction;
import org.apache.flink.api.java.functions.RichMapFunction;
import org.apache.flink.api.java.functions.RichReduceFunction;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.streaming.api.JobGraphBuilder;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.function.aggregation.AggregationFunction;
import org.apache.flink.streaming.api.function.aggregation.MaxAggregationFunction;
import org.apache.flink.streaming.api.function.aggregation.MinAggregationFunction;
import org.apache.flink.streaming.api.function.aggregation.SumAggregationFunction;
import org.apache.flink.streaming.api.function.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.function.sink.SinkFunction;
import org.apache.flink.streaming.api.function.sink.WriteFormatAsCsv;
import org.apache.flink.streaming.api.function.sink.WriteFormatAsText;
import org.apache.flink.streaming.api.function.sink.WriteSinkFunctionByBatches;
import org.apache.flink.streaming.api.function.sink.WriteSinkFunctionByMillis;
import org.apache.flink.streaming.api.invokable.SinkInvokable;
import org.apache.flink.streaming.api.invokable.StreamOperatorInvokable;
import org.apache.flink.streaming.api.invokable.operator.BatchGroupReduceInvokable;
import org.apache.flink.streaming.api.invokable.operator.FilterInvokable;
import org.apache.flink.streaming.api.invokable.operator.FlatMapInvokable;
import org.apache.flink.streaming.api.invokable.operator.MapInvokable;
import org.apache.flink.streaming.api.invokable.operator.StreamReduceInvokable;
import org.apache.flink.streaming.api.invokable.operator.WindowGroupReduceInvokable;
import org.apache.flink.streaming.api.invokable.util.DefaultTimestamp;
import org.apache.flink.streaming.api.invokable.util.Timestamp;
import org.apache.flink.streaming.partitioner.BroadcastPartitioner;
import org.apache.flink.streaming.partitioner.DistributePartitioner;
import org.apache.flink.streaming.partitioner.FieldsPartitioner;
import org.apache.flink.streaming.partitioner.ForwardPartitioner;
import org.apache.flink.streaming.partitioner.ShufflePartitioner;
import org.apache.flink.streaming.partitioner.StreamPartitioner;
import org.apache.flink.streaming.util.serialization.FunctionTypeWrapper;
import org.apache.flink.streaming.util.serialization.TypeSerializerWrapper;
import org.apache.flink.types.TypeInformation;

/**
 * A DataStream represents a stream of elements of the same type. A DataStream
 * can be transformed into another DataStream by applying a transformation as
 * for example
 * <ul>
 * <li>{@link DataStream#map},</li>
 * <li>{@link DataStream#filter}, or</li>
 * <li>{@link DataStream#batchReduce}.</li>
 * </ul>
 * 
 * @param <OUT>
 *            The type of the DataStream, i.e., the type of the elements of the
 *            DataStream.
 */
public class DataStream<OUT> {

	protected static Integer counter = 0;
	protected final StreamExecutionEnvironment environment;
	protected final String id;
	protected int degreeOfParallelism;
	protected List<String> userDefinedNames;
	protected boolean selectAll;
	protected StreamPartitioner<OUT> partitioner;
	protected TypeSerializerWrapper<OUT> outTypeWrapper;
	protected List<DataStream<OUT>> mergedStreams;

	protected final JobGraphBuilder jobGraphBuilder;

	/**
	 * Create a new {@link DataStream} in the given execution environment with
	 * partitioning set to forward by default.
	 * 
	 * @param environment
	 *            StreamExecutionEnvironment
	 * @param operatorType
	 *            The type of the operator in the component
	 * @param outTypeWrapper
	 *            Type of the output
	 */
	public DataStream(StreamExecutionEnvironment environment, String operatorType,
			TypeSerializerWrapper<OUT> outTypeWrapper) {
		if (environment == null) {
			throw new NullPointerException("context is null");
		}

		counter++;
		this.id = operatorType + "-" + counter.toString();
		this.environment = environment;
		this.degreeOfParallelism = environment.getDegreeOfParallelism();
		this.jobGraphBuilder = environment.getJobGraphBuilder();
		this.userDefinedNames = new ArrayList<String>();
		this.selectAll = false;
		this.partitioner = new ForwardPartitioner<OUT>();
		this.outTypeWrapper = outTypeWrapper;
		this.mergedStreams = new ArrayList<DataStream<OUT>>();
		this.mergedStreams.add(this);
	}

	/**
	 * Create a new DataStream by creating a copy of another DataStream
	 * 
	 * @param dataStream
	 *            The DataStream that will be copied.
	 */
	public DataStream(DataStream<OUT> dataStream) {
		this.environment = dataStream.environment;
		this.id = dataStream.id;
		this.degreeOfParallelism = dataStream.degreeOfParallelism;
		this.userDefinedNames = new ArrayList<String>(dataStream.userDefinedNames);
		this.selectAll = dataStream.selectAll;
		this.partitioner = dataStream.partitioner;
		this.jobGraphBuilder = dataStream.jobGraphBuilder;
		this.outTypeWrapper = dataStream.outTypeWrapper;
		this.mergedStreams = new ArrayList<DataStream<OUT>>();
		this.mergedStreams.add(this);
		if (dataStream.mergedStreams.size() > 1) {
			for (int i = 1; i < dataStream.mergedStreams.size(); i++) {
				this.mergedStreams.add(new DataStream<OUT>(dataStream.mergedStreams.get(i)));
			}
		}

	}

	/**
	 * Partitioning strategy on the stream.
	 */
	public static enum ConnectionType {
		SHUFFLE, BROADCAST, FIELD, FORWARD, DISTRIBUTE
	}

	/**
	 * Returns the ID of the {@link DataStream}.
	 * 
	 * @return ID of the DataStream
	 */
	public String getId() {
		return id;
	}

	/**
	 * Gets the degree of parallelism for this operator.
	 * 
	 * @return The parallelism set for this operator.
	 */
	public int getParallelism() {
		return this.degreeOfParallelism;
	}

	/**
	 * Gets the output type.
	 * 
	 * @return The output type.
	 */
	public TypeInformation<OUT> getOutputType() {
		return this.outTypeWrapper.getTypeInfo();
	}

	/**
	 * Gets the class of the field at the given position
	 * 
	 * @param pos
	 *            Position of the field
	 * @return The class of the field
	 */
	@SuppressWarnings("rawtypes")
	protected Class<?> getClassAtPos(int pos) {
		Class<?> type;
		TypeInformation<OUT> outTypeInfo = outTypeWrapper.getTypeInfo();
		if (outTypeInfo.isTupleType()) {
			type = ((TupleTypeInfo) outTypeInfo).getTypeAt(pos).getTypeClass();
		} else if (pos == 0) {
			type = outTypeInfo.getTypeClass();
		} else {
			throw new IndexOutOfBoundsException("Position is out of range");
		}
		return type;
	}

	/**
	 * Checks if the given field position is allowed for the output type
	 * 
	 * @param pos
	 *            Position to check
	 */
	protected void checkFieldRange(int pos) {
		try {
			getClassAtPos(pos);
		} catch (IndexOutOfBoundsException e) {
			throw new RuntimeException("Selected field is out of range");

		}
	}

	/**
	 * Creates a new {@link MergedDataStream} by merging {@link DataStream}
	 * outputs of the same type with each other. The DataStreams merged using
	 * this operator will be transformed simultaneously.
	 * 
	 * @param streams
	 *            The DataStreams to merge output with.
	 * @return The {@link MergedDataStream}.
	 */
	public DataStream<OUT> merge(DataStream<OUT>... streams) {
		DataStream<OUT> returnStream = this.copy();

		for (DataStream<OUT> stream : streams) {
			for (DataStream<OUT> ds : stream.mergedStreams) {
				validateMerge(ds.getId());
				returnStream.mergedStreams.add(ds.copy());
			}
		}
		return returnStream;
	}

	private void validateMerge(String id) {
		for (DataStream<OUT> ds : this.mergedStreams) {
			if (ds.getId().equals(id)) {
				throw new RuntimeException("A DataStream cannot be merged with itself");
			}
		}
	}

	/**
	 * Creates a new {@link ConnectedDataStream} by connecting
	 * {@link DataStream} outputs of different type with each other. The
	 * DataStreams connected using this operators can be used with CoFunctions.
	 * 
	 * @param dataStream
	 *            The DataStream with which this stream will be joined.
	 * @return The {@link ConnectedDataStream}.
	 */
	public <R> ConnectedDataStream<OUT, R> connect(DataStream<R> dataStream) {
		return new ConnectedDataStream<OUT, R>(environment, jobGraphBuilder, this, dataStream);
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are partitioned by their hashcode and are sent to only one component.
	 * 
	 * @param keyPosition
	 *            The field used to compute the hashcode.
	 * @return The DataStream with field partitioning set.
	 */
	public DataStream<OUT> partitionBy(int keyPosition) {
		if (keyPosition < 0) {
			throw new IllegalArgumentException("The position of the field must be non-negative");
		}

		return setConnectionType(new FieldsPartitioner<OUT>(keyPosition));
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are broadcasted to every parallel instance of the next component.
	 * 
	 * @return The DataStream with broadcast partitioning set.
	 */
	public DataStream<OUT> broadcast() {
		return setConnectionType(new BroadcastPartitioner<OUT>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are shuffled to the next component.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<OUT> shuffle() {
		return setConnectionType(new ShufflePartitioner<OUT>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are forwarded to the local subtask of the next component. This is the
	 * default partitioner setting.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<OUT> forward() {
		return setConnectionType(new ForwardPartitioner<OUT>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are distributed evenly to the next component.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<OUT> distribute() {
		return setConnectionType(new DistributePartitioner<OUT>());
	}

	/**
	 * Applies a Map transformation on a {@link DataStream}. The transformation
	 * calls a {@link MapFunction} for each element of the DataStream. Each
	 * MapFunction call returns exactly one element. The user can also extend
	 * {@link RichMapFunction} to gain access to other features provided by the
	 * {@link RichFuntion} interface.
	 * 
	 * @param mapper
	 *            The MapFunction that is called for each element of the
	 *            DataStream.
	 * @param <R>
	 *            output type
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> map(MapFunction<OUT, R> mapper) {
		FunctionTypeWrapper<OUT> inTypeWrapper = new FunctionTypeWrapper<OUT>(mapper,
				MapFunction.class, 0);
		FunctionTypeWrapper<R> outTypeWrapper = new FunctionTypeWrapper<R>(mapper,
				MapFunction.class, 1);

		return addFunction("map", mapper, inTypeWrapper, outTypeWrapper, new MapInvokable<OUT, R>(
				mapper));
	}

	/**
	 * Applies a FlatMap transformation on a {@link DataStream}. The
	 * transformation calls a {@link FlatMapFunction} for each element of the
	 * DataStream. Each FlatMapFunction call can return any number of elements
	 * including none. The user can also extend {@link RichFlatMapFunction} to
	 * gain access to other features provided by the {@link RichFuntion}
	 * interface.
	 * 
	 * @param flatMapper
	 *            The FlatMapFunction that is called for each element of the
	 *            DataStream
	 * 
	 * @param <R>
	 *            output type
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> flatMap(FlatMapFunction<OUT, R> flatMapper) {
		FunctionTypeWrapper<OUT> inTypeWrapper = new FunctionTypeWrapper<OUT>(flatMapper,
				FlatMapFunction.class, 0);
		FunctionTypeWrapper<R> outTypeWrapper = new FunctionTypeWrapper<R>(flatMapper,
				FlatMapFunction.class, 1);

		return addFunction("flatMap", flatMapper, inTypeWrapper, outTypeWrapper,
				new FlatMapInvokable<OUT, R>(flatMapper));
	}

	/**
	 * Applies a reduce transformation on the data stream. The user can also
	 * extend the {@link RichReduceFunction} to gain access to other features
	 * provided by the {@link RichFuntion} interface.
	 * 
	 * @param reducer
	 *            The {@link ReduceFunction} that will be called for every
	 *            element of the input values.
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> reduce(ReduceFunction<OUT> reducer) {
		return addFunction("reduce", reducer, new FunctionTypeWrapper<OUT>(reducer,
				ReduceFunction.class, 0), new FunctionTypeWrapper<OUT>(reducer,
				ReduceFunction.class, 0), new StreamReduceInvokable<OUT>(reducer));
	}

	public GroupedDataStream<OUT> groupBy(int keyPosition) {
		return new GroupedDataStream<OUT>(this, keyPosition);
	}

	/**
	 * Applies a reduce transformation on preset chunks of the DataStream. The
	 * transformation calls a {@link GroupReduceFunction} for each tuple batch
	 * of the predefined size. Each GroupReduceFunction call can return any
	 * number of elements including none. The user can also extend
	 * {@link RichGroupReduceFunction} to gain access to other features provided
	 * by the {@link RichFuntion} interface.
	 * 
	 * 
	 * @param reducer
	 *            The GroupReduceFunction that is called for each tuple batch.
	 * @param batchSize
	 *            The number of tuples grouped together in the batch.
	 * @param <R>
	 *            output type
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> batchReduce(GroupReduceFunction<OUT, R> reducer,
			long batchSize) {
		return batchReduce(reducer, batchSize, batchSize);
	}

	/**
	 * Applies a reduce transformation on preset sliding chunks of the
	 * DataStream. The transformation calls a {@link GroupReduceFunction} for
	 * each tuple batch of the predefined size. The tuple batch gets slid by the
	 * given number of tuples. Each GroupReduceFunction call can return any
	 * number of elements including none. The user can also extend
	 * {@link RichGroupReduceFunction} to gain access to other features provided
	 * by the {@link RichFuntion} interface.
	 * 
	 * 
	 * @param reducer
	 *            The GroupReduceFunction that is called for each tuple batch.
	 * @param batchSize
	 *            The number of tuples grouped together in the batch.
	 * @param slideSize
	 *            The number of tuples the batch is slid by.
	 * @param <R>
	 *            output type
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> batchReduce(GroupReduceFunction<OUT, R> reducer,
			long batchSize, long slideSize) {
		if (batchSize < 1) {
			throw new IllegalArgumentException("Batch size must be positive");
		}
		if (slideSize < 1) {
			throw new IllegalArgumentException("Slide size must be positive");
		}

		FunctionTypeWrapper<OUT> inTypeWrapper = new FunctionTypeWrapper<OUT>(reducer,
				GroupReduceFunction.class, 0);
		FunctionTypeWrapper<R> outTypeWrapper = new FunctionTypeWrapper<R>(reducer,
				GroupReduceFunction.class, 1);

		return addFunction("batchReduce", reducer, inTypeWrapper, outTypeWrapper,
				new BatchGroupReduceInvokable<OUT, R>(reducer, batchSize, slideSize));
	}

	/**
	 * Applies a reduce transformation on preset "time" chunks of the
	 * DataStream. The transformation calls a {@link GroupReduceFunction} on
	 * records received during the predefined time window. The window is shifted
	 * after each reduce call. Each GroupReduceFunction call can return any
	 * number of elements including none.The user can also extend
	 * {@link RichGroupReduceFunction} to gain access to other features provided
	 * by the {@link RichFuntion} interface.
	 * 
	 * 
	 * @param reducer
	 *            The GroupReduceFunction that is called for each time window.
	 * @param windowSize
	 *            SingleOutputStreamOperator The time window to run the reducer
	 *            on, in milliseconds.
	 * @param <R>
	 *            output type
	 * @return The transformed DataStream.
	 */
	public <R> SingleOutputStreamOperator<R, ?> windowReduce(GroupReduceFunction<OUT, R> reducer,
			long windowSize) {
		return windowReduce(reducer, windowSize, windowSize);
	}

	/**
	 * Applies a reduce transformation on preset "time" chunks of the
	 * DataStream. The transformation calls a {@link GroupReduceFunction} on
	 * records received during the predefined time window. The window is shifted
	 * after each reduce call. Each GroupReduceFunction call can return any
	 * number of elements including none.The user can also extend
	 * {@link RichGroupReduceFunction} to gain access to other features provided
	 * by the {@link RichFuntion} interface.
	 * 
	 * 
	 * @param reducer
	 *            The GroupReduceFunction that is called for each time window.
	 * @param windowSize
	 *            SingleOutputStreamOperator The time window to run the reducer
	 *            on, in milliseconds.
	 * @param slideInterval
	 *            The time interval, batch is slid by.
	 * @param <R>
	 *            output type
	 * @return The transformed DataStream.
	 */
	public <R> SingleOutputStreamOperator<R, ?> windowReduce(GroupReduceFunction<OUT, R> reducer,
			long windowSize, long slideInterval) {
		return windowReduce(reducer, windowSize, slideInterval, new DefaultTimestamp<OUT>());
	}

	/**
	 * Applies a reduce transformation on preset "time" chunks of the
	 * DataStream. The transformation calls a {@link GroupReduceFunction} on
	 * records received during the predefined time window. The window is shifted
	 * after each reduce call. Each GroupReduceFunction call can return any
	 * number of elements including none. The time is determined by a
	 * user-defined timestamp. The user can also extend
	 * {@link RichGroupReduceFunction} to gain access to other features provided
	 * by the {@link RichFuntion} interface.
	 * 
	 * 
	 * @param reducer
	 *            The GroupReduceFunction that is called for each time window.
	 * @param windowSize
	 *            SingleOutputStreamOperator The time window to run the reducer
	 *            on, in milliseconds.
	 * @param slideInterval
	 *            The time interval, batch is slid by.
	 * @param timestamp
	 *            Timestamp function to retrieve a timestamp from an element.
	 * @param <R>
	 *            output type
	 * @return The transformed DataStream.
	 */
	public <R> SingleOutputStreamOperator<R, ?> windowReduce(GroupReduceFunction<OUT, R> reducer,
			long windowSize, long slideInterval, Timestamp<OUT> timestamp) {
		if (windowSize < 1) {
			throw new IllegalArgumentException("Window size must be positive");
		}
		if (slideInterval < 1) {
			throw new IllegalArgumentException("Slide interval must be positive");
		}

		FunctionTypeWrapper<OUT> inTypeWrapper = new FunctionTypeWrapper<OUT>(reducer,
				GroupReduceFunction.class, 0);
		FunctionTypeWrapper<R> outTypeWrapper = new FunctionTypeWrapper<R>(reducer,
				GroupReduceFunction.class, 1);

		return addFunction("batchReduce", reducer, inTypeWrapper, outTypeWrapper,
				new WindowGroupReduceInvokable<OUT, R>(reducer, windowSize, slideInterval, timestamp));
	}

	/**
	 * Applies an aggregation that sums the data stream at the given position.
	 * 
	 * @param positionToSum
	 *            The position in the data point to sum
	 * @return The transformed DataStream.
	 */
	@SuppressWarnings("unchecked")
	public SingleOutputStreamOperator<OUT, ?> sum(int positionToSum) {
		checkFieldRange(positionToSum);
		return aggregate((AggregationFunction<OUT>) SumAggregationFunction.getSumFunction(
				positionToSum, getClassAtPos(positionToSum)));
	}

	/**
	 * Syntactic sugar for sum(0)
	 * 
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> sum() {
		return sum(0);
	}

	/**
	 * Applies an aggregation that that gives the minimum of the data stream at
	 * the given position.
	 * 
	 * @param positionToMin
	 *            The position in the data point to minimize
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> min(int positionToMin) {
		checkFieldRange(positionToMin);
		return aggregate(new MinAggregationFunction<OUT>(positionToMin));
	}

	/**
	 * Syntactic sugar for min(0)
	 * 
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> min() {
		return min(0);
	}

	/**
	 * Applies an aggregation that gives the maximum of the data stream at the
	 * given position.
	 * 
	 * @param positionToMax
	 *            The position in the data point to maximize
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> max(int positionToMax) {
		checkFieldRange(positionToMax);
		return aggregate(new MaxAggregationFunction<OUT>(positionToMax));
	}

	/**
	 * Syntactic sugar for max(0)
	 * 
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> max() {
		return max(0);
	}

	protected SingleOutputStreamOperator<OUT, ?> aggregate(AggregationFunction<OUT> aggregate) {

		StreamReduceInvokable<OUT> invokable = new StreamReduceInvokable<OUT>(aggregate);

		SingleOutputStreamOperator<OUT, ?> returnStream = addFunction("reduce", aggregate, null,
				null, invokable);

		this.jobGraphBuilder.setTypeWrappersFrom(getId(), returnStream.getId());
		return returnStream;
	}

	/**
	 * Applies a Filter transformation on a {@link DataStream}. The
	 * transformation calls a {@link FilterFunction} for each element of the
	 * DataStream and retains only those element for which the function returns
	 * true. Elements for which the function returns false are filtered. The
	 * user can also extend {@link RichFilterFunction} to gain access to other
	 * features provided by the {@link RichFuntion} interface.
	 * 
	 * @param filter
	 *            The FilterFunction that is called for each element of the
	 *            DataSet.
	 * @return The filtered DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> filter(FilterFunction<OUT> filter) {
		FunctionTypeWrapper<OUT> typeWrapper = new FunctionTypeWrapper<OUT>(filter,
				FilterFunction.class, 0);

		return addFunction("filter", filter, typeWrapper, typeWrapper, new FilterInvokable<OUT>(
				filter));
	}

	/**
	 * Writes a DataStream to the standard output stream (stdout). For each
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @return The closed DataStream.
	 */
	public DataStreamSink<OUT> print() {
		DataStream<OUT> inputStream = this.copy();
		PrintSinkFunction<OUT> printFunction = new PrintSinkFunction<OUT>();
		DataStreamSink<OUT> returnStream = addSink(inputStream, printFunction, null);

		jobGraphBuilder.setInToOutTypeWrappersFrom(inputStream.getId(), returnStream.getId());

		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsText(String path) {
		return writeAsText(this, path, new WriteFormatAsText<OUT>(), 1, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsText(String path, long millis) {
		return writeAsText(this, path, new WriteFormatAsText<OUT>(), millis, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsText(String path, int batchSize) {
		return writeAsText(this, path, new WriteFormatAsText<OUT>(), batchSize, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsText(String path, long millis, OUT endTuple) {
		return writeAsText(this, path, new WriteFormatAsText<OUT>(), millis, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsText(String path, int batchSize, OUT endTuple) {
		return writeAsText(this, path, new WriteFormatAsText<OUT>(), batchSize, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return the data stream constructed
	 */
	private DataStreamSink<OUT> writeAsText(DataStream<OUT> inputStream, String path,
			WriteFormatAsText<OUT> format, long millis, OUT endTuple) {
		DataStreamSink<OUT> returnStream = addSink(inputStream, new WriteSinkFunctionByMillis<OUT>(
				path, format, millis, endTuple), null);
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return the data stream constructed
	 */
	private DataStreamSink<OUT> writeAsText(DataStream<OUT> inputStream, String path,
			WriteFormatAsText<OUT> format, int batchSize, OUT endTuple) {
		DataStreamSink<OUT> returnStream = addSink(inputStream,
				new WriteSinkFunctionByBatches<OUT>(path, format, batchSize, endTuple), null);
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsCsv(String path) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<OUT>(), 1, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsCsv(String path, long millis) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<OUT>(), millis, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsCsv(String path, int batchSize) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<OUT>(), batchSize, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsCsv(String path, long millis, OUT endTuple) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<OUT>(), millis, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return The closed DataStream
	 */
	public DataStreamSink<OUT> writeAsCsv(String path, int batchSize, OUT endTuple) {
		if (this instanceof SingleOutputStreamOperator) {
			((SingleOutputStreamOperator<?, ?>) this).setMutability(false);
		}
		return writeAsCsv(this, path, new WriteFormatAsCsv<OUT>(), batchSize, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return the data stream constructed
	 */
	private DataStreamSink<OUT> writeAsCsv(DataStream<OUT> inputStream, String path,
			WriteFormatAsCsv<OUT> format, long millis, OUT endTuple) {
		DataStreamSink<OUT> returnStream = addSink(inputStream, new WriteSinkFunctionByMillis<OUT>(
				path, format, millis, endTuple));
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return the data stream constructed
	 */
	private DataStreamSink<OUT> writeAsCsv(DataStream<OUT> inputStream, String path,
			WriteFormatAsCsv<OUT> format, int batchSize, OUT endTuple) {
		DataStreamSink<OUT> returnStream = addSink(inputStream,
				new WriteSinkFunctionByBatches<OUT>(path, format, batchSize, endTuple), null);
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Initiates an iterative part of the program that executes multiple times
	 * and feeds back data streams. The iterative part needs to be closed by
	 * calling {@link IterativeDataStream#closeWith(DataStream)}. The
	 * transformation of this IterativeDataStream will be the iteration head.
	 * The data stream given to the {@code closeWith(DataStream)} method is the
	 * data stream that will be fed back and used as the input for the iteration
	 * head. Unlike in batch processing by default the output of the iteration
	 * stream is directed to both to the iteration head and the next component.
	 * To direct tuples to the iteration head or the output specifically one can
	 * use the {@code split(OutputSelector)} on the iteration tail while
	 * referencing the iteration head as 'iterate'.
	 * <p>
	 * The iteration edge will be partitioned the same way as the first input of
	 * the iteration head.
	 * <p>
	 * By default a DataStream with iteration will never terminate, but the user
	 * can use the {@link IterativeDataStream#setMaxWaitTime} call to set a max
	 * waiting time for the iteration.
	 * 
	 * @return The iterative data stream created.
	 */
	public IterativeDataStream<OUT> iterate() {
		return new IterativeDataStream<OUT>(this);
	}

	protected <R> DataStream<OUT> addIterationSource(String iterationID, long waitTime) {

		DataStream<R> returnStream = new DataStreamSource<R>(environment, "iterationSource", null);

		jobGraphBuilder.addIterationSource(returnStream.getId(), this.getId(), iterationID,
				degreeOfParallelism, waitTime);

		return this.copy();
	}

	/**
	 * Internal function for passing the user defined functions to the JobGraph
	 * of the job.
	 * 
	 * @param functionName
	 *            name of the function
	 * @param function
	 *            the user defined function
	 * @param functionInvokable
	 *            the wrapping JobVertex instance
	 * @param <R>
	 *            type of the return stream
	 * @return the data stream constructed
	 */
	protected <R> SingleOutputStreamOperator<R, ?> addFunction(String functionName,
			final Function function, TypeSerializerWrapper<OUT> inTypeWrapper,
			TypeSerializerWrapper<R> outTypeWrapper,
			StreamOperatorInvokable<OUT, R> functionInvokable) {
		DataStream<OUT> inputStream = this.copy();
		@SuppressWarnings({ "unchecked", "rawtypes" })
		SingleOutputStreamOperator<R, ?> returnStream = new SingleOutputStreamOperator(environment,
				functionName, outTypeWrapper);

		try {
			jobGraphBuilder.addTask(returnStream.getId(), functionInvokable, inTypeWrapper,
					outTypeWrapper, functionName,
					SerializationUtils.serialize((Serializable) function), degreeOfParallelism);
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot serialize user defined function");
		}

		connectGraph(inputStream, returnStream.getId(), 0);

		if (inputStream instanceof IterativeDataStream) {
			IterativeDataStream<OUT> iterativeStream = (IterativeDataStream<OUT>) inputStream;
			returnStream.addIterationSource(iterativeStream.iterationID.toString(),
					iterativeStream.waitTime);
		}

		return returnStream;
	}

	/**
	 * Internal function for setting the partitioner for the DataStream
	 * 
	 * @param partitioner
	 *            Partitioner to set.
	 * @return The modified DataStream.
	 */
	protected DataStream<OUT> setConnectionType(StreamPartitioner<OUT> partitioner) {
		DataStream<OUT> returnStream = this.copy();

		for (DataStream<OUT> stream : returnStream.mergedStreams) {
			stream.partitioner = partitioner;
		}

		return returnStream;
	}

	/**
	 * Internal function for assembling the underlying
	 * {@link org.apache.flink.nephele.jobgraph.JobGraph} of the job. Connects
	 * the outputs of the given input stream to the specified output stream
	 * given by the outputID.
	 * 
	 * @param inputStream
	 *            input data stream
	 * @param outputID
	 *            ID of the output
	 * @param typeNumber
	 *            Number of the type (used at co-functions)
	 */
	protected <X> void connectGraph(DataStream<X> inputStream, String outputID, int typeNumber) {
		for (DataStream<X> stream : inputStream.mergedStreams) {
			jobGraphBuilder.setEdge(stream.getId(), outputID, stream.partitioner, typeNumber,
					inputStream.userDefinedNames, inputStream.selectAll);
		}

	}

	/**
	 * Adds the given sink to this DataStream. Only streams with sinks added
	 * will be executed once the {@link StreamExecutionEnvironment#execute()}
	 * method is called.
	 * 
	 * @param sinkFunction
	 *            The object containing the sink's invoke function.
	 * @return The closed DataStream.
	 */
	public DataStreamSink<OUT> addSink(SinkFunction<OUT> sinkFunction) {
		return addSink(this.copy(), sinkFunction);
	}

	private DataStreamSink<OUT> addSink(DataStream<OUT> inputStream, SinkFunction<OUT> sinkFunction) {
		return addSink(inputStream, sinkFunction, new FunctionTypeWrapper<OUT>(sinkFunction,
				SinkFunction.class, 0));
	}

	private DataStreamSink<OUT> addSink(DataStream<OUT> inputStream,
			SinkFunction<OUT> sinkFunction, TypeSerializerWrapper<OUT> typeWrapper) {
		DataStreamSink<OUT> returnStream = new DataStreamSink<OUT>(environment, "sink",
				outTypeWrapper);

		try {
			jobGraphBuilder.addSink(returnStream.getId(), new SinkInvokable<OUT>(sinkFunction),
					typeWrapper, "sink", SerializationUtils.serialize(sinkFunction),
					degreeOfParallelism);
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot serialize SinkFunction");
		}

		inputStream.connectGraph(inputStream.copy(), returnStream.getId(), 0);

		return returnStream;
	}

	/**
	 * Creates a copy of the {@link DataStream}
	 * 
	 * @return The copy
	 */
	protected DataStream<OUT> copy(){
		return new DataStream<OUT>(this);
	}

}