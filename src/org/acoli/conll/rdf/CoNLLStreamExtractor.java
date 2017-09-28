import java.io.*;
import java.net.*;
import java.util.*; 
// import org.apache.jena.rdf.model.*;	// Jena 3.x
import com.hp.hpl.jena.rdf.model.*;		// Jena 2.x
import com.hp.hpl.jena.update.*;
import com.hp.hpl.jena.query.*;

/** extracts RDF data from CoNLL files, transforms the result using SPARQL UPDATE queries,
 * 	optionally followed by SPARQL SELECT to produce TSV output<br>
 *  NOTE: queries can be provided as literal queries, file or as URLs, the latter two are preferred as
 *  a literal query string may be parsed by the JVM/shell
 */
public class CoNLLStreamExtractor {
	
	public static void main(String[] argv) throws Exception {
		System.err.println("synopsis: CoNLLStreamExtractor baseURI FIELD1[.. FIELDn] [-u SPARQL_UPDATE1..m] [-s SPARQL_SELECT]\n"+
			"\tbaseURI       CoNLL base URI, cf. CoNLL2RDF\n"+
			"\tFIELDi        CoNLL field label, cf. CoNLL2RDF\n"+
			"\tSPARQL_UPDATE SPARQL UPDATE (DELETE/INSERT) query, either literally or its location (file/uri)\n"+
			"\t              can be followed by an optional integer in {}-parentheses = number of repetitions\n"+
			"\tSPARQL_SELECT SPARQL SELECT statement to produce TSV output\n"+
			"reads CoNLL from stdin, "
			+ "splits sentences, "
			+ "creates CoNLL RDF, "+
			"applies SPARQL queries");
		
		String baseURI = argv[0];
		List<String> fields = new ArrayList<String>();
		List<String> updates = new ArrayList<String>();
		String select = null;
		
		int i = 1;
		while(i<argv.length && !argv[i].toLowerCase().matches("^-+u$"))
			fields.add(argv[i++]);
		while(i<argv.length && argv[i].toLowerCase().matches("^-+u$")) i++;
		while(i<argv.length && !argv[i].toLowerCase().matches("^-+s$")) {
			int freq = 1;
			try {
				freq = Integer.parseInt(argv[i].replaceFirst(".*\\{([0-9]+)\\}$", "$1"));
			} catch (NumberFormatException e) {}
			String update =argv[i++].replaceFirst("\\{[0-9]+\\}$", "");
			while(freq>0) {
				updates.add(update);
				freq--;
			}
		}
		while(i<argv.length && argv[i].toLowerCase().matches("^-+s$")) i++;
		if(i<argv.length)
			select=argv[i++];
		while(i<argv.length)
			select=select+" "+argv[i++]; // because queries may be parsed by the shell (Cygwin)
		
		System.err.println("running CoNLLStreamExtractor");
		System.err.println("\tbaseURI:       "+baseURI);
		System.err.println("\tCoNLL columns: "+fields);
		System.err.println("\tSPARQL update: "+updates);
		System.err.println("\tSPARQL select: "+select);
		
		System.err.print("read SPARQL ..");
		//UpdateRequest request = UpdateFactory.create();
		for(i = 0; i<updates.size(); i++) {
			Reader sparqlreader = new StringReader(updates.get(i));
			File f = new File(updates.get(i));
			URL u = null;
			try {
				u = new URL(updates.get(i));
			} catch (MalformedURLException e) {}
			
			if(f.exists()) {			// can be read from a file
				sparqlreader = new FileReader(f);
				System.err.print("f");
			} else if(u!=null) {
				try {
					sparqlreader = new InputStreamReader(u.openStream());
					System.err.print("u");
				} catch (Exception e) {}
			}

			updates.set(i,"");
			BufferedReader in = new BufferedReader(sparqlreader);
			for(String line = in.readLine(); line!=null; line=in.readLine())
				updates.set(i,updates.get(i)+line+"\n");
			System.err.print(".");
		}
		System.err.print(".");
		
		if(select!=null) {
			Reader sparqlreader = new StringReader(select);
			File f = new File(select);
			URL u = null;
			try {
				u = new URL(select);
			} catch (MalformedURLException e) {}
			
			if(f.exists()) {			// can be read from a file
				sparqlreader = new FileReader(f);
				System.err.print("f");
			} else if(u!=null) {
				try {
					sparqlreader = new InputStreamReader(u.openStream());
					System.err.print("u");
				} catch (Exception e) {}
			}

			BufferedReader in = new BufferedReader(sparqlreader);
			select="";
			for(String line = in.readLine(); line!=null; line=in.readLine())
				select=select+line+"\n";
		}		
		System.err.println(". ok");
		
		CoNLL2RDF conll2rdf = new CoNLL2RDF(baseURI, fields.toArray(new String[fields.size()]));
		
		System.err.print("process input ..");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		OutputStreamWriter out = new OutputStreamWriter(System.out);
		String buffer = "";
		for(String line = ""; line !=null; line=in.readLine()) {
			if(line.contains("#"))
				System.out.println(line.replaceAll("^[^#]*#", "#"));
			line=line.replaceAll("<[\\/]?[psPS]( [^>]*>|>)","").trim(); 		// in this way, we can also read sketch engine data and split at s and p elements
			if(!(line.matches("^<[^>]*>$")))							// but we skip all other XML elements, as used by Sketch Engine or TreeTagger chunker
				if(line.equals("") && !buffer.trim().equals("")) {
					Model m = conll2rdf.conll2model(new StringReader(buffer+"\n"));
					if(m!=null) { // null if an error occurred
						update(m, updates); 
						print(m,select, out);
					}
					buffer="";
				} else
					buffer=buffer+line+"\n";
		}
		if(!buffer.trim().equals("")) {
			Model m = conll2rdf.conll2model(new StringReader(buffer+"\n"));
			update(m,updates); 
			print(m,select,out);
		}
	}
	
	public static void update(Model m, List<String> updates) {
		for(String update : updates)
			UpdateAction.execute(UpdateFactory.create(update), m);
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