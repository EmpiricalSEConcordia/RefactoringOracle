// $Id$
package org.hibernate.search.test.reader.performance;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.hibernate.search.Environment;
import org.hibernate.search.store.FSDirectoryProvider;
import org.hibernate.search.test.SearchTestCase;
import org.hibernate.search.test.reader.Detective;
import org.hibernate.search.test.reader.Suspect;
import org.hibernate.search.util.FileHelper;

/**
 * To enable performance tests: de-comment buildBigIndex(); in setUp() and rename no_testPerformance
 * @author Sanne Grinovero
 */
public abstract class ReaderPerformance extends SearchTestCase {
		
	//more iterations for more reliable measures:
	private static final int TOTAL_WORK_BATCHES = 10;
	//the next 3 define the kind of workload mix to test on:
	private static final int SEARCHERS_PER_BATCH = 10;
	private static final int UPDATES_PER_BATCH = 2;
	private static final int INSERTIONS_PER_BATCH = 1;

	private static final int WORKER_THREADS = 20;

	private static final int WARMUP_CYCLES = 6;
	
	protected void setUp() throws Exception {
		File baseIndexDir = getBaseIndexDir();
		baseIndexDir.mkdir();
		File[] files = baseIndexDir.listFiles();
		for ( File file : files ) {
			FileHelper.delete( file );
		}
		super.setUp();
	}
	
	public void testFakeTest(){
		//to make JUnit happy when disabling performance test
	}

	private void buildBigIndex() throws InterruptedException, CorruptIndexException, LockObtainFailedException, IOException {
		System.out.println( "Going to create fake index..." );
		FSDirectory directory = FSDirectory.getDirectory(new File(getBaseIndexDir(), Detective.class.getCanonicalName()));
		IndexWriter.MaxFieldLength fieldLength = new IndexWriter.MaxFieldLength( IndexWriter.DEFAULT_MAX_FIELD_LENGTH );
		IndexWriter iw = new IndexWriter( directory, new SimpleAnalyzer(), true, fieldLength );
		IndexFillRunnable filler = new IndexFillRunnable( iw );
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool( WORKER_THREADS );
		for (int batch=0; batch<=5000000; batch++){
			executor.execute( filler );
		}
		executor.shutdown();
		executor.awaitTermination( 600, TimeUnit.SECONDS );
		iw.commit();
		iw.optimize();
		iw.close();
		System.out.println( "Index created." );
	}

	@SuppressWarnings("unchecked")
	protected Class[] getMappings() {
		return new Class[] {
				Detective.class,
				Suspect.class
		};
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		FileHelper.delete( getBaseIndexDir() );
	}
	
	protected void configure(org.hibernate.cfg.Configuration cfg) {
		super.configure( cfg );
		cfg.setProperty( "hibernate.search.default.directory_provider", FSDirectoryProvider.class.getName() );
		cfg.setProperty( "hibernate.search.default.indexBase", getBaseIndexDir().getAbsolutePath() );
		cfg.setProperty( "hibernate.search.default.optimizer.transaction_limit.max", "10" ); // workaround too many open files
		cfg.setProperty( Environment.ANALYZER_CLASS, StopAnalyzer.class.getName() );
		cfg.setProperty( Environment.READER_STRATEGY, getReaderStrategyName() );
	}

	protected abstract String getReaderStrategyName();
	
	//this test is disabled as it is very slow (and someone should read the output)
	public final void disabled_testPerformance() throws InterruptedException, CorruptIndexException, LockObtainFailedException, IOException {
		buildBigIndex();
		for (int i=0; i<WARMUP_CYCLES; i++) {
			timeMs();
		}
	}
	
	private final void timeMs() throws InterruptedException {
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool( WORKER_THREADS );
		CountDownLatch startSignal = new CountDownLatch(1);
		InsertActivity insertionTask = new InsertActivity( getSessions(), startSignal );
		SearchActivity searchTask = new SearchActivity( getSessions(), startSignal );
		UpdateActivity updateTask = new UpdateActivity( getSessions(), startSignal );
		//we declare needed activities in order, scheduler will "mix":
		for (int batch=0; batch<=TOTAL_WORK_BATCHES; batch++){
			for ( int inserters=0; inserters<INSERTIONS_PER_BATCH; inserters++)
				executor.execute( insertionTask );
			for ( int searchers=0; searchers<SEARCHERS_PER_BATCH; searchers++)
				executor.execute( searchTask );
			for ( int updaters=0; updaters<UPDATES_PER_BATCH; updaters++)
				executor.execute( updateTask );
		}
		executor.shutdown();
		long startTime = System.currentTimeMillis();
		startSignal.countDown();//start!
		executor.awaitTermination( 600, TimeUnit.SECONDS );
		long endTime = System.currentTimeMillis();
		System.out.println( "Performance test for " + getReaderStrategyName() + ": " + (endTime - startTime) +"ms. (" + 
				(TOTAL_WORK_BATCHES*SEARCHERS_PER_BATCH) + " searches, " + 
				(TOTAL_WORK_BATCHES*INSERTIONS_PER_BATCH) + " insertions, " + 
				(TOTAL_WORK_BATCHES*UPDATES_PER_BATCH) + " updates)" );
	}
	
}
