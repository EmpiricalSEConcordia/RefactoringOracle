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

package eu.stratosphere.client.program;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.List;

import eu.stratosphere.api.Job;
import eu.stratosphere.compiler.CompilerException;
import eu.stratosphere.compiler.DataStatistics;
import eu.stratosphere.compiler.PactCompiler;
import eu.stratosphere.compiler.contextcheck.ContextChecker;
import eu.stratosphere.compiler.costs.DefaultCostEstimator;
import eu.stratosphere.compiler.plan.OptimizedPlan;
import eu.stratosphere.compiler.plandump.PlanJSONDumpGenerator;
import eu.stratosphere.compiler.plantranslate.NepheleJobGraphGenerator;
import eu.stratosphere.configuration.ConfigConstants;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.configuration.GlobalConfiguration;
import eu.stratosphere.core.fs.Path;
import eu.stratosphere.nephele.client.AbstractJobResult.ReturnCode;
import eu.stratosphere.nephele.client.JobExecutionResult;
import eu.stratosphere.nephele.client.JobClient;
import eu.stratosphere.nephele.client.JobExecutionException;
import eu.stratosphere.nephele.client.JobSubmissionResult;
import eu.stratosphere.nephele.jobgraph.JobGraph;

/**
 * Encapsulates the functionality necessary to compile and submit a pact program to a nephele cluster.
 */
public class Client {
	
	private final Configuration nepheleConfig;	// the configuration describing the job manager address
	
	private final PactCompiler compiler;		// the compiler to compile the jobs

	// ------------------------------------------------------------------------
	//                            Construction
	// ------------------------------------------------------------------------
	
	/**
	 * Creates a new instance of the class that submits the jobs to a nephele job-manager.
	 * at the given address using the default port.
	 * 
	 * @param jobManagerAddress Address and port of the job-manager.
	 */
	public Client(InetSocketAddress jobManagerAddress, Configuration config) {
		this.nepheleConfig = config;
		nepheleConfig.setString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, jobManagerAddress.getAddress().getHostAddress());
		nepheleConfig.setInteger(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY, jobManagerAddress.getPort());
		
		this.compiler = new PactCompiler(new DataStatistics(), new DefaultCostEstimator(), jobManagerAddress);
	}

	/**
	 * Creates a instance that submits the pact programs to the job-manager defined in the
	 * configuration.
	 * 
	 * @param nepheleConfig The config used to obtain the job-manager's address.
	 */
	public Client(Configuration nepheleConfig) {
		this.nepheleConfig = nepheleConfig;
		
		// instantiate the address to the job manager
		final String address = nepheleConfig.getString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, null);
		if (address == null) {
			throw new CompilerException("Cannot find address to job manager's RPC service in the global configuration.");
		}
		
		final int port = GlobalConfiguration.getInteger(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY, ConfigConstants.DEFAULT_JOB_MANAGER_IPC_PORT);
		if (port < 0) {
			throw new CompilerException("Cannot find port to job manager's RPC service in the global configuration.");
		}

		final InetSocketAddress jobManagerAddress = new InetSocketAddress(address, port);
		this.compiler = new PactCompiler(new DataStatistics(), new DefaultCostEstimator(), jobManagerAddress);
	}

	
	// ------------------------------------------------------------------------
	//                      Compilation and Submission
	// ------------------------------------------------------------------------
	
	/**
	 * Creates the optimized plan for a given pact program, using this client's compiler.
	 *  
	 * @param prog The program to be compiled.
	 * @return The compiled and optimized plan, as returned by the compiler.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file.
	 * @throws JobInstantiationException Thrown, if the plan assembler function causes an exception.
	 */
	public OptimizedPlan getOptimizedPlan(JobWithJars prog) throws CompilerException, ProgramInvocationException, JobInstantiationException {
		Job plan = prog.getPlan();
		ContextChecker checker = new ContextChecker();
		checker.check(plan);
		return this.compiler.compile(plan);
	}
	
	/**
	 * Optimizes a given PACT program and returns the optimized plan as JSON string.
	 * 
	 * @param prog The PACT program to be compiled to JSON.
	 * @return A JSON string representation of the optimized input plan.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file.
	 * @throws JobInstantiationException Thrown, if the plan assembler function causes an exception.
	 */
	public static String getPreviewAsJSON(PackagedProgram prog) throws CompilerException, ProgramInvocationException, JobInstantiationException {
		StringWriter string = new StringWriter(1024);
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(string);
			dumpPreviewAsJSON(prog, pw);
		} finally {
			pw.close();
		}
		return string.toString();
	}
	
	/**
	 * Optimizes a given PACT program and returns the optimized plan as JSON string.
	 * 
	 * @param prog The PACT program to be compiled to JSON.
	 * @return A JSON string representation of the optimized input plan.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file.
	 * @throws JobInstantiationException Thrown, if the plan assembler function causes an exception.
	 */
	public static void dumpPreviewAsJSON(PackagedProgram prog, PrintWriter out) throws CompilerException, ProgramInvocationException, JobInstantiationException {
		PlanJSONDumpGenerator jsonGen = new PlanJSONDumpGenerator();
		jsonGen.dumpPactPlanAsJSON(prog.getPreviewPlan(), out);
	}
	
	/**
	 * Optimizes a given PACT program and returns the optimized plan as JSON string.
	 * 
	 * @param prog The PACT program to be compiled to JSON.
	 * @return A JSON string representation of the optimized input plan.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file.
	 * @throws JobInstantiationException Thrown, if the plan assembler function causes an exception.
	 */
	public String getOptimizerPlanAsJSON(JobWithJars prog) throws CompilerException, ProgramInvocationException, JobInstantiationException {
		StringWriter string = new StringWriter(1024);
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(string);
			dumpOptimizerPlanAsJSON(prog, pw);
		} finally {
			pw.close();
		}
		return string.toString();
	}
	
	/**
	 * Optimizes a given PACT program and returns the optimized plan as JSON string.
	 * 
	 * @param prog The PACT program to be compiled to JSON.
	 * @return A JSON string representation of the optimized input plan.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file.
	 * @throws JobInstantiationException Thrown, if the plan assembler function causes an exception.
	 */
	public void dumpOptimizerPlanAsJSON(JobWithJars prog, PrintWriter out) throws CompilerException, ProgramInvocationException, JobInstantiationException {
		PlanJSONDumpGenerator jsonGen = new PlanJSONDumpGenerator();
		jsonGen.dumpOptimizerPlanAsJSON(getOptimizedPlan(prog), out);
	}
	
	
	/**
	 * Creates the job-graph, which is ready for submission, from a compiled and optimized pact program.
	 * The original pact-program is required to access the original jar file.
	 * 
	 * @param prog The original pact program.
	 * @param optPlan The optimized plan.
	 * @return The nephele job graph, generated from the optimized plan.
	 */
	public JobGraph getJobGraph(JobWithJars prog, OptimizedPlan optPlan) throws ProgramInvocationException {
		NepheleJobGraphGenerator gen = new NepheleJobGraphGenerator();
		JobGraph job = gen.compileJobGraph(optPlan);
		
		
		try {
			List<File> jarFiles = prog.getJarFiles();

			for (File jar : jarFiles) {
				job.addJar(new Path(jar.getAbsolutePath()));
			}
		}
		catch (IOException ioex) {
			throw new ProgramInvocationException("Could not extract the nested libraries: " + ioex.getMessage(), ioex);
		}
		
		return job;
	}
	
	
	/**
	 * Runs a pact program on the nephele system whose job-manager is configured in this client's configuration.
	 * This method involves all steps, from compiling, job-graph generation to submission.
	 * 
	 * @param prog The program to be executed.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file,
	 *                                    or if the submission failed. That might be either due to an I/O problem,
	 *                                    i.e. the job-manager is unreachable, or due to the fact that the execution
	 *                                    on the nephele system failed.
	 * @throws JobInstantiationException Thrown, if the plan assembler function causes an exception.
	 */
	public JobExecutionResult run(JobWithJars prog) throws CompilerException, ProgramInvocationException, JobInstantiationException {
		return run(prog, false);
	}
	
	/**
	 * Runs a pact program on the nephele system whose job-manager is configured in this client's configuration.
	 * This method involves all steps, from compiling, job-graph generation to submission.
	 * 
	 * @param prog The program to be executed.
	 * @param wait A flag that indicates whether this function call should block until the program execution is done.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file,
	 *                                    or if the submission failed. That might be either due to an I/O problem,
	 *                                    i.e. the job-manager is unreachable, or due to the fact that the execution
	 *                                    on the nephele system failed.
	 * @throws JobInstantiationException Thrown, if the plan assembler function causes an exception.
	 */
	public JobExecutionResult run(JobWithJars prog, boolean wait) throws CompilerException, ProgramInvocationException, JobInstantiationException {
		return run(prog, getOptimizedPlan(prog), wait);
	}
	
	/**
	 * Submits the given program to the nephele job-manager for execution. The first step of teh compilation process is skipped and
	 * the given compiled plan is taken.
	 * 
	 * @param prog The original pact program.
	 * @param compiledPlan The optimized plan.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file,
	 *                                    or if the submission failed. That might be either due to an I/O problem,
	 *                                    i.e. the job-manager is unreachable, or due to the fact that the execution
	 *                                    on the nephele system failed.
	 */
	public JobExecutionResult run(JobWithJars prog, OptimizedPlan compiledPlan) throws ProgramInvocationException {
		return run(prog, compiledPlan, false);
	}
	
	/**
	 * Submits the given program to the nephele job-manager for execution. The first step of the compilation process is skipped and
	 * the given compiled plan is taken.
	 * 
	 * @param prog The original pact program.
	 * @param compiledPlan The optimized plan.
	 * @param wait A flag that indicates whether this function call should block until the program execution is done.
	 * @throws ProgramInvocationException Thrown, if the pact program could not be instantiated from its jar file,
	 *                                    or if the submission failed. That might be either due to an I/O problem,
	 *                                    i.e. the job-manager is unreachable, or due to the fact that the execution
	 *                                    on the nephele system failed.
	 */
	public JobExecutionResult run(JobWithJars prog, OptimizedPlan compiledPlan, boolean wait) throws ProgramInvocationException {
		JobGraph job = getJobGraph(prog, compiledPlan);
		return run(prog, job, wait);
	}

	/**
	 * Submits the job-graph to the nephele job-manager for execution.
	 * 
	 * @param prog The program to be submitted.
	 * @throws ProgramInvocationException Thrown, if the submission failed. That might be either due to an I/O problem,
	 *                                    i.e. the job-manager is unreachable, or due to the fact that the execution
	 *                                    on the nephele system failed.
	 */
	public JobExecutionResult run(JobWithJars program, JobGraph jobGraph) throws ProgramInvocationException {
		return run(program, jobGraph, false);
	}
	/**
	 * Submits the job-graph to the nephele job-manager for execution.
	 * 
	 * @param prog The program to be submitted.
	 * @param wait Method will block until the job execution is finished if set to true. 
	 *               If set to false, the method will directly return after the job is submitted. 
	 * @throws ProgramInvocationException Thrown, if the submission failed. That might be either due to an I/O problem,
	 *                                    i.e. the job-manager is unreachable, or due to the fact that the execution
	 *                                    on the nephele system failed.
	 */
	public JobExecutionResult run(JobWithJars program, JobGraph jobGraph, boolean wait) throws ProgramInvocationException
	{
		JobClient client;
		try {
			client = new JobClient(jobGraph, nepheleConfig);
		}
		catch (IOException e) {
			throw new ProgramInvocationException("Could not open job manager: " + e.getMessage());
		}

		try {
			if (wait) {
				return client.submitJobAndWait();
			}
			else {
				JobSubmissionResult result = client.submitJob();
				
				if (result.getReturnCode() != ReturnCode.SUCCESS) {
					throw new ProgramInvocationException("The job was not successfully submitted to the nephele job manager"
						+ (result.getDescription() == null ? "." : ": " + result.getDescription()));
				}
			}
		}
		catch (IOException e) {
			throw new ProgramInvocationException("Could not submit job to job manager: " + e.getMessage());
		}
		catch (JobExecutionException jex) {
			if(jex.isJobCanceledByUser()) {
				throw new ProgramInvocationException("The program has been canceled");
			} else {
				throw new ProgramInvocationException("The program execution failed: " + jex.getMessage());
			}
		}
		return new JobExecutionResult(-1, null);
	}
}
