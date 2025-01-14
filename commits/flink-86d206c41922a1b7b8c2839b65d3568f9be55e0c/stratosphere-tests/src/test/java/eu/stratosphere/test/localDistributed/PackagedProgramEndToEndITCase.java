/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
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
package eu.stratosphere.test.localDistributed;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;

import eu.stratosphere.client.minicluster.NepheleMiniCluster;
import org.junit.Assert;
import org.junit.Test;

import eu.stratosphere.client.RemoteExecutor;
import eu.stratosphere.test.testdata.KMeansData;
import eu.stratosphere.util.LogUtils;

// When the API changes KMeansForTest needs to be rebuilt and the KMeansForTest.jar in resources needs
// to be replaced with the new one.

public class PackagedProgramEndToEndITCase {

	private static final int DOP = 4;

	static {
		LogUtils.initializeDefaultTestConsoleLogger();
	}
	
	@Test
	public void testEverything() {
		NepheleMiniCluster cluster = new NepheleMiniCluster();
		try {
			// set up the files
			File points = File.createTempFile("kmeans_points", ".in");
			File clusters = File.createTempFile("kmeans_clusters", ".in");
			File outFile = File.createTempFile("kmeans_result", ".out");
			points.deleteOnExit();
			clusters.deleteOnExit();
			outFile.deleteOnExit();
			outFile.delete();

			FileWriter fwPoints = new FileWriter(points);
			fwPoints.write(KMeansData.DATAPOINTS);
			fwPoints.close();

			FileWriter fwClusters = new FileWriter(clusters);
			fwClusters.write(KMeansData.INITIAL_CENTERS);
			fwClusters.close();

			URL jarFileURL = getClass().getResource("/KMeansForTest.jar");
			String jarPath = jarFileURL.getFile();

			// run KMeans
			cluster.setNumTaskTracker(2);
			cluster.setTaskManagerNumSlots(2);
			cluster.start();
			RemoteExecutor ex = new RemoteExecutor("localhost", 6498);

			ex.executeJar(jarPath,
					"eu.stratosphere.examples.scala.testing.KMeansForTest",
					new String[] {new Integer(DOP).toString(),
							points.toURI().toString(),
							clusters.toURI().toString(),
							outFile.toURI().toString(),
							"25"});

			points.delete();
			clusters.delete();
			outFile.delete();

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		} finally {
			try {
				cluster.stop();
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail(e.getMessage());
			}
		}
	}
}
