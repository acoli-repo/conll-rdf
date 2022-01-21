package org.acoli.conll.rdf;

import java.io.*;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** support for the bracketing notation as provided by PTB 
 *  at the moment, bracketing columns are auto-detected.
 *  decision heuristic: every colum whose cells are all either empty, contain * or a round bracket 
 *  note that we check the SRL bracketing notation only for the first column, it is then applied to *all* following columns
 *  warning: no support for SRL bracketing annotations, as there, the values become properties rather than object literals
 *  */
abstract class CoNLLBrackets2RDF extends Format2RDF {

	private static Logger LOG = LogManager.getLogger(Format2RDF.class.getName());
	
	/** marks columns that contain brackets, judging from the first sentence that  */
	protected final Boolean[] col2bracket;
	
	public CoNLLBrackets2RDF(String baseURI, String[] fields, Writer out) throws IOException {
		this(new CoNLL2RDF(baseURI, fields, out));
	}

	public CoNLLBrackets2RDF(String baseURI, String[] fields) throws IOException {
		this(new CoNLL2RDF(baseURI, fields));
	}
	
	protected CoNLLBrackets2RDF(Format2RDF other) throws IOException {
		super(other);
		col2bracket=new Boolean[super.col2field.size()]; // initialized with nulls, i.e., undetermined
		try {
			@SuppressWarnings("unused")
			XMLTSV2RDF tmp = (XMLTSV2RDF)other;
			LOG.warn("CoNLLBrackets2RDF should not wrap XMLTSV2RDF as this overrides the default sentence splitting, please revise");
		} catch (ClassCastException e) { // expected
		}
	}

	/** @param argv baseURI field1 field2 ... (see variable <code>help</code> and method <code>conll2ttl</code>) */
	public static void main(String[] argv) throws Exception {		
		Format2RDF.main("CoNLLBracketsWithDefaultURIs",argv);
	}

	/** note that we do not checking whether brackets are balanced <br/>
	 * Also note we only keep comments that start at the beginning of the line */
	void conll2ttl(Reader in, Writer out) throws IOException {
		
		// (1) heuristically spot bracketing columns
		if(Arrays.asList(col2bracket).contains(null)) {
			BufferedReader bin = new BufferedReader(in);
			StringBuffer buffer = new StringBuffer();
			Boolean[] myCol2bracket = Arrays.copyOf(col2bracket, col2bracket.length);
			for(String line = bin.readLine(); line!=null; line=bin.readLine()) {
				buffer.append(line+"\n");
				if(!line.trim().matches("^<.*>$") && !line.trim().startsWith("#")) {
					line=line.replaceFirst("^([^<*][^<\\\\])#.*", "$1");
					String[] fields = line.split("\t");
					if(fields.length>=col2field.size()) { // then, we're pretty sure to be in a CoNLL row ;)
						for(int i = 0; i<fields.length && i<col2bracket.length; i++)
							if(col2bracket[i]==null) {
								if(fields[i].matches(".*[()].*") && myCol2bracket[i]==null) myCol2bracket[i]=true;
								if(!empty.matcher(fields[i].trim()).matches() && !fields[i].trim().equals("*") && !fields[i].matches(".*[()].*"))
									myCol2bracket[i]=false;
							}
					}
				}
			}
			for(int i = 0; i<col2bracket.length; i++)
				col2bracket[i]=myCol2bracket[i];
			in = new StringReader(buffer.toString());
		}
		
		// (2) for all confirmed bracketing columns (col2bracket[i]=true): parse bracket structure
		//     note that we skip XML markup, and analyze it partially, only. Thus, do not wrap XMLTSV2RDF here
		BufferedReader bin = new BufferedReader(in);
		StringBuffer conll = new StringBuffer();
		Hashtable<Integer,StringBuffer> col2trees = new Hashtable<Integer,StringBuffer>();
		
		for(String line = ""; line!=null; line=bin.readLine()) {
			line=line.replaceAll("<[\\/]?[psPS]( [^>]*>|>)","").trim(); 		// in this way, we can also read sketch engine data and split at s and p elements
			if(!(line.trim().matches("^<[^>]*>$"))) {							// but we skip all other XML elements, as used by Sketch Engine or TreeTagger chunker
				if(!line.replaceFirst("^#.*","").trim().equals("")) tok++;
				
				String fields[] = line.split("\t");
				String id_string = ""+tok;
				
				try {
					if(field2col.get("ID")!=null) id_string = fields[field2col.get("ID")];
				} catch (ArrayIndexOutOfBoundsException e) {
				} catch (NumberFormatException e) {
				} // thrown in CoNLL2RDF
				String URI = ":s"+sent+"_"+id_string; 

				for(int i = 0; i<fields.length; i++) {
					if(i<this.col2bracket.length && this.col2bracket[i]!=null && this.col2bracket[i]) { // if in bracketing format (note: no SRL arguments supported)
						
						if(col2trees.get(i)==null) col2trees.put(i, new StringBuffer());
						
						if(empty.matcher(fields[i].trim()).matches()) {
							col2trees.get(i).append(URI+"\n");
						} else if(fields[i].contains("*")) {
							col2trees.get(i).append(fields[i].replaceFirst("\\*[^\\*]*$","")+"\n");
							col2trees.get(i).append(URI+"\n");
							col2trees.get(i).append(fields[i].replaceAll(".*\\*","")+"\n");
						} else if(fields[i].contains(")")) {
							col2trees.get(i).append(fields[i].replaceFirst("\\).*","")+"\n");
							col2trees.get(i).append(URI+"\n");
							col2trees.get(i).append(fields[i].replaceAll("^[\\)]*\\)", "\\)")+"\n");
						} else {
							col2trees.get(i).append(fields[i]+"\n");
							col2trees.get(i).append(URI+"\n");
						}
						
						// remove original annotation
						fields[i]="";
					}
				}
				line=fields[0];
				for(int i = 1; i<fields.length; i++)
					line=line+"\t"+fields[i];
			}
			conll.append(line+"\n");
			if(line.trim().equals("") && conll.toString().replaceAll("#[^\n]*","").trim().length()>0) {
				conll2rdf.conll2ttl(new StringReader(conll.toString().replaceAll("\\s*\n\\s*","\n")),out);
				conll=new StringBuffer();

				// trees
				for(int i : new TreeSet<Integer>(col2trees.keySet())) {
					String f = this.col2field.get(col2field.size()-1);
					if(i<col2field.size())
						f=this.col2field.get(i);
					out.write(getTTL(col2trees.get(i).toString(), f));
				}
				col2trees.clear();
				out.flush();
				
				this.sent++;
				this.tok=0;
				conll=new StringBuffer();				
				col2trees.clear();
			}
		}
		
		if(conll.toString().trim().length()>0 || !col2trees.isEmpty()) {
			conll2rdf.conll2ttl(new StringReader(conll.toString().replaceAll("\\s*\n\\s*","\n")),out);
			conll=new StringBuffer();

			// trees
			for(int i : new TreeSet<Integer>(col2trees.keySet())) {
				String f = this.col2field.get(col2field.size()-1);
				if(i<col2field.size())
					f=this.col2field.get(i);
				out.write(getTTL(col2trees.get(i).toString(), f));
			}
			col2trees.clear();
			out.flush();
		}

	}

	protected String getTTL(String string, String col) {
		String result="";
		string=string.replaceAll("\\(", "\n\\(").replaceAll("\\)","\\)\n").replaceAll("\\s*\n\\s*", "\n")+"\n";
		String lastSibling = null;

		Stack<String> nodes = new Stack<String>();
		String[] lines = string.split("\n");
		for(int i = 0; i<lines.length; i++) {
			String n = lines[i];
			if(n.startsWith(":")) {
			  if(nodes.size()>0) { // terminal in a powla:node
				if(lastSibling!=null)
					result=result+lastSibling+" powla:next "+n+". \n";
				result=result+n+" powla:hasParent "+nodes.peek()+" .\n";
				lastSibling=n;
			  } // else: skip URI
			} else if(n.startsWith("(")) {
				String uri = getURI(lines,i,col);
				String val = n.replaceFirst("^\\(", "").trim();
				if(lastSibling!=null && nodes.size()>0)
					result=result+lastSibling+" powla:next "+uri+". \n";
				result=result+uri+" a powla:Node, conll:"+col+" ";
				if(nodes.size()>0)
					result=result+"; powla:hasParent "+nodes.peek()+" "; // we use column labels as node types
				if(val.length()>0)
					result=result+"; rdf:value \""+val+"\" ";
				result=result+".\n";
				nodes.push(uri);
				lastSibling=null;
			} else if(n.endsWith(")")) {
				lastSibling=null;
				if(nodes.size()>0) lastSibling = nodes.pop();
			}
		}
		return result.replaceAll("\\s*\n\\s*", "\n").trim()+"\n";
	}

	/** implement different URI minting strategies, must only return a value if current line starts with "(" */
	abstract protected String getURI(String[] lines, int i, String col);
}