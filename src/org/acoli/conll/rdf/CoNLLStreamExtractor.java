package org.acoli.conll.rdf;

import java.io.*;
import java.net.*;
import java.util.*; 
// import org.apache.jena.rdf.model.*;	// Jena 3.x
import org.apache.jena.rdf.listeners.ChangedListener;
import org.apache.jena.rdf.model.*;		// Jena 2.x
import org.apache.jena.update.*;
import org.apache.log4j.Logger;
import org.apache.jena.query.*;
import javafx.util.Pair;

/** extracts RDF data from CoNLL files, transforms the result using SPARQL UPDATE queries,
 * 	optionally followed by SPARQL SELECT to produce TSV output<br>
 *  NOTE: queries can be provided as literal queries, file or as URLs, the latter two are preferred as
 *  a literal query string may be parsed by the JVM/shell
 */
public class CoNLLStreamExtractor {
	
	private static Logger LOG = Logger.getLogger(CoNLLStreamExtractor.class.getName());

	static final int MAXITERATE = 999; // maximal update iterations allowed until the update loop is canceled and an error msg is thrown - to prevent faulty update scripts running in an endless loop
	
	public static void main(String[] argv) throws Exception {
		LOG.info("synopsis: CoNLLStreamExtractor baseURI FIELD1[.. FIELDn] [-u SPARQL_UPDATE1..m] [-s SPARQL_SELECT]\n"+
			"\tbaseURI       CoNLL base URI, cf. CoNLL2RDF\n"+
			"\tFIELDi        CoNLL field label, cf. CoNLL2RDF\n"+
			"\tSPARQL_UPDATE SPARQL UPDATE (DELETE/INSERT) query, either literally or its location (file/uri)\n"+
			"\t              can be followed by an optional integer in {}-parentheses = number of repetitions\n"+
			"\t              or {u} to repeat unlimited (capped at 999)\n"+
			"\t              both option run until there are no more changes in the model\n"+
			"\tSPARQL_SELECT SPARQL SELECT statement to produce TSV output\n"+
			"\treads CoNLL from stdin, splits sentences, creates CoNLL RDF, applies SPARQL queries");
		
		String baseURI = argv[0];
		List<String> fields = new ArrayList<String>();
		List<Pair<String, String>> updates = new ArrayList<Pair<String, String>>();
		String select = null;
		
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
		
		CoNLL2RDF conll2rdf = new CoNLL2RDF(baseURI, fields.toArray(new String[fields.size()]));
		List<Pair<Integer,Long> > dRTs = new ArrayList<Pair<Integer,Long> >(); // iterations and execution time of each update in seconds
		LOG.info("process input ..");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		OutputStreamWriter out = new OutputStreamWriter(System.out);
		String buffer = "";
		for(String line = ""; line !=null; line=in.readLine()) {
			if(line.contains("#"))
				out.write(line.replaceAll("^[^#]*#", "#") + "\n");
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
		LOG.debug("Done - List of interations and execution times for the updates done (in given order):\n\t\t" + dRTs.toString());
	}
	
	public static List<Pair<Integer,Long> > update(Model m, List<Pair<String,String>> updates) {
		List<Pair<Integer,Long> > result = new ArrayList<Pair<Integer,Long> >();
		for(Pair<String,String> update : updates) {
			Long startTime = System.currentTimeMillis();
			ChangedListener cL = new ChangedListener();
			m.register(cL);
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
				if (change) change = cL.hasChanged();
				v++;
			}
			if (v == MAXITERATE)
				LOG.warn("Warning: MAXITERATE reached.");
			result.add(new Pair<Integer, Long>(v, System.currentTimeMillis() - startTime));
		}
		return result;
	}
		
	/** run either SELECT statement (cf. https://jena.apache.org/documentation/query/app_api.html) and return CoNLL-like TSV or just TTL <br>
	 *  Note: this CoNLL-like export has limitations, of course: it will export one property per column, hence, collapsed dependencies or 
	 *  SRL annotations cannot be reconverted */
	public static void print(Model m, String select, Writer out) throws IOException {
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
}