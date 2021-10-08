package org.acoli.conll.rdf;

import java.io.StringWriter;

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
    
}
