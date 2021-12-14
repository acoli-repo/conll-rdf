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
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;
import java.lang.reflect.*;
import org.apache.jena.rdf.model.*;	// Jena 3.x
//import com.hp.hpl.jena.rdf.model.*;		// Jena 2.x


/**
 * Convert CoNLL and similar TSV-based formats (e.g., SketchEngine) to a shallow RDF model.<br>
 * Produces a compact and NLP-friendly TTL representation of the original CoNLL file: one line per CoNLL row, empty cells skipped.<br>
 * Uses the provided base uri as empty prefix for word ids and conll: as prefix for conll relations<br>
 * e.g., <code>:s1.2 a nif:Word; conll:word "nsiirin"</code><br>
 * Note: setting BASE to base URI and using empty prefix for conll would be more compact but less readable<br>
 * e.g., <code>&lt;#s1.2&gt; a nif:Word; :word "nsiirin"</code>.
 * @author Christian Chiarcos {@literal chiarcos@informatik.uni-frankfurt.de}
 * @author Christian Faeth {@literal faeth@em.uni-frankfurt.de}
 **/
public class CoNLL2RDF extends Format2RDF{
	
	
	public CoNLL2RDF(String baseURI, String[] fields, Writer out) throws IOException {
		super(baseURI, fields, out);
	}
	
	public CoNLL2RDF(String baseURI, String[] fields) throws IOException {
		super(baseURI, fields);
	}

	/** @param argv baseURI field1 field2 ... (see variable <code>help</code> and method <code>conll2ttl</code>) */
	public static void main(String[] argv) throws Exception {		
		Format2RDF.main("CoNLL",argv);
	}
	
	/**
	 * See conll2ttl(Reader), but note that we write *only* to the specified writer.
	 * If this is not the pre-defined writer out, we define the prefixes.<br>
	 * This handling of writers is done to provide the core functionality for conll2ttl(Reader) and conll2model(Reader).<br>
	 * NOTE: make sure to finish the input with a newline character
	 * NOTE2: now skipping comments (todo: reenable)
	 */
	protected void conll2ttl(Reader in, Writer out) throws IOException {
		writePrefixes(out);

		BufferedReader bin = new BufferedReader(in);
		String sentence = "";
		ArrayList<String> predicates = new ArrayList<String>();
		String root = ":s"+sent+"_"+0;
		TreeSet<String> argTriples = new TreeSet<String>();
		Set<String> argsProperties = new TreeSet<String>();
		Set<String> headSubProperties = new TreeSet<String>();
		
		for(String line = ""; line!=null; line=bin.readLine()) {
			//if(line.contains("#"))
				//out.write(line.replaceFirst("^[^#]*","")); // uncomment to keep commentaries
			line=line.replaceAll("<[\\/]?[psPS]( [^>]*>|>)","").trim(); 		// in this way, we can also read sketch engine data and split at s and p elements
			if(!(line.trim().matches("^<[^>]*>$"))) {							// but we skip all other XML elements, as used by Sketch Engine or TreeTagger chunker
				root = ":s"+sent+"_"+0;
				if(line.trim().equals("") && !sentence.equals("")) {
					for(String arg : argTriples)
						sentence=sentence+".\n"+arg;
					sentence=sentence+"."+
							//" # offset="+pos+
							"\n";
					// sentence=sentence+".\n";
					argTriples.clear();
					if(col2field.get(col2field.size()-1).toLowerCase().matches(".*args")) {
						for(int i = 0; i<predicates.size(); i++) {
							sentence=sentence.replaceAll("_TMP_"+col2field.get(col2field.size()-1).replaceFirst("[\\-_]*[Aa][rR][gG][sS]$","_"+i),predicates.get(i));
						}
					}
					out.write(sentence+"\n");
					out.flush();
					predicates.clear();
					sentence="";
					tok=0;
					sent++;
				} else {
					// if(line.contains("#")) out.write(line.replaceFirst("^[^#]*#", "#")+"\n");
					// uncomment to keep comments
					line=line.replaceFirst("#.*","").trim();
					if(!line.equals("")) {
						if(sentence.equals("")) {
							if(sent>1) { 
								String lastRoot = ":s"+(sent-1)+"_"+0;
								sentence=sentence+lastRoot+" nif:nextSentence "+root+".\n";
							}
							sentence=sentence+root+" a nif:Sentence.\n";
						}
						tok++;
						String id_string = ""+tok;
						String[] field = line.split("\t");
						try {
							if(field2col.get("ID")!=null) id_string = field[field2col.get("ID")];
						} catch (ArrayIndexOutOfBoundsException e) {
							throw new ArrayIndexOutOfBoundsException("if defined as label, the ID column is obligatory");
						} catch (NumberFormatException e) {
							throw new NumberFormatException("the ID column must contain integers, only");
						}
						String URI = ":s"+sent+"_"+id_string; // URI naming scheme follows the German TIGER Corpus
						
						if(tok>1)
							sentence=sentence+"; nif:nextWord "+URI+"."+
							//" # offset="+pos+
							"\n";
						sentence=sentence+URI+" a nif:Word";
						for(int i = 0; i<field.length; i++) {
							field[i]=field[i].trim();
							if(!empty.matcher(field[i]).matches()) {
								if(i<col2field.size() && col2field.get(i).toLowerCase().equals("word"))
									pos=pos+field[i].trim().length();
								if(i<col2field.size() && col2field.get(i).toLowerCase().matches("^head[0-9]*$")) {
									sentence=sentence+"; conll:"+col2field.get(i)+" :s"+sent+"_"+field[i].trim();
									// we do that with a SPARQL query now
									//if(col2field.get(i).equals("HEAD")) {
									//	sentence=sentence+"; conll:"+field[field2col.get("EDGE")].trim()+" :s"+sent+"_"+field[i].trim(); // easy querying
									//	headSubProperties.add("conll:"+field[field2col.get("EDGE")].trim()+" rdfs:subPropertyOf conll:HEAD.");
									//}
								} else if (i<col2field.size()-1 || (i==col2field.size()-1 && !col2field.get(col2field.size()-1).toLowerCase().matches(".*args$")))
									sentence=sentence+"; conll:"+col2field.get(i)+" \""+field[i].trim().replaceAll("&","&amp;").replaceAll("\"","&quot;").replaceAll("\\\\","\\\\\\\\")+"\"";
								else if (col2field.get(col2field.size()-1).toLowerCase().matches(".*args$")) {
									// works fine, but yields wrong direction // sentence=sentence+"; conll:"+field[i].trim()+" _TMP_"+col2field.get(col2field.size()-1).replaceFirst("[\\-_]*[Aa][rR][gG][sS]$","_"+(i+1-col2field.size()));
									argTriples.add(
										"_TMP_"+col2field.get(col2field.size()-1).replaceFirst("[\\-_]*[Aa][rR][gG][sS]$","_"+(i+1-col2field.size()))+
										" conll:"+field[i].trim()+
										" "+URI);
									argsProperties.add("conll:"+field[i].trim()+" "+
										"rdfs:subPropertyOf "+
										"conll:"+col2field.get(col2field.size()-1).replaceFirst("([\\-_]*[Aa][rR][gG])[sS]$","$1")+".");
								}
								if(i<col2field.size() && col2field.get(i).equals(col2field.get(col2field.size()-1).replaceFirst("[\\-_]*[Aa][rR][gG][sS]$","")))
									predicates.add(URI);
								pos++;
							}
						}
						if(field2col.get("HEAD")==null) // if no HEAD annotation available, mark everything as depending on root
							sentence=sentence+"; conll:HEAD "+root;
					}
				}
			}
		}
			if(!sentence.equals("")) {
				for(String arg : argTriples)
					sentence=sentence+".\n"+arg;
				sentence=sentence+".\n";
				argTriples.clear();
				if(col2field.get(col2field.size()-1).toLowerCase().matches(".*args$"))
					for(int i = 0; i<predicates.size(); i++)
						sentence=sentence.replaceAll("\\?"+col2field.get(col2field.size()-1).replaceFirst("[\\-_]*[Aa][rR][gG][sS]$","+"+i),predicates.get(i));
				out.write(sentence); //+"\n");
			//out.write("\n");
			for(String p : headSubProperties) 
				out.write(p.trim()+"\n");
			//out.write("\n");
			for(String p : argsProperties)
				out.write(p.trim()+"\n");
		}
			out.flush();			
			
			if(tok>0) {
				sent++;
				tok=0;
			}
	}
}