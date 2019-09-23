package org.acoli.conll.rdf;

import java.io.*;
import java.util.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** support for XML-enhanced TSV formats as used by SketchEngine, CWB and the TreeTagger chunker <br/>
 * 	captures SGML/XML markup only, process TSV content via CoNLL2RDF */
public class XMLTSV2RDF extends Format2RDF {

	/** counter variable for generating continuous IDs for xml elements */
	protected int xml=0;
	
	/** markup elements that indicate sentence breaks (here: HTML and TEI/XML) */
	protected final static List<String> breakingMarkupElements = Arrays.asList(
			// breaking elements in HTML
			"head","header","nav", "section", "article", "table", "caption", "br", "body", "h1", "h2", "h3", 
			"h4", "h5", "h6", "p",
			// breaking elements in TEI/XML
			"abstract","bibl","biblFull","biblStruct","body","caption","category","collection","div","div1","div2",
			"div3","div4","div5","div6","div7","entry","entryFree","epigraph","epilogue","fDescr","fDecl",
			"figDesc","fileDesc","floatingText","front","handDesc","handNote","handNotes","head","headItem",
			"history","hom","imprint","meeting","monogr","objectDesc","p","profileDesc","projectDesc","prologue",
			"publicationStmt","recordingStmt","respStmt","revisionDesc","roleDesc","s","scriptDesc","seriesStmt",
			"settingDesc","sourceDesc","sp","specDesc","spGrp","table","teiCorpus","teiHeader","text","title",
			"titlePage","titleStmt","trailer","transcriptionDesc","typeDesc","u","witStart"
			);
	
	/** for handling TSV data structures */
	private final CoNLL2RDF conll2rdf;
	
	public XMLTSV2RDF(String baseURI, String[] fields, Writer out) throws IOException {
		super(baseURI, fields, out);
		this.conll2rdf = new CoNLL2RDF(baseURI,fields,out);
		// System.err.println("XMLTSV");
	}

	public XMLTSV2RDF(String baseURI, String[] fields) throws IOException {
		this(baseURI,fields,new OutputStreamWriter(System.out));
	}

	
	/** @param argv baseURI field1 field2 ... (see variable <code>help</code> and method <code>conll2ttl</code>) */
	public static void main(String[] argv) throws Exception {		
		Format2RDF.main("XMLTSV",argv);
	}

	/** we extract markup elements (full/single lines only, as in CWB, SketchEngine, TreeTagger) and hand the rest over to CoNLL2RDF; for large files, this will be slow */
	void conll2ttl(Reader in, Writer out) throws IOException {

		BufferedReader bin = new BufferedReader(in);
		StringBuffer conll = new StringBuffer();
		Stack<String> stack = new Stack<String>();
		Stack<Integer> ids = new Stack<Integer>();
		StringBuffer tree = new StringBuffer();
		
		for(String line = ""; line!=null; line=bin.readLine()) {
			if(line.trim().matches("<[^>]*>$")) {
				if(!line.trim().startsWith("</"))
					xml++;
				if(breakingMarkupElements.contains(line.trim().replaceFirst("<","").replaceAll("[^a-z0-9].*",""))) {
					if(conll.toString().trim().length()>0) {
						tok=0;
						sent++;
						conll2rdf.conll2ttl(new StringReader(conll.toString()),out);
						out.write(getTTL(tree.toString()));
						//for(int i = stack.size()-1; i>=0; i--)
						//	out.write(ids.get(i)+": </"+stack.get(i).trim().substring(1).replaceFirst("[>\\s].*","")+">\n");
						tree=new StringBuffer();
						for(int i = 0; i<stack.size(); i++) {
							tree.append(ids.get(i)+": "+stack.get(i)+"\n");
						}
					}
					conll = new StringBuffer();
				}
				if(line.trim().startsWith("</")) {
					//tree.append(ids.peek()+": "+line+"\n");
					ids.pop();
					stack.pop();
				} else if(line.contains("/>")) {
					tree.append(xml+": "+line+"\n");					
				} else {
					tree.append(xml+": "+line+"\n");
					stack.push(line);
					ids.push(xml);
				}
			} else {
				if(line.trim().equals("") && conll.toString().trim().length()>0) { // new line => sentence ends
					tok=0;
					sent++;
					conll2rdf.conll2ttl(new StringReader(conll.toString()),out);
					conll = new StringBuffer();
					out.write(getTTL(tree.toString()));
					//for(int i = stack.size()-1; i>=0; i--)
						//out.write(ids.get(i)+": </"+stack.get(i).trim().substring(1).replaceFirst("[>\\s].*","")+">\n");
					tree=new StringBuffer();
					for(int i = 0; i<stack.size(); i++) {
						tree.append(ids.get(i)+": "+stack.get(i)+"\n");
					}
				} else {
					conll.append(line+"\n");
					line=line.replaceFirst("#.*","").trim();
					if(!line.equals("")) {
						tok++;
						String id_string = ""+tok;
						String[] field = line.split("\t");
						try {
							if(field2col.get("ID")!=null) id_string = field[field2col.get("ID")];
						} catch (ArrayIndexOutOfBoundsException e) {
						} catch (NumberFormatException e) {
						} // thrown in CoNLL2RDF
						String URI = ":s"+sent+"_"+id_string; 
						// URI naming scheme follows the German TIGER Corpus						
						tree.append(URI+"\n");
					}
				}
			}
		}
		
		if(conll.toString().trim().length()>0)
		conll2rdf.conll2ttl(new StringReader(conll.toString()),out);
		out.write(getTTL(tree.toString()));
		//for(int i = stack.size()-1; i>=0; i--)
			//out.write(ids.get(i)+": </"+stack.get(i).trim().substring(1).replaceFirst("[>\\s].*","")+">\n");
		out.flush();
	}

	/** convert internal element / wordURI representation to TTL */
	private String getTTL(String tmp) {
		String result = "";
		Stack<String> nodeURIs = new Stack<String>();
		String lastSibling = null;

		for(String line : tmp.split("\n")) {
			line=line.trim();
			if(line.startsWith(":")) { // WORD URI
				if(nodeURIs.size()>0) {
					if(lastSibling!= null) result=result+lastSibling+" powla:next "+line+" .\n";
					result=result+line+" powla:hasParent "+nodeURIs.peek()+" .\n";
				}
				lastSibling=line;
			} else if(line.contains("</")) {
				lastSibling = nodeURIs.pop();
			} else if(line.length()>0) {
				int id = Integer.parseInt(line.replaceFirst(":.*", ""));
				String element = line.replaceFirst("^[0-9]+: <([^>/\\s]+)[\\s>/].*", "$1");
				String atts = line.replaceFirst("^[0-9]+: <[^>/\\s]+","").replaceFirst("[/]?>$", "").trim();
				//result = result+id+": <"+element+" ["+atts+"]>\n";
				if(lastSibling!= null) result=result+lastSibling+" powla:next :x"+id+". ";
				result=result+":x"+id;
				if(nodeURIs.size()>0) {
					result=result+" powla:hasParent "+nodeURIs.peek()+";";
				}
				result=result+" a powla:Node, conll:XML_DATA; rdf:value \""+element+"\" ";
				if(line.contains("/>")) {
					lastSibling=":x"+id;
				} else {
					lastSibling=null;
					nodeURIs.push(":x"+id);
				}
				while(atts.length()>0) {
					String att=atts.trim().replaceFirst("\\s[^\\s]=\".*", "");
					atts=atts.trim().substring(att.length()).trim();
					result=result+"; x:"+att.replaceFirst("=.*","").trim()+" \""+att.replaceFirst(".*=", "").trim().replaceFirst("^['\"](.*)['\"]$","$1")+"\"";
				}
				result=result+".\n";
			}
		}
		return result;
	}
}
