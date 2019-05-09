/*
 * Copyright [2017] [ACoLi Lab, Prof. Dr. Chiarcos, Goethe University Frankfurt]
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.apache.jena.atlas.logging.Log;
import org.apache.jena.ext.com.google.common.collect.Lists;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.listeners.ChangedListener;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.update.Update;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javafx.util.Pair;

/**
 *  @author Christian Chiarcos {@literal chiarcos@informatik.uni-frankfurt.de}
 *  @author Christian Faeth {@literal faeth@em.uni-frankfurt.de}
 */
public class CoNLLRDFUpdater extends CoNLLRDFComponent {
	
	@SuppressWarnings("serial")
	private static final List<Integer> CHECKINTERVAL = new ArrayList<Integer>() {{add(3); add(10); add(25); add(50); add(100); add(200); add(500);}};
	static final int MAXITERATE = 999; // maximum update iterations allowed until the update loop is cancelled and an error message is thrown - to prevent faulty update scripts running in an endless loop
	private static final Logger LOG = Logger.getLogger(CoNLLRDFUpdater.class.getName());
	private static final String DEFAULTUPDATENAME = "DIRECTUPDATE";
	
	public static class Triple<F, S, M> {
		public F first;
		public S second;
		public M third;
		public Triple (F first, S second, M third) {
			this.first = first;
			this.second = second;
			this.third = third;
		}
	}
	
	
	private class UpdateThread extends Thread {
		
		private CoNLLRDFUpdater updater;
		private int threadID;
		private Dataset memDataset;
		
		/**
		 * Each UpdateThread receives its own ID and a back-reference to the calling Updater.
		 * 
		 * In the current implementation, each thread manages its own in-memory Dataset.
		 * This is the fastest approach since no concurring access on a single Datasets occurs.
		 * However: lots of RAM may be needed.
		 * 
		 * @param updater
		 * 				The calling Updater (= ThreadHandler)
		 * @param id
		 * 				The id of this Thread.
		 */
		public UpdateThread(CoNLLRDFUpdater updater, int id) {
			this.updater = updater;
			threadID = id;
			memDataset = DatasetFactory.create();
			Iterator<String> iter = updater.dataset.listNames();
			while(iter.hasNext()) {
				String graph = iter.next();
				memDataset.addNamedModel(graph, updater.dataset.getNamedModel(graph));
			}
			memDataset.addNamedModel("https://github.com/acoli-repo/conll-rdf/lookback", ModelFactory.createDefaultModel());
			memDataset.addNamedModel("https://github.com/acoli-repo/conll-rdf/lookahead", ModelFactory.createDefaultModel());
		}
		
		/**
		 * Run the update thread.
		 * Load the buffer, execute the updates with all iterations and graphsout, unload the buffer.
		 */
		public void run() {
			while (updater.running) {
				//Execute Thread

				LOG.trace("NOW Processing on thread "+threadID+": outputbuffersize "+sentBufferOut.size());
				Triple<List<String>, String, List<String>> sentBufferThread = sentBufferThreads.get(threadID);
				StringWriter out = new StringWriter();
				try {
					loadBuffer(sentBufferThread);
					
					List<Pair<Integer,Long> > ret = executeUpdates(updates);
					if (dRTs.get(threadID).isEmpty())
						dRTs.get(threadID).addAll(ret);
					else
						for (int x = 0; x < ret.size(); ++x)
							dRTs.get(threadID).set(x, new Pair<Integer, Long>(
									dRTs.get(threadID).get(x).getKey() + ret.get(x).getKey(), 
									dRTs.get(threadID).get(x).getValue() + ret.get(x).getValue()));
					
					unloadBuffer(sentBufferThread, out);
				} catch (Exception e) {
//					memDataset.begin(ReadWrite.WRITE);
					memDataset.getDefaultModel().removeAll();
					memDataset.getNamedModel("https://github.com/acoli-repo/conll-rdf/lookback").removeAll();
					memDataset.getNamedModel("https://github.com/acoli-repo/conll-rdf/lookahead").removeAll();
//					memDataset.commit();
//					memDataset.end();
					e.printStackTrace();
//					continue;
				}

				// synchronized write access to sentBuffer in order to avoid corruption
				synchronized(updater) {
				LOG.trace("NOW PRINTING on thread "+threadID+": outputbuffersize "+sentBufferOut.size());
				for (int i = 0; i < sentBufferOut.size(); i++) {
					if (sentBufferOut.get(i).equals(String.valueOf(threadID))) {
						sentBufferOut.set(i, out.toString());
						break;
					}
				}				
				
				//go to sleep and let Updater take control
					LOG.trace("Updater notified by "+threadID);
					updater.notify();

				}
				try {
					synchronized (this) {
						LOG.trace("Waiting: "+threadID);
						wait();
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		/**
		 * Loads Data to this thread's working model.
		 * @param buffer 
		 * 			the model to be read.
		 * @throws Exception
		 */
		private void loadBuffer(Triple<List<String>, String, List<String>> sentBufferThread) throws Exception { //TODO: adjust for TXN-Models
			//check validity of current sentence
			isValidUTF8(sentBufferThread.second, "Input data encoding issue for \"" + sentBufferThread.second + "\"");
			//load ALL
			try {
//				memDataset.begin(ReadWrite.WRITE);
				
				// for lookback
				for (String sent:sentBufferThread.first) {
					memDataset.getNamedModel("https://github.com/acoli-repo/conll-rdf/lookback").read(new StringReader(sent),null, "TTL");
				}
				
				// for current sentence
				memDataset.getDefaultModel().read(new StringReader(sentBufferThread.second),null, "TTL");
				
				// for lookahead
				for (String sent:sentBufferThread.third) {
					memDataset.getNamedModel("https://github.com/acoli-repo/conll-rdf/lookahead").read(new StringReader(sent),null, "TTL");
				}
				
//				memDataset.commit();
//				Model m = ModelFactory.createDefaultModel().read(new StringReader(buffer),null, "TTL");
//				memAccessor.add(m);
//				memDataset.getDefaultModel().setNsPrefixes(m.getNsPrefixMap());
			} catch (Exception ex) {
				LOG.error("Exception while reading: " + sentBufferThread.second);
				throw ex;
			} finally {
//				memDataset.end();
			}
			
		}

		/**
		 * Unloads Data from this thread's working model.
		 * Includes comments from original data.
		 * @param buffer
		 * 			Original data for extracting comments.
		 * @param out
		 * 			Output Writer.
		 * @throws Exception
		 */
		private void unloadBuffer(Triple<List<String>, String, List<String>> sentBufferThread, Writer out) throws Exception { //TODO: adjust for TXN-Models
			String buffer = sentBufferThread.second;
			try {
				BufferedReader in = new BufferedReader(new StringReader(buffer));
				String line;
				while((line=in.readLine())!=null) {
					line=line.trim();
					if(line.startsWith("#")) out.write(line+"\n");
				}
				memDataset.getDefaultModel().write(out, "TTL");
				out.write("\n");
				out.flush();
			} catch (Exception ex) {
//				memDataset.abort();
				LOG.error("Exception while unloading: " + buffer);
			} finally {
//				memDataset.begin(ReadWrite.WRITE);
				memDataset.getDefaultModel().removeAll();
				memDataset.getNamedModel("https://github.com/acoli-repo/conll-rdf/lookback").removeAll();
				memDataset.getNamedModel("https://github.com/acoli-repo/conll-rdf/lookahead").removeAll();
//				memDataset.commit();
//				memDataset.end();
			}
			
		}
		
		/**
		 * Executes updates on this thread. Data must be preloaded first.
		 * 
		 * @param updates
		 * 			The updates as a List of Triples containing
		 * 			- update filename
		 * 			- update script
		 * 			- number of iterations
		 * @return
		 * 			List of pairs containing Execution info on each update:
		 * 			- total no. of iterations
		 * 			- total time
		 */
		private List<Pair<Integer, Long>> executeUpdates(List<Triple<String, String, String>> updates) { //TODO: NOW:check consistency and check graphsout stepping

			String sent = new String();
			boolean graphsout = false;
			boolean triplesout = false;
			if (graphOutputDir != null || triplesOutputDir != null) {
				try {
				sent = memDataset.getDefaultModel().listSubjectsWithProperty(
								memDataset.getDefaultModel().getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), 
								memDataset.getDefaultModel().getProperty("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Sentence")
							).next().getLocalName();
				} catch (Exception e) {
					sent = "none";
				}
				if (graphOutputSentences.contains(sent)){
					graphsout = true;
				}

				if (triplesOutputSentences.contains(sent)){
					triplesout = true;
				}
				if (graphsout) try {
						produceDot(memDataset.getDefaultModel(), "INIT", null, sent, 0, 0, 0);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				if (triplesout) try {
						produceNTRIPLES(memDataset.getDefaultModel(), "INIT", null, sent, 0, 0, 0);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
			List<Pair<Integer,Long> > result = new ArrayList<Pair<Integer,Long> >();
			int upd_id = 1;
			int iter_id = 1;
			for(Triple<String, String, String> update : updates) {
				iter_id = 1;
				Long startTime = System.currentTimeMillis();
				Model defaultModel = memDataset.getDefaultModel();
				ChangedListener cL = new ChangedListener();
				defaultModel.register(cL);
				String oldModel = "";
				int frq = MAXITERATE, v = 0;
				boolean change = true;
				try {
					frq = Integer.parseInt(update.third);
				} catch (NumberFormatException e) {
					if (!"*".equals(update.third))
						throw e;
				}
				while(v < frq && change) {
					try {
						UpdateRequest updateRequest;
						updateRequest = UpdateFactory.create(update.second);
						if (graphsout || triplesout) { //execute Update-block step by step and output intermediate results
							int step = 1;
							Model dM = memDataset.getDefaultModel();
							String dMS = dM.toString();
							ChangedListener cLdM = new ChangedListener();
							dM.register(cLdM);
							for(Update operation : updateRequest.getOperations()) {
								//							memDataset.begin(ReadWrite.WRITE);
								UpdateAction.execute(operation, memDataset);
								//							memDataset.commit();
								//							memDataset.end();
								if (cLdM.hasChanged() && (!dMS.equals(memDataset.getDefaultModel().toString()))) {
									if (graphsout) try {
										produceDot(defaultModel, update.first, operation.toString(), sent, upd_id, iter_id, step);
									} catch (IOException e) {
										LOG.error("Error while producing DOT for update No. "+upd_id+": "+update.first);
										e.printStackTrace();
									}
									if (triplesout) try {
										produceNTRIPLES(defaultModel, update.first, operation.toString(), sent, upd_id, iter_id, step);
									} catch (IOException e) {
										LOG.error("Error while producing NTRIPLES for update No. "+upd_id+": "+update.first);
										e.printStackTrace();
									}
								}
								step++;
							}
						} else { //execute updates en bloc
							//						memDataset.begin(ReadWrite.WRITE);
							UpdateAction.execute(updateRequest, memDataset); //REMOVE THE PARAMETERS sent_id, upd_id, iter_id to use deshoe's original file names
							//						memDataset.commit();
							//						memDataset.end();
						}
					} catch (Exception e) {
						LOG.error("Error while processing update No. "+upd_id+": "+update.first);
						e.printStackTrace();
					}
					
					
					if (oldModel.isEmpty()) {
						change = cL.hasChanged();
						LOG.trace("cl.hasChanged(): "+change);
					} else {
						change = !defaultModel.toString().equals(oldModel);
						oldModel = "";
					}
					if (CHECKINTERVAL.contains(v))
						oldModel = defaultModel.toString();
					v++;
					iter_id++;
				}
				if (v == MAXITERATE)
					LOG.warn("Warning: MAXITERATE reached for " + update.first + ".");
				result.add(new Pair<Integer, Long>(v, System.currentTimeMillis() - startTime));
				defaultModel.unregister(cL);
				upd_id++;
			}			
			return result;
		}
		
		/**
		 * Produce dotFile for a specific update iteration.
		 * 
		 * @param m
		 * 			The current model.
		 * @param updateSrc
		 * 			The update source filename.
		 * @param updateQuery
		 * 			The update query string.
		 * @param sent
		 * 			The sentence ID.
		 * @param upd_id
		 * 			The update ID.
		 * @param iter_id
		 * 			The ID of the current iteration of the given update on the given sentence.
		 * @param step
		 * 			The single isolated query step of the current update.
		 * @throws IOException
		 */
		private void produceDot(Model m, String updateSrc, String updateQuery, String sent, int upd_id, int iter_id, int step) throws IOException {
			if (graphOutputDir != null) {
				String updateName = (new File(updateSrc)).getName();
				updateName = (updateName != null && !updateName.isEmpty()) ? updateName : UUID.randomUUID().toString();
				
				File outputFile = new File(graphOutputDir, sent
								+"__U"+String.format("%03d", upd_id)
								+"_I" +String.format("%04d", iter_id)
								+"_S" +String.format("%03d", step)
								+"__" +updateName.replace(".sparql", "")+".dot");
				Writer w = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
				CoNLLRDFViz.produceDot(m, w, updateQuery);
			}		
		}
		
		/**
		 * Produce lexicographically sorted ntriples-file for a specific update iteration.
		 * 
		 * @param m
		 * 			The current model.
		 * @param updateSrc
		 * 			The update source filename.
		 * @param updateQuery
		 * 			The update query string.
		 * @param sent
		 * 			The sentence ID.
		 * @param upd_id
		 * 			The update ID.
		 * @param iter_id
		 * 			The ID of the current iteration of the given update on the given sentence.
		 * @param step
		 * 			The single isolated query step of the current update.
		 * @throws IOException
		 */
		private void produceNTRIPLES(Model m, String updateSrc, String updateQuery, String sent, int upd_id, int iter_id, int step) throws IOException {
			if (triplesOutputDir != null) {
				String updateName = (new File(updateSrc)).getName();
				updateName = (updateName != null && !updateName.isEmpty()) ? updateName : UUID.randomUUID().toString();
				
				File outputFile = new File(triplesOutputDir, sent
								+"__U"+String.format("%03d", upd_id)
								+"_I" +String.format("%04d", iter_id)
								+"_S" +String.format("%03d", step)
								+"__" +updateName.replace(".sparql", "")+".nt");
				//write N3 to String
				StringWriter w = new StringWriter();
				m.write(w, "N-TRIPLE");
				//sort lines
				List<String> list = Arrays.asList(w.toString().split("\\n"));
				Collections.sort(list);
				//Write lines to file
				Writer out = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
				for (String s : list) {
					out.write(s+"\n");
				}
				out.flush();
				out.close();
			}		
		}
	}
	
	
	private final Dataset dataset;
	
	//for updates
	private final List<Triple<String, String, String>> updates;
	
	//for thread handling
	private final List<UpdateThread> updateThreads;
	private final List<String> sentBufferOut; //Buffer for outputting sentences in original order
	// Buffer providing each thread with its respective sentence(s) to process
	// <List:lookbackBuffer>, <String:currentSentence>, <List:lookaheadBuffer>
	private final List<Triple<List<String>, String, List<String>>> sentBufferThreads; 


	//For lookahead
	private final List<String> sentBufferLookahead; 
	private int lookahead_snts = 0;
	
	//For lookback
	private final List<String> sentBufferLookback; 
	private int lookback_snts = 0;
	
	//For graphsout
	private final List<String> graphOutputSentences; 
	private File graphOutputDir;
	
	//For triplesout
	private final List<String> triplesOutputSentences; 
	private File triplesOutputDir;
	
	//for statistics
	private final List<List<Pair<Integer,Long>>> dRTs; // iterations and execution time of each update in seconds
	//private int parsedSentences = 0; // no longer used since graphsout default is set at sentence readin
	private boolean running = false;
	

	/**
	 * Default Constructor providing empty data to the standard constructor.
	 */
	public CoNLLRDFUpdater() {
		this("", "", 0);
	}
	
	/**
	 * Standard Constructor for Updater. Creates Threads and Buffers for Thread handling.
	 * Also creates the database modules for the respective execution modes.
	 * @param type: The type of database to be used:
	 * 				MEM: fully independent in-memory datasets per thread 
	 * 						(fastest, no transactions, high RAM usage, no HDD)
	 * 				TXN: single transactional in-memory dataset for all threads
	 * 						(in development, medium speed and RAM, no HDD)
	 * 				TDB2: single transactional TDB2-database for all threads
	 * 						(in development, slow-medium speed, low RAM usage, high HDD usage)
	 * 				default: MEM
	 * @param path: 
	 * 				path to database (only for TDB2 or other DB-backed modes)
	 * @param threads
	 * 				Maximum amount of threads for execution.
	 * 				default: threads = number of logical cores available to runtime
	 */
	public CoNLLRDFUpdater(String type, String path, int threads) {
		if (type == "TDB2") {
			//TODO
			dataset = DatasetFactory.create();//TDB
		} else if (type == "TXN") {
			dataset = DatasetFactory.createTxnMem();
		} else {
			dataset = DatasetFactory.createTxnMem();
		}
//		memAccessor = DatasetAccessorFactory.create(memDataset);
		
		//updates
		updates = Collections.synchronizedList(new ArrayList<Triple<String, String, String>>());
		
		//threads
		// Use the processor cores available to runtime (but at least 1) as thread count, if an invalid thread count is provided.
		if (threads <= 0) {
			threads = (Runtime.getRuntime().availableProcessors()>0)?(Runtime.getRuntime().availableProcessors()):(1);
			LOG.info("Falling back to default thread maximum.");
		}
		LOG.info("Executing on "+threads+" processor cores, max.");
		updateThreads = Collections.synchronizedList(new ArrayList<UpdateThread>());
		sentBufferThreads = Collections.synchronizedList(new ArrayList<Triple<List<String>, String, List<String>>>());
		dRTs = Collections.synchronizedList(new ArrayList<List<Pair<Integer,Long>>>());
		for (int i = 0; i < threads; i++) {
			updateThreads.add(null);
			dataset.addNamedModel("http://thread"+i, ModelFactory.createDefaultModel());
			sentBufferThreads.add(new Triple<List<String>, String, List<String>>(
					new ArrayList<String>(), new String(), new ArrayList<String>()));
			dRTs.add(Collections.synchronizedList(new ArrayList<Pair<Integer,Long> >()));
		}
		sentBufferOut = Collections.synchronizedList(new ArrayList<String>());
		
		//lookahead+lookback
		sentBufferLookahead = Collections.synchronizedList(new ArrayList<String>());
		sentBufferLookback = Collections.synchronizedList(new ArrayList<String>());
		
		//graphsout
		graphOutputSentences = Collections.synchronizedList(new ArrayList<String>());
		graphOutputDir = null;
		
		//triplesout
		triplesOutputSentences = Collections.synchronizedList(new ArrayList<String>());
		triplesOutputDir = null;
		
		//runtime
		//parsedSentences = 0;
		running = false;
	}

	/**
	 * Activates the lookahead mode for caching a fixed number of additional sentences per thread.
	 * @param lookahead_snts
	 * 			the number of additional sentences to be cached
	 */
	public void activateLookahead(int lookahead_snts) {
		if (lookahead_snts < 0) lookahead_snts = 0;
		this.lookahead_snts = lookahead_snts;
	}
	
	/**
	 * Activates the lookback mode for caching a fixed number of preceding sentences per thread.
	 * @param lookback_snts
	 * 			the number of preceding sentences to be cached
	 */
	public void activateLookback(int lookback_snts) {
		if (lookback_snts < 0) lookback_snts = 0;
		this.lookback_snts = lookback_snts;
	}
	
	/**
	 * Activates the graphsout mode for single graphviz .dot files per execution step.
	 * @param dir
	 * 			folder to store .dot files
	 * @param sentences
	 * 			List of sentenceIDs to be included in graphsout mode (s23_0, s4_0 ...)
	 * @throws IOException
	 */
	public void activateGraphsOut(String dir, List<String> sentences) throws IOException {
		graphOutputSentences.clear();
		graphOutputDir = new File(dir.toLowerCase());
		if (graphOutputDir.exists() || graphOutputDir.mkdirs()) {
			if (! graphOutputDir.isDirectory()) {
				graphOutputDir = null;
				throw new IOException("Error: Given -graphsout DIRECTORY is not a valid directory: " + dir.toLowerCase());
			}
		} else {
			graphOutputDir = null;
			throw new IOException("Error: Failed to create given -graphsout DIRECTORY: " + dir.toLowerCase());
		}
		graphOutputSentences.addAll(sentences);
	}
	
	/**
	 * Activates the triplesout mode for single ntriples-files per execution step.
	 * @param dir
	 * 			folder to store .dot files
	 * @param sentences
	 * 			List of sentenceIDs to be included in triplesout mode (s23_0, s4_0 ...)
	 * @throws IOException
	 */
	public void activateTriplesOut(String dir, List<String> sentences) throws IOException {
		triplesOutputSentences.clear();
		triplesOutputDir = new File(dir.toLowerCase());
		if (triplesOutputDir.exists() || triplesOutputDir.mkdirs()) {
			if (! triplesOutputDir.isDirectory()) {
				triplesOutputDir = null;
				throw new IOException("Error: Given -triplesout DIRECTORY is not a valid directory: " + dir.toLowerCase());
			}
		} else {
			triplesOutputDir = null;
			throw new IOException("Error: Failed to create given -triplesout DIRECTORY: " + dir.toLowerCase());
		}
		triplesOutputSentences.addAll(sentences);
	}

	/**
	 * Load external RDF file into a named graph of the local dataset. 
	 * This graph is permanent for the runtime and is accessed read-only by all threads.
	 * The default graph of the local dataset is reserved for updating nif:Sentences and 
	 * can not be defined here.
	 * @param url
	 * 			location of the RDF file to be loaded
	 * @param graph (optional)
	 * 			the named graph to load the data into.
	 * 			default: graph = url
	 * @throws IOException
	 */
	public void loadGraph(URI url, URI graph) throws IOException {
		LOG.info("loading...");
		LOG.info(url +" into "+ graph);
		if (!url.isAbsolute()) {
			url = (new File(url.toString())).toURI();
		}
		if (graph == null) {
			graph = url;
		}
		Model m = ModelFactory.createDefaultModel();
		try {
			m.read(readInURI(url));
			dataset.addNamedModel(graph.toString(), m);
		} catch (IOException ex) {
			LOG.error("Exception while reading " + url + " into " + graph);
			throw ex;
		}
		LOG.info("done...");
	}
	
	/**
	 * Define a set of updates to be executed for each sentence processed by this CoNLLRDFUpdater.
	 * Existing updates will be overwritten by calling this function.
	 * @param updatesRaw
	 * 			The new set of updates as a List of String Triples. Each Triple has the following form:
	 * 			<Name of Update>, <update script>OR<path to script>, <iterations>
	 * @throws IOException
	 */
	public void parseUpdates(List<Triple<String, String, String>> updatesRaw) throws IOException { 
		this.updates.clear();
		
		List<Triple<String, String, String>> updatesTemp = new ArrayList<Triple<String, String, String>>();
		updatesTemp.addAll(updatesRaw);
		
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i<updatesTemp.size(); i++) {
			Reader sparqlreader = new StringReader(updatesTemp.get(i).second);
			File f = new File(updatesTemp.get(i).second);
			URL u = null;
			try {
				u = new URL(updatesTemp.get(i).second);
			} catch (MalformedURLException e) {}
			if(f.exists()) {			// can be read from a file 
				try {
					sparqlreader = new FileReader(f);
					sb.append("f");
				} catch (Exception e) {}
			} else if(u!=null) {
				try {
					sparqlreader = new InputStreamReader(u.openStream());
					sb.append("u");
				} catch (Exception e) {
				}
			}
			
			String updateName = updatesTemp.get(i).first;
			// check for String as Update and set update name to default
			if (!(sparqlreader instanceof FileReader) && !(sparqlreader instanceof InputStreamReader)) {
				updateName = DEFAULTUPDATENAME;
			}
			
			updatesTemp.set(i,new Triple<String, String, String>(updateName, "", updatesTemp.get(i).third)); // TODO: Needed? just removes the second string, but second is overwritten later anyway....
			BufferedReader in = new BufferedReader(sparqlreader);
			String updateBuff = "";
			for(String line = in.readLine(); line!=null; line=in.readLine())
				updateBuff = updateBuff + line + "\n";
			isValidUTF8(updateBuff, "SPARQL update String is not UTF-8 encoded for " + updatesTemp.get(i).first);
			try {
				@SuppressWarnings("unused")
				UpdateRequest qexec = UpdateFactory.create(updateBuff);
				updatesTemp.set(i,new Triple<String, String, String>(updatesTemp.get(i).first, updateBuff, updatesTemp.get(i).third));
			} catch (QueryParseException e) {
				LOG.error("SPARQL parse exception for Update No. "+i+": "+updateName+"\n" + e + "\n" + updateBuff); // this is SPARQL code with broken SPARQL syntax
				System.exit(1);
//				try {
//					@SuppressWarnings("unused")
//					Path ha = Paths.get(updateBuff.trim());
//				} catch (InvalidPathException d) {
//					LOG.error("SPARQL parse exception for:\n" + updateBuff); // this is SPARQL code with broken SPARQL syntax
//					System.exit(1);
//				}
//				LOG.error("File not found exception for (Please note - if update is passed on as a String is has to be in `-quotes!): " + updatesTemp.get(i).first); // this is a faulty SPARQL script file path - if you have written a valid path into your SPARQL script file, it is your own fault
//				System.exit(1);
			}				
			updatesTemp.set(i,new Triple<String, String, String>(updatesTemp.get(i).first, updateBuff, updatesTemp.get(i).third));
			sb.append(".");
		}
		
		this.updates.addAll(Collections.synchronizedList(updatesTemp));
		LOG.debug(sb.toString());

	}
	
	/**
	 * Tries to read from a specific URI.
	 * Tries to read content directly or from GZIP
	 * Validates content against UTF-8.
	 * @param uri
	 * 		the URI to be read
	 * @return
	 * 		the text content
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	private static String readInURI(URI uri) throws MalformedURLException, IOException {
		String result = null;
		try {
			result = uri.toString();
			if (result != null && result.endsWith(".gz")) {
				StringBuilder sb = new StringBuilder();
				BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(uri.toURL().openStream())));
				for (String line; (line = br.readLine()) != null; sb.append(line));
				result = sb.toString();
				isValidUTF8(result, "Given URI input (" + uri.getPath() + ") is not UTF-8 encoded");
			}
		} catch (Exception ex) {
			LOG.error("Excpetion while reading " + uri.getPath());
			throw ex;
		}
		return result;
	}
	
	private static void isValidUTF8(String s, String message) {
		try 
		{
			s.getBytes("UTF-8");
		} 
		catch (UnsupportedEncodingException e)
		{
		    LOG.error(message + " - Encoding error: " + e.getMessage());
		    System.exit(-1);
		}		
	}

	/**
	 * Processes CoNLL-RDF on the local dataset using the predfined updates and threads.
	 * Streams data from a buffered reader to a buffered writer. Distributes the processing 
	 * across available threads. Each thread handles one sentence at a time.
	 * Caches and outputs the resulting sentences in-order.
	 * @param in
	 * 			the input stream wrapped in a BufferedReader
	 * @param out
	 * 			the output stream wrapped in a PrintStream
	 * @throws IOException
	 */
	public void processSentenceStream() throws IOException {
		running = true;
		String line;
		String lastLine ="";
		String buffer="";
//		List<Pair<Integer,Long> > dRTs = new ArrayList<Pair<Integer,Long> >(); // iterations and execution time of each update in seconds
		while((line = getInputStream().readLine())!=null) {
			line=line.replaceAll("[\t ]+"," ").trim();

			if(!buffer.trim().equals(""))
				if((line.startsWith("@") || line.startsWith("#")) && !lastLine.startsWith("@") && !lastLine.startsWith("#")) { //!buffer.matches("@[^\n]*\n?$")) {
					if ((graphOutputDir != null) && (graphOutputSentences.isEmpty())) { // get first sentence id as default for graphsout output
						Model m = ModelFactory.createDefaultModel();
						String sentID = m.read(new StringReader(buffer),null, "TTL").listSubjectsWithProperty(
								m.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), 
								m.getProperty("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Sentence")
							).next().getLocalName();
						graphOutputSentences.add(sentID);
					}
					// --> deprecated
					//parsedSentences++;
					//execute updates using thread handler  --> now in lookahead handling
					//executeThread(buffer);
					// <-- deprecated 

					
					//lookahead
					//add ALL sentences to sentBufferLookahead
					sentBufferLookahead.add(buffer);
					if (sentBufferLookahead.size() > lookahead_snts) {
						//READY TO PROCESS 
						// remove first sentence from buffer and process it.
						// !!if lookahead = 0 then only current buffer is in sentBufferLookahead!!
						executeThread(sentBufferLookahead.remove(0));
					}		
					
					//lookback
					//needs to consider lookahead buffer. The full buffer size needs to be lookahead + lookback.
					if (lookback_snts > 0) {
						while (sentBufferLookback.size() >= lookback_snts + sentBufferLookahead.size()) sentBufferLookback.remove(0);
						sentBufferLookback.add(buffer);
					}
					
					
					flushOutputBuffer(getOutputStream());
					buffer="";
				}
			if(line.trim().startsWith("@") && !lastLine.trim().endsWith(".")) 
				buffer=buffer+"\n";

			if(line.trim().startsWith("#") && (!lastLine.trim().startsWith("#"))) 
				buffer=buffer+"\n";

			buffer=buffer+line+"\t";//+"\n";

			if(line.trim().endsWith(".") || line.trim().matches("^(.*>)?[^<]*#")) 
				buffer=buffer+"\n";

			lastLine=line;
		}
		// --> deprecated
		//parsedSentences++;
		//executeThread(buffer);
		// --> deprecated
		
		//lookahead 
		//add final sentence
		//work down remaining buffer
		sentBufferLookahead.add(buffer);
		while (sentBufferLookahead.size()>0) {
			executeThread(sentBufferLookahead.remove(0));
			if (lookback_snts > 0) {
				while (sentBufferLookback.size() >= lookback_snts + sentBufferLookahead.size()) sentBufferLookback.remove(0);
			}
		}
			
		
		//wait for threads to finish work
		boolean threadsRunning = true;
		while(threadsRunning) {
			threadsRunning = false;
			for (UpdateThread t:updateThreads) {
				if (t != null)
				if (t.getState() == Thread.State.RUNNABLE || t.getState() == Thread.State.BLOCKED) {
					threadsRunning = true;
				}
			}
		}
		//terminate all threads
		running = false;
		for (UpdateThread t:updateThreads) {
			if (t != null)
			if(t.getState() == Thread.State.NEW) {
				t.start(); //in case of spontaneous resurrection, new threads should not have any work to do at this point
			} else if (!(t.getState() == Thread.State.TERMINATED)) {
				synchronized(t) {
					t.notify();
				}
			}
		}
		
		//sum up statistics
		List<Pair<Integer,Long>> dRTs_sum = new ArrayList<Pair<Integer,Long> >();
		for (List<Pair<Integer,Long>> dRT_thread:dRTs) {
			if (dRTs_sum.isEmpty())
				dRTs_sum.addAll(dRT_thread);
			else
				for (int x = 0; x < dRT_thread.size(); ++x)
					dRTs_sum.set(x, new Pair<Integer, Long>(
							dRTs_sum.get(x).getKey() + dRT_thread.get(x).getKey(), 
							dRTs_sum.get(x).getValue() + dRT_thread.get(x).getValue()));
			
		}
		if (!dRTs_sum.isEmpty())
			LOG.debug("Done - List of iterations and execution times for the updates done (in given order):\n\t\t" + dRTs_sum.toString());

		//final flush
		flushOutputBuffer(getOutputStream());
		getOutputStream().close();
		
	}

	private synchronized void flushOutputBuffer(PrintStream out) {
		LOG.trace("OutBufferSIze: "+sentBufferOut.size());
		while (!sentBufferOut.isEmpty()) {
			if (sentBufferOut.get(0).matches("\\d+")) break;
			out.print(sentBufferOut.remove(0));
		}
	}

	private void executeThread(String buffer) {
		Triple<List<String>, String, List<String>>sentBufferThread = 
				new Triple<List<String>, String, List<String>>(
				new ArrayList<String>(), new String(), new ArrayList<String>());
		//sentBufferLookback only needs to be filled up to the current sentence. 
		//All other sentences are for further lookahead iterations 
//		sentBufferThread.first.addAll(sentBufferLookback);
		for (int i = 0; i < sentBufferLookback.size() - sentBufferLookahead.size(); i++) {
			sentBufferThread.first.add(sentBufferLookback.get(i));
		}
		sentBufferThread.second = buffer;
		sentBufferThread.third.addAll(sentBufferLookahead);
		int i = 0;
		while(i < updateThreads.size()) {
			LOG.trace("ThreadState " + i + ": "+((updateThreads.get(i)!=null)?updateThreads.get(i).getState():"null"));
			if (updateThreads.get(i) == null) {
				sentBufferThreads.set(i, sentBufferThread);
				sentBufferOut.add(String.valueOf(i)); //add last sentences to the end of the output queue.
				updateThreads.set(i, new UpdateThread(this, i));
				updateThreads.get(i).start();
				LOG.trace("restart "+i);
				LOG.trace("OutBufferSize: "+sentBufferOut.size());
				break;
			} else 
				if (updateThreads.get(i).getState() == Thread.State.WAITING) {
				synchronized(updateThreads.get(i)) {
				sentBufferThreads.set(i, sentBufferThread);
				sentBufferOut.add(String.valueOf(i)); //add last sentences to the end of the output queue.
				updateThreads.get(i).notify();
				}
				LOG.trace("wake up "+i);
				break;
			} else 
				if (updateThreads.get(i).getState() == Thread.State.NEW) {
				sentBufferThreads.set(i, sentBufferThread);
				sentBufferOut.add(String.valueOf(i)); //add last sentences to the end of the output queue.
				updateThreads.get(i).start();
				LOG.trace("start "+i);
				LOG.trace("OutBufferSize: "+sentBufferOut.size());
				break;
			} else 
				if (updateThreads.get(i).getState() == Thread.State.TERMINATED) {
				sentBufferThreads.set(i, sentBufferThread);
				sentBufferOut.add(String.valueOf(i)); //add last sentences to the end of the output queue.
				updateThreads.set(i, new UpdateThread(this, i));
				updateThreads.get(i).start();
				LOG.trace("restart "+i);
				LOG.trace("OutBufferSize: "+sentBufferOut.size());
				break;
			}
			
			i++;
			if (i >= updateThreads.size()) {
				try {
					synchronized (this) {
//						System.err.println("Updater waiting");
						wait(20);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					i = 0;
				}
			}
		}
	}

	public static void main(String[] argv) throws URISyntaxException, IOException {
		LOG.info("synopsis: CoNLLRDFUpdater [-loglevel LEVEL] [-threads T] [-lookahead N] [-lookback N]\n"
				+ "\t[-custom [-model URI [GRAPH]]* [-graphsout DIR [SENT_ID]] [-triplesout DIR [SENT_ID]] -updates [UPDATE]]\n"
				+ "\t\t-loglevel: set log level to LEVEL\n"
				+ "\t\t-threads: use T threads max\n"
				+ "\t\t-lookahead: cache N further sentences in lookahead graph\n"
				+ "\t\t-lookback: cache N preceeding sentences in lookback graph\n"
				+ "\t\t\t default: half of available logical processor cores\n"
				+ "\t\t-custom: use custom update scripts\n"
				+ "\t\t-model: to load additional Models into local graph\n"
				+ "\t\t-graphsout: output directory for the .dot graph files\n"
				+ "\t\t\t followed by the IDs of the sentences to be visualized\n"
				+ "\t\t\t default: first sentence only\n"
				+ "\t\t-triplesout: same as graphsout but write N-TRIPLES for text debug instead.\n"
				+ "\t\t\t followed by the IDs of the sentences\n"
				+ "\t\t\t default: first sentence only\n"
				+ "\t\t-updates: followed by SPARQL scripts paired with {iterations/u}\n"
				+ "\t\tread TTL from stdin => update CoNLL-RDF");
		
		String args = Arrays.asList(argv).toString().replaceAll("[\\[\\], ]+"," ").trim().toLowerCase();
		int i = 0;
		
		// READ LOGLEVEL
		if (args.contains("-loglevel ")) {
			i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+loglevel$")) i++;
			i++;
			try {
				LOG.setLevel(Level.toLevel(argv[i]));
			} catch (Exception e) {
				LOG.error("Wrong usage of threads parameter. NaN.");
				System.exit(0);
			}
		}
		
		// READ THREAD PARAMETERS
		int threads = 0;
		if (args.contains("-threads ")) {
			i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+threads$")) i++;
			i++;
			try {
				threads = Integer.parseInt(argv[i]);
			} catch (Exception e) {
				LOG.error("Wrong usage of threads parameter. NaN.");
				System.exit(0);
			}
		}
		
		//READ MODE (currently only CUSTOM)
		boolean CUSTOM = args.contains("-custom ");
		if(!CUSTOM ) { // no default possible here
			LOG.error("Please specify update script.");
		}

		
		CoNLLRDFUpdater updater = new CoNLLRDFUpdater("","",threads);

		// should be <#UPDATEFILENAMEORSTRING, #UPDATESTRING, #UPDATEITER>
		List<Triple<String, String, String>> updates = new ArrayList<Triple<String, String, String>>();
		List<String> models = new ArrayList<String>();
		
		

		// READ GRAPHSOUT PARAMETERS
		String graphOutputDir = null;
		List<String> graphOutputSentences = new ArrayList<String>();

		if (args.contains("-graphsout ")) {
			i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+graphsout$")) i++;
			i++;
			graphOutputDir = argv[i];
			i++;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+.*$")) {
				graphOutputSentences.add(argv[i++]);
			}
			updater.activateGraphsOut(graphOutputDir, graphOutputSentences);
		}
		
		// READ TRIPLESOUT PARAMETERS
		String triplesOutputDir = null;
		List<String> triplesOutputSentences = new ArrayList<String>();

		if (args.contains("-triplesout ")) {
			i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+triplesout$")) i++;
			i++;
			triplesOutputDir = argv[i];
			i++;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+.*$")) {
				triplesOutputSentences.add(argv[i++]);
			}
			updater.activateTriplesOut(triplesOutputDir, triplesOutputSentences);
		}
		
		
		// READ LOOKAHEAD PARAMETERS
		int lookahead_snts = 0;
		if (args.contains("-lookahead ")) {
			i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+lookahead$")) i++;
			i++;
			try {
				lookahead_snts = Integer.parseInt(argv[i]);
			} catch (Exception e) {
				LOG.error("Wrong usage of lookahead parameter. NaN.");
				System.exit(0);
			}
			updater.activateLookahead(lookahead_snts);
		}
		
		// READ LOOKBACK PARAMETERS
		int lookback_snts = 0;
		if (args.contains("-lookback ")) {
			i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+lookback$")) i++;
			i++;
			try {
				lookback_snts = Integer.parseInt(argv[i]);
			} catch (Exception e) {
				LOG.error("Wrong usage of lookback parameter. NaN.");
				System.exit(0);
			}
			updater.activateLookback(lookback_snts);
		}
		
		
		//CUSTOM UPDATE SCRIPT MODE
		if(CUSTOM) {
			
			
			// READ ALL MODELS from System.in
			i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+custom$")) i++;
			i++;
			while(i<argv.length) {
				while(i<argv.length && !argv[i].toLowerCase().matches("^-+model$")) i++;
				i++;
				while(i<argv.length && !argv[i].toLowerCase().matches("^-+.*$"))
					models.add(argv[i++]);
				if (models.size()==1) {
					updater.loadGraph(new URI(models.get(0)), new URI(models.get(0)));
				} else if (models.size()==2){
					updater.loadGraph(new URI(models.get(0)), new URI(models.get(1)));
				} else if (models.size()>2){
					throw new IOException("Error while loading model: Please use -custom [-model URI [GRAPH]]* -updates [UPDATE]+");
				}
				models.removeAll(models);
			}
			
			

			// READ ALL UPDATES from System.in
			i=0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+custom$")) i++;
			i++;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+updates$")) i++;
			i++;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+.*$")) {
				String freq;
				freq = argv[i].replaceFirst(".*\\{([0-9u*]+)\\}$", "$1");
				if (argv[i].equals(freq)) // update script without iterations in curly brackets defaults to 1
					freq = "1";
				else if (freq.equals("u"))
					freq = "*";
				String update =argv[i++].replaceFirst("\\{[0-9u*]+\\}$", "");
				updates.add(new Triple<String, String, String>(update, update, freq));
			}
			updater.parseUpdates(updates);
			

			long start = System.currentTimeMillis();
			
			updater.setInputStream(new BufferedReader(new InputStreamReader(System.in)));
			updater.setOutputStream(System.out);
			
			//READ SENTENCES from System.in  
			updater.processSentenceStream();
			LOG.debug((System.currentTimeMillis()-start)/1000 + " seconds");
		}
	}

	@Override
	public void start() {
		run();
	}

	@Override
	public void run() {
		try {
			processSentenceStream();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
