package org.acoli.conll.rdf;

import java.io.IOException;
import java.io.Writer;

/** generate URIs from URIs first and last child element <br/>
 *  in this way, co-extensional nodes are lumped together (as in NIF). this is sparser, but may be tricky
*/
public class CoNLLBracketsWithSpanURIs2RDF extends CoNLLBrackets2RDF {

	public CoNLLBracketsWithSpanURIs2RDF(String baseURI, String[] fields, Writer out) throws IOException {
		super(baseURI, fields, out);
	}

	public CoNLLBracketsWithSpanURIs2RDF(String baseURI, String[] fields) throws IOException {
		super(baseURI, fields);
	}

	public CoNLLBracketsWithSpanURIs2RDF(Format2RDF other) throws IOException {
		super(other);
	}

	/** @param argv baseURI field1 field2 ... (see variable <code>help</code> and method <code>conll2ttl</code>) */
	public static void main(String[] argv) throws Exception {		
		Format2RDF.main("CoNLLBracketsWithSpanURIs",argv);
	}
	
	/** generate URIs from the nif:Words it contains <br/>
	 *  Note that this is potentially lossy and error-prone as it merges non-branching nodes
	 *  (but it can be handy and is the default behavior of related vocabularies, e.g., NIF)
	 *  */
	protected String getURI(String[] lines, int i, String col) {
		if(lines[i].startsWith("(")) {// if this is indeed a node
			int first = i;
			while(first<lines.length && !lines[first].startsWith(":"))
				first++;
			while(!lines[first].startsWith(":") && first>0)
				first--;			
			int last = i+1;
			int open = 1;
			while(last<lines.length && open>0) { 
				open=open+lines[last].replaceAll("[^\\(]", "").length()-lines[last].replaceAll("[^\\)]", "").length();
				last++;
			}
			if(last==lines.length)
				last--;
			while(last>0 && !lines[last].startsWith(":")) { // only if none found
				last--;
			}
			return lines[first]+"_"+lines[last].replaceAll(".*:", "");
		}
		return null;
	}
}
