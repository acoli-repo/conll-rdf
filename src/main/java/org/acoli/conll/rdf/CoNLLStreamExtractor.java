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

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.jena.rdf.listeners.ChangedListener;
import org.apache.jena.rdf.model.*;
import org.apache.jena.update.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.*;

/** extracts RDF data from CoNLL files, transforms the result using SPARQL UPDATE queries,
 * 	optionally followed by SPARQL SELECT to produce TSV output<br>
 *  NOTE: queries can be provided as literal queries, file or as URLs, the latter two are preferred as
 *  a literal query string may be parsed by the JVM/shell
 *  @author Christian Chiarcos {@literal chiarcos@informatik.uni-frankfurt.de}
 *  @author Christian Faeth {@literal faeth@em.uni-frankfurt.de}
 */
public class CoNLLStreamExtractor extends CoNLLRDFComponent {
	private static Logger LOG = LogManager.getLogger(CoNLLStreamExtractor.class.getName());
	private String baseURI;
	private List<String> columns = new ArrayList<String>();
	private boolean readColumnComment = false;
	private String select = null;
	List<Pair<String, String>> updates = new ArrayList<Pair<String, String>>();
	
	//@Leo: added for improved Fintan support
	private String segmentDelimiter;

	public String getSegmentDelimiter() {
		return segmentDelimiter;
	}

	public void setSegmentDelimiter(String segmentDelimiter) {
		this.segmentDelimiter = segmentDelimiter;
	}

	public String getBaseURI() {
		return baseURI;
	}

	public void setBaseURI(String baseURI) {
		this.baseURI = baseURI;
	}

	public List<String> getColumns() {
		return columns;
	}

	public void setColumns(List<String> columns) {
		this.columns = columns;
	}

	public void setReadColumnComment(boolean read) {
		readColumnComment = read;
	}

	public String getSelect() {
		return select;
	}

	public void setSelect(String select) {
		this.select = select;
	}

	public List<Pair<String, String>> getUpdates() {
		return updates;
	}

	public void setUpdates(List<Pair<String, String>> updates) {
		this.updates = updates;
	}

	@Override
	protected void processSentenceStream() throws IOException {
		if (readColumnComment) {
			// look for a CoNLL-U Plus -style comment containing the Columns
			this.findColumnsFromComment();
			// TODO this check should happen earlier
			if (this.getColumns().isEmpty()) {
				throw new IOException("Missing required Argument Fields/Columns not found as global.columns either");
			}
		}
		CoNLL2RDF conll2rdf = new CoNLL2RDF(baseURI, columns.toArray(new String[columns.size()]));
		List<Pair<Integer,Long> > dRTs = new ArrayList<Pair<Integer,Long> >(); // iterations and execution time of each update in seconds
		LOG.info("process input ..");
		BufferedReader in = new BufferedReader(new InputStreamReader(getInputStream()));
		OutputStreamWriter out = new OutputStreamWriter(new PrintStream(getOutputStream()));
		String buffer = "";
		ArrayList<String> comments = new ArrayList<>();
		for(String line = ""; line !=null; line=in.readLine()) {
			if(line.contains("#")) {
				out.write(line.replaceAll("^[^#]*#", "#") + "\n");
				comments.add(line.replaceAll("^[^#]*#", ""));
			}
			line=line.replaceAll("<[\\/]?[psPS]( [^>]*>|>)","").trim(); // in this way, we can also read sketch engine data and split at s and p elements
			if(!(line.matches("^<[^>]*>$")))							// but we skip all other XML elements, as used by Sketch Engine or TreeTagger chunker
				if(line.equals("") && !buffer.trim().equals("")) {
					Model m = conll2rdf.conll2model(new StringReader(buffer+"\n"));
					if(m!=null) { // null if an error occurred
						List<Pair<Integer,Long> > ret = update(m, updates);
						if (dRTs.isEmpty())
							dRTs = ret;
						else
							for (int x = 0; x < ret.size(); ++x)
								dRTs.set(x, new ImmutablePair<Integer, Long>(dRTs.get(x).getKey() + ret.get(x).getKey(), dRTs.get(x).getValue() + ret.get(x).getValue()));
						if (comments.size() > 0) {
							m = injectSentenceComments(m, comments);
							comments.clear();
						}
						print(m,select, out);
						if(segmentDelimiter != null) {
							out.write("\n"+segmentDelimiter+"\n");
							out.flush();
						}
					}
					buffer="";
				} else
					buffer=buffer+line+"\n";
		}
		if(!buffer.trim().equals("")) {
			Model m = conll2rdf.conll2model(new StringReader(buffer+"\n"));
			List<Pair<Integer,Long> > ret = update(m, updates);
			if (dRTs.isEmpty())
				dRTs = ret;
			else
				for (int x = 0; x < ret.size(); ++x)
					dRTs.set(x, new ImmutablePair<Integer, Long>(dRTs.get(x).getKey() + ret.get(x).getKey(), dRTs.get(x).getValue() + ret.get(x).getValue()));
			if (comments.size() > 0) {
				m = injectSentenceComments(m, comments);
				comments.clear();
			}
			print(m,select,out);
		}
		if (!dRTs.isEmpty())
			LOG.debug("Done - List of interations and execution times for the updates done (in given order):\n\t\t" + dRTs.toString());

		getOutputStream().close();
	
	}

	/**
	 * Adds a list of conll comments to a sentence model as a rdfs:comment property separated by escaped newlines.
	 * @param model a RDF Model representing a sentence
	 * @param comments a list of single line comments
	 * @return the updated model
	 */
	private Model injectSentenceComments(Model model, ArrayList<String> comments) {
		LOG.debug("Injecting comments.");
		// alternative to ParameterizedSparqlString: UpdateQuery
		ParameterizedSparqlString s = new ParameterizedSparqlString();
		s.setCommandText("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
			+"PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"
			+"INSERT { ?node rdfs:comment ?comment . }"
			+"WHERE { ?node a nif:Sentence . }");
		s.setLiteral("comment", String.join("\\n", comments));

		UpdateAction.execute(s.asUpdate(), model);
		return model;
	}
	/**
	 * Searches a BufferedReader for a global.columns = field to extract the column names from (CoNLL-U Plus feature).
	 * We allow for arbitrary lines to search, however as of September 2019, CoNLL-U Plus only allows first line.
	 * Does NOT validate if custom columns are in a separate name space, as required by the format.
	 * @see <a href="https://universaldependencies.org/ext-format.html">CoNLL-U Plus Format</a>
	 * @param inputStream an untouched BufferedReader
	 * @param maxLinesToSearch how many lines to search. CoNLL-U Plus requires this to be 1.
	 */
	static List<String> findFieldsFromComments(BufferedReader inputStream, int maxLinesToSearch) throws IOException {
		List<String> fields = new ArrayList<>();
		final int readAheadLimit = 10000;
		float meanByteSizeOfLine = 0.0f;
		int m = 0;
		int peekedChars = 0;
		LOG.debug("Peeking Stream for global.columns comment...");
		if ( ! inputStream.ready()) {
			LOG.debug("Stream is not ready. Waiting...");
		}
		if (!inputStream.markSupported()) {
			LOG.warn("Marking is not supported for this inputStream, could lead to cutting off the beginning of the stream.");
		}
		try {
			inputStream.mark(readAheadLimit);

			String peekedLine;
			while ((peekedLine = inputStream.readLine()) != null && m < maxLinesToSearch) {
				m++;
				meanByteSizeOfLine = ((meanByteSizeOfLine * (m - 1)) + peekedLine.length()) / m;
				LOG.debug("Mean Byte size: " + meanByteSizeOfLine);
				peekedChars += peekedLine.length();
				LOG.debug("Peeking line: " + peekedLine);
				if ((peekedChars + meanByteSizeOfLine) > readAheadLimit) {
					LOG.info("Couldn't find CoNLL-U Plus columns.");
					inputStream.reset();
					return fields;
				}
				LOG.debug("Testing line: " + peekedLine);
				if (peekedLine.matches("^#\\s?global\\.columns\\s?=.*")) {
					// TODO catch bad global.comment containing no columns
					fields = Arrays.asList(peekedLine.trim().replaceFirst("#\\s?global\\.columns\\s?=", "").split("#")[0]
									.trim().split(" |\t"));
					inputStream.reset();
					return fields;
				}

			}
			inputStream.reset();
		} catch (IOException e) {
			LOG.error(e);
			LOG.warn("Couldn't figure out CoNLL-U Plus, searched for " + m + " lines.");
		}
		return fields;
	}

	public void findColumnsFromComment() throws IOException {
		this.setColumns(findFieldsFromComments(new BufferedReader(new InputStreamReader(this.getInputStream())), 1));
		LOG.info("\tCoNLL columns: " + this.getColumns());
	}

	public List<Pair<Integer, Long>> update(Model m, List<Pair<String, String>> updates) {
		List<Pair<Integer, Long>> result = new ArrayList<Pair<Integer, Long>>();
		for (Pair<String, String> update : updates) {
			Long startTime = System.currentTimeMillis();
			ChangedListener cL = new ChangedListener();
			m.register(cL);
			String oldModel = "";
			int frq = MAXITERATE, v = 0;
			boolean change = true;
			try {
				frq = Integer.parseInt(update.getValue());
			} catch (NumberFormatException e) {
				if (!"*".equals(update.getValue()))
					throw e;
			}
			while(v < frq && change) {
				UpdateAction.execute(UpdateFactory.create(update.getKey()), m);
				if (oldModel.isEmpty())
					change = cL.hasChanged();
				else {
					change = !m.toString().equals(oldModel);
					oldModel = "";
				}
				if (CHECKINTERVAL.contains(v))
					oldModel = m.toString();
				v++;
			}
			if (v == MAXITERATE)
				LOG.warn("Warning: MAXITERATE reached.");
			result.add(new ImmutablePair<Integer, Long>(v, System.currentTimeMillis() - startTime));
			m.unregister(cL);
		}
		return result;
	}
		
	/** run either SELECT statement (cf. https://jena.apache.org/documentation/query/app_api.html) and return CoNLL-like TSV or just TTL <br>
	 *  Note: this CoNLL-like export has limitations, of course: it will export one property per column, hence, collapsed dependencies or 
	 *  SRL annotations cannot be reconverted */
	public void print(Model m, String select, Writer out) throws IOException {
		if(select!=null) {
			QueryExecution qexec = QueryExecutionFactory.create(select, m);
			ResultSet results = qexec.execSelect();
			List<String> cols = results.getResultVars();
			out.write("# "); 									// well, this may be redundant, but permitted in CoNLL
			for(String col : cols)
				out.write(col+"\t");
			out.write("\n");
			out.flush();
			while(results.hasNext()) {
				QuerySolution sol = results.next();
				for(String col : cols)
					if(sol.get(col)==null) out.write("_\t");		// CoNLL practice
					else out.write(sol.get(col)+"\t");
				out.write("\n");
				out.flush();
			}
			out.write("\n");
			out.flush();
		} else {
			m.write(out, "TTL");
			out.flush();
		}
	}

	public Pair<String, String> parseUpdate(String updateArg) throws IOException {
		String freq;
		// TODO Move this code for parsing Options to CoNLLRDFCommandLine
		// TODO Unit Testing for this Method
		freq = updateArg.replaceFirst(".*\\{([0-9u*]+)\\}$", "$1");
		if (updateArg.equals(freq)) {
			freq = "1";
		} else if (freq.equals("u")) {
			freq = "*";
		}
		final String updateRaw = updateArg.replaceFirst("\\{[0-9*]+\\}$", "");
		return new ImmutablePair<String, String>(updateRaw, freq);
		// UpdateRequest request = UpdateFactory.create();
	}

	public String parseSparqlArg(String sparqlArg) throws IOException {
		// TODO this code is duplicate and should be stored centrally in CoNLLRDFCommandLine
		// TODO Unit Testing for this Method
		String sparql = "";

		Reader sparqlreader = new StringReader(sparqlArg);
		File file = new File(sparqlArg);
		URL url = null;
		try {
			url = new URL(sparqlArg);
		} catch (MalformedURLException e) {
		}

		if (file.exists()) { // can be read from a file
			sparqlreader = new FileReader(file);
		} else if (url != null) {
			try {
				sparqlreader = new InputStreamReader(url.openStream());
			} catch (Exception e) {
			}
		}

		BufferedReader in = new BufferedReader(sparqlreader);
		for (String line = in.readLine(); line != null; line = in.readLine()) {
			sparql = sparql + line + "\n";
		}
		return sparql;
	}

	public static void main(String[] args) throws IOException {
		final CoNLLStreamExtractor extractor;
		try {
			extractor = new CoNLLStreamExtractorFactory().buildFromCLI(args);
			extractor.setInputStream(System.in);
			extractor.setOutputStream(System.out);
		} catch (ParseException e) {
			LOG.error(e);
			System.exit(1);
			return;
		}
		extractor.processSentenceStream();
	}
}
