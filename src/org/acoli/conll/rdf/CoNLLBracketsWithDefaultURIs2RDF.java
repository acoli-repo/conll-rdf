package org.acoli.conll.rdf;

import java.io.IOException;
import java.io.Writer;

/** generate URIs by numbering brackets 
 *  TODO: per column*/
public class CoNLLBracketsWithDefaultURIs2RDF extends CoNLLBrackets2RDF {

	/** counter to generate consecutive node ids */
	protected int brackets = 0;
	
	public CoNLLBracketsWithDefaultURIs2RDF(String baseURI, String[] fields, Writer out) throws IOException {
		super(baseURI, fields, out);
	}

	public CoNLLBracketsWithDefaultURIs2RDF(String baseURI, String[] fields) throws IOException {
		super(baseURI, fields);
	}

	public CoNLLBracketsWithDefaultURIs2RDF(Format2RDF other) throws IOException {
		super(other);
	}

	/** @param argv baseURI field1 field2 ... (see variable <code>help</code> and method <code>conll2ttl</code>) */
	public static void main(String[] argv) throws Exception {		
		Format2RDF.main("CoNLLBracketsWithDefaultURIs",argv);
	}
	
	/** generate URIs from consecutive number */
	protected String getURI(String[] lines, int i) {
		if(lines[i].startsWith("(")) // if this is indeed a node
			return ":b"+(brackets++);
		return null;
	}
}
