package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.zip.GZIPInputStream;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

public class CoNLLRDFUtil {

    /**
     * FOR LEO: please move whereever you like
     * 
     * @param model CoNLL-RDF sentence as Model
     * @return String[0]: all comments + \n String[1]: model as Turtle (unsorted)
     *         concatenate: Full CoNLL-RDF output
     */
    public static String conllRdfModel2String(Model model) {
        final String comments = rdfComments2String(model);
    
    	// generate CoNLL-RDF Turtle (unsorted)
    	StringWriter modelOut = new StringWriter();
    	model.write(modelOut, "TTL");
    	final String modelString = modelOut.toString();

    	return comments + modelString;
    }

    /**
     * @param model CoNLL-RDF sentence as Model
     * @return String: all comments + \n
     */
    private static String rdfComments2String(Model model) {
        // generate comments in out[0]
    	String out = new String();
    	String selectComments = "PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"
    			+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
    			+ "SELECT ?c WHERE {?x a nif:Sentence . ?x rdfs:comment ?c}";
    	QueryExecution qexec = QueryExecutionFactory.create(selectComments, model);
    	ResultSet results = qexec.execSelect();
    	while (results.hasNext()) {
    		// TODO please check the regex. Should put a # in front of every line, which does not
    		// already start with #.
    		out += results.next().getLiteral("c").toString().replaceAll("^([^#])", "#\1") + "\n";
    	}
        return out;
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
    static String readInURI(URI uri) throws MalformedURLException, IOException {
    	String result = null;
    	try {
    		result = uri.toString();
    		if (result != null && result.endsWith(".gz")) {
    			StringBuilder sb = new StringBuilder();
    			BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(uri.toURL().openStream())));
    			for (String line; (line = br.readLine()) != null; sb.append(line));
    			result = sb.toString();
    		}
    	} catch (Exception ex) {
    		CoNLLRDFUpdater.LOG.error("Excpetion while reading " + uri.getPath());
    		throw ex;
    	}
    	return result;
    }
    
}
