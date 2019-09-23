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
import org.apache.log4j.Logger;
import org.apache.jena.query.*;

/** extracts RDF data from CoNLL files, transforms the result using SPARQL UPDATE queries,
 * 	optionally followed by SPARQL SELECT to produce TSV output<br>
 *  NOTE: queries can be provided as literal queries, file or as URLs, the latter two are preferred as
 *  a literal query string may be parsed by the JVM/shell
 *  @author Christian Chiarcos {@literal chiarcos@informatik.uni-frankfurt.de}
 *  @author Christian Faeth {@literal faeth@em.uni-frankfurt.de}
 */
public class CoNLLStreamExtractor extends CoNLLRDFComponent {
	public static class Pair<F, S> {
		public F key;
		public S value;
		public Pair (F key, S value) {
			this.key = key;
			this.value = value;
		}
		public F getKey() {
			return key;
		}
		public void setKey(F key) {
			this.key = key;
		}
		public S getValue() {
			return value;
		}
		public void setValue(S value) {
			this.value = value;
		}
	}

	private static Logger LOG = Logger.getLogger(CoNLLStreamExtractor.class.getName());

	@SuppressWarnings("serial")
	private static List<Integer> CHECKINTERVAL = new ArrayList<Integer>() {{add(3); add(10); add(25); add(50); add(100); add(200); add(500);}};

	static final int MAXITERATE = 999; // maximal update iterations allowed until the update loop is canceled and an error msg is thrown - to prevent faulty update scripts running in an endless loop


	private String baseURI;
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


	private List<String> columns = new ArrayList<String>();
	private String select = null;
	List<Pair<String, String>> updates = new ArrayList<Pair<String, String>>();

	private void processSentenceStream() throws Exception {
		CoNLL2RDF conll2rdf = new CoNLL2RDF(baseURI, columns.toArray(new String[columns.size()]));
		List<Pair<Integer,Long> > dRTs = new ArrayList<Pair<Integer,Long> >(); // iterations and execution time of each update in seconds
		LOG.info("process input ..");
		BufferedReader in = getInputStream();
		OutputStreamWriter out = new OutputStreamWriter(getOutputStream());
		String buffer = "";
		for(String line = ""; line !=null; line=in.readLine()) {
			if(line.contains("#")) {// trim.matches(^#)
				out.write(line.replaceAll("^[^#]*#", "#") + "\n");
			}
			line=line.replaceAll("<[\\/]?[psPS]( [^>]*>|>)","").trim(); 		// in this way, we can also read sketch engine data and split at s and p elements
			if(!(line.matches("^<[^>]*>$")))							// but we skip all other XML elements, as used by Sketch Engine or TreeTagger chunker
				if(line.equals("") && !buffer.trim().equals("")) {
					Model m = conll2rdf.conll2model(new StringReader(buffer+"\n"));
					if(m!=null) { // null if an error occurred
						List<Pair<Integer,Long> > ret = update(m, updates);
						if (dRTs.isEmpty())
							dRTs = ret;
						else
							for (int x = 0; x < ret.size(); ++x)
								dRTs.set(x, new Pair<Integer, Long>(dRTs.get(x).getKey() + ret.get(x).getKey(), dRTs.get(x).getValue() + ret.get(x).getValue()));
						print(m,select, out);
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
					dRTs.set(x, new Pair<Integer, Long>(dRTs.get(x).getKey() + ret.get(x).getKey(), dRTs.get(x).getValue() + ret.get(x).getValue()));
			print(m,select,out);
		}
		if (!dRTs.isEmpty())
			LOG.debug("Done - List of interations and execution times for the updates done (in given order):\n\t\t" + dRTs.toString());

		getOutputStream().close();

	}

	public List<Pair<Integer,Long> > update(Model m, List<Pair<String,String>> updates) {
		List<Pair<Integer,Long> > result = new ArrayList<Pair<Integer,Long> >();
		for(Pair<String,String> update : updates) {
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
			result.add(new Pair<Integer, Long>(v, System.currentTimeMillis() - startTime));
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
			//System.err.println("!!!"+cols);
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



	public static void main(String[] argv) throws Exception {
		LOG.info("synopsis: CoNLLStreamExtractor baseURI FIELD1[.. FIELDn] [-u SPARQL_UPDATE1..m] [-s SPARQL_SELECT]\n"+
				"\tbaseURI       CoNLL base URI, cf. CoNLL2RDF\n"+
				"\tFIELDi        CoNLL field label, cf. CoNLL2RDF\n"+
				"\tSPARQL_UPDATE SPARQL UPDATE (DELETE/INSERT) query, either literally or its location (file/uri)\n"+
				"\t              can be followed by an optional integer in {}-parentheses = number of repetitions\n"+
				"\t              The SPARQL_UPDATE parameter is DEPRECATED - please use CoNLLRDFUpdater instead!\n"+
				"\tSPARQL_SELECT SPARQL SELECT statement to produce TSV output\n"+
				"\treads CoNLL from stdin, splits sentences, creates CoNLL RDF, applies SPARQL queries");

		String baseURI = argv[0];
		List<String> fields = new ArrayList<String>();
		List<Pair<String, String>> updates = new ArrayList<Pair<String, String>>();
		String select = null;
		BufferedReader inputStream = new BufferedReader(new InputStreamReader(System.in));

		int i = 1;
		while(i<argv.length && !argv[i].toLowerCase().matches("^-+u$"))
			fields.add(argv[i++]);
		while(i<argv.length && argv[i].toLowerCase().matches("^-+u$")) i++;
		while(i<argv.length && !argv[i].toLowerCase().matches("^-+s$")) {
			String freq;
			freq = argv[i].replaceFirst(".*\\{([0-9u*]+)\\}$", "$1");
			if (argv[i].equals(freq))
				freq = "1";
			else if (freq.equals("u"))
				freq = "*";
			String update =argv[i++].replaceFirst("\\{[0-9*]+\\}$", "");
			updates.add(new Pair<String, String>(update, freq));
		}
		while(i<argv.length && argv[i].toLowerCase().matches("^-+s$")) i++;
		if(i<argv.length)
			select=argv[i++];
		while(i<argv.length)
			select=select+" "+argv[i++]; // because queries may be parsed by the shell (Cygwin)

		if (fields.size() == 0) { // might be conllu plus, we check the first line for col names.
			fields = CoNLL2RDF.findFieldsFromComments(inputStream, 1);
		}


		LOG.info("running CoNLLStreamExtractor");
		LOG.info("\tbaseURI:       "+baseURI);
		LOG.info("\tCoNLL columns: "+fields);
		LOG.info("\tSPARQL update: "+updates);
		LOG.info("\tSPARQL select: "+select);

		LOG.info("read SPARQL ..");
		//UpdateRequest request = UpdateFactory.create();
		StringBuilder sb = new StringBuilder();
		for(i = 0; i<updates.size(); i++) {
			Reader sparqlreader = new StringReader(updates.get(i).getKey());
			File f = new File(updates.get(i).getKey());
			URL u = null;
			try {
				u = new URL(updates.get(i).getKey());
			} catch (MalformedURLException e) {}

			if(f.exists()) {			// can be read from a file
				sparqlreader = new FileReader(f);
				sb.append("f");
			} else if(u!=null) {
				try {
					sparqlreader = new InputStreamReader(u.openStream());
					sb.append("u");
				} catch (Exception e) {}
			}

			updates.set(i,new Pair<String, String>("", updates.get(i).getValue()));
			BufferedReader in = new BufferedReader(sparqlreader);
			for(String line = in.readLine(); line!=null; line=in.readLine())
				updates.set(i,new Pair<String, String>(updates.get(i).getKey()+line+"\n",updates.get(i).getValue()));
			sb.append(".");
		}
		sb.append(".");

		if(select!=null) {
			Reader sparqlreader = new StringReader(select);
			File f = new File(select);
			URL u = null;
			try {
				u = new URL(select);
			} catch (MalformedURLException e) {}

			if(f.exists()) {			// can be read from a file
				sparqlreader = new FileReader(f);
				sb.append("f");
			} else if(u!=null) {
				try {
					sparqlreader = new InputStreamReader(u.openStream());
					sb.append("u");
				} catch (Exception e) {}
			}

			BufferedReader in = new BufferedReader(sparqlreader);
			select="";
			for(String line = in.readLine(); line!=null; line=in.readLine())
				select=select+line+"\n";
		}
		sb.append(". ok");
		LOG.info(sb.toString());


		CoNLLStreamExtractor ex = new CoNLLStreamExtractor();
		ex.setBaseURI(baseURI);
		ex.setColumns(fields);
		ex.setUpdates(updates);
		ex.setSelect(select);
		ex.setInputStream(inputStream);
		ex.setOutputStream(System.out);

		ex.processSentenceStream();

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

	@Override
	public void start() {
		run();
	}
}