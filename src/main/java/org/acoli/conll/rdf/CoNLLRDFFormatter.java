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
import java.util.*;
import org.apache.jena.rdf.model.*;		// Jena 2.x
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.cli.ParseException;
import org.apache.jena.query.*;


/** reads CoNLL-RDF from stdin, writes it formatted to stdout (requires a Un*x shell)<br>
 *  this is basically for diagnostic purposes 
 *  @author Christian Chiarcos {@literal chiarcos@informatik.uni-frankfurt.de}
 *  @author Christian Faeth {@literal faeth@em.uni-frankfurt.de}
 */
public class CoNLLRDFFormatter extends CoNLLRDFComponent {
	
	protected static Logger LOG = LogManager.getLogger(CoNLLRDFFormatter.class.getName());
		
	public static final String ANSI_RESET    = "\u001B[0m";
	public static final String ANSI_BRIGHTER = "\u001B[1m";
	public static final String ANSI_ULINE    = "\u001B[4m";
	public static final String ANSI_FLASH	 = "\u001B[5m";
	public static final String ANSI_BLACK    = "\u001B[30m";
	public static final String ANSI_RED      = "\u001B[31m";
	public static final String ANSI_GREEN    = "\u001B[32m";
	public static final String ANSI_YELLOW   = "\u001B[33m";
	public static final String ANSI_BLUE     = "\u001B[34m";
	public static final String ANSI_PURPLE   = "\u001B[35m";
	public static final String ANSI_CYAN     = "\u001B[36m";
	public static final String ANSI_WHITE    = "\u001B[37m";
	public static final String ANSI_BLACK_BK = "\u001B[40m";
	public static final String ANSI_RED_BK   = "\u001B[41m";
	public static final String ANSI_GREEN_BK = "\u001B[42m";
	public static final String ANSI_YLW_BK   = "\u001B[43m";
	public static final String ANSI_BLUE_BK  = "\u001B[44m";
	public static final String ANSI_PPL_BK   = "\u001B[45m";
	public static final String ANSI_CYAN_BK  = "\u001B[46m";
	public static final String ANSI_WHITE_BK = "\u001B[47m";
	
	public class Module {
		private Mode mode = Mode.CONLLRDF;
		private List<String> cols = new ArrayList<String>();
		String select = "";
		private PrintStream outputStream;
		
		public Mode getMode() {
			return mode;
		}

		public void setMode(Mode mode) {
			this.mode = mode;
		}

		public List<String> getCols() {
			return cols;
		}

		public void setCols(List<String> cols) {
			this.cols = cols;
		}

		public String getSelect() {
			return select;
		}

		public void setSelect(String select) {
			this.select = select;
		}

		public PrintStream getOutputStream() {
			if (outputStream != null) {
				return outputStream;
			} else {
				// Retrieve outputStream of the enclosing Formatter
				return new PrintStream(CoNLLRDFFormatter.this.getOutputStream());
			}
		}

		public void setOutputStream(PrintStream outputStream) {
			this.outputStream = outputStream;
		}
	}
	
	public static enum Mode {
		CONLL, CONLLRDF, DEBUG, QUERY, GRAMMAR, SEMANTICS, GRAMMAR_SEMANTICS
	}

	private List<Module> modules = new ArrayList<Module>();
	
	public List<Module> getModules() {
		return modules;
	}
	public Module addModule(Mode mode) {
		Module module = new Module();
		module.setMode(mode);
		modules.add(module);
		return module;
	}

		/** do some highlighting, but provide the full TTL data*/
		public String colorTTL(String buffer) {
			return buffer.replaceAll("(terms:[^ ]*)",ANSI_YLW_BK+"$1"+ANSI_RESET)
						.replaceAll("(rdfs:label +)(\"[^\"]*\")","$1"+ANSI_CYAN+"$2"+ANSI_RESET)
						.replaceAll("(nif:[^ ]*)",ANSI_YELLOW+"$1"+ANSI_RESET)
						.replaceAll("(conll:[^ \n]*)([^;\n]*[;]?)",ANSI_CYAN_BK+ANSI_BRIGHTER+ANSI_BLUE+"$1"+ANSI_RESET+ANSI_CYAN_BK+ANSI_BRIGHTER+"$2"+ANSI_RESET);
		}
		
		/** default: do not return type assignments */
		protected static String extractCoNLLGraph(String buffer) {
			return extractCoNLLGraph(buffer,false);
		}

		/** buffer must be valid turtle, produces an extra column for terms: type assignments */
		protected static String extractCoNLLGraph(String buffer, boolean includeTermConcepts) {
			Model m = null;
			try {
				m = ModelFactory.createDefaultModel().read(new StringReader(buffer),null, "TTL");
			} catch (org.apache.jena.riot.RiotException e) {
				e.printStackTrace();
				LOG.error("while reading:\n"+buffer);
			}
			Vector<String> ids = new Vector<String>();
			Vector<String> words = new Vector<String>();
			Vector<String> annos = new Vector<String>();
			Vector<Integer> depth = new Vector<Integer>();
			Vector<String> edges = new Vector<String>();
			Vector<String> headDir = new Vector<String>();
			Vector<String> terms = new Vector<String>();
 			Integer maxDepth = 0;
 			Integer maxEdgeLength = 0;
 			Integer maxIdLength = 0;
 			Integer maxWordLength = 0;
 			Integer maxTermLength = 0;

			String word = null;
			try {
				word = QueryExecutionFactory.create(
					"PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"+
					"SELECT ?first WHERE { ?first a nif:Word. FILTER(NOT EXISTS{ [] nif:nextWord ?first })} LIMIT 1",
					m).execSelect().next().get("?first").toString();
				while(true) {
					ids.add(word.replaceAll(".*[\\\\/#:]", ""));
					maxIdLength=Math.max(maxIdLength, ids.get(ids.size()-1).length());
					try {
						words.add(
								QueryExecutionFactory.create(
										"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
										"SELECT ?word WHERE { <"+word+"> conll:WORD ?word } LIMIT 1",
										m).execSelect().next().get("?word").toString());
					} catch (NoSuchElementException e) {
						LOG.warn("Warning: no conll:WORD (WORD column) found");
						words.add("");
					}
					maxWordLength=Math.max(maxWordLength, words.get(words.size()-1).length());
					String anno = "";
					ResultSet annos_raw = QueryExecutionFactory.create(
							"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
							"SELECT ?rel ?val WHERE { <"+word+"> ?rel ?val \n"
									+ "FILTER(contains(str(?rel),'http://ufal.mff.cuni.cz/conll2009-st/task-description.html#'))\n"
									+ "FILTER(?rel!=conll:HEAD && ?rel!=conll:EDGE && ?rel!=conll:WORD) } ORDER BY ASC(?rel)",
							m).execSelect();
					String rel = "";
					while(annos_raw.hasNext()) {
						QuerySolution next = annos_raw.next();
						String nextRel = next.get("?rel").toString().replaceFirst(".*#","");
						if(!rel.equals(nextRel)) 
							anno=anno+
								ANSI_BLUE+ANSI_ULINE+
								nextRel+
								ANSI_RESET+" ";
						rel=nextRel;
						anno=anno+
								next.get("?val").toString().
								 replaceFirst("^http://purl.org/acoli/open-ie/(.*)$",ANSI_YLW_BK+"$1"+ANSI_RESET).
								 replaceFirst(".*#","")+
								" ";
					}
					
					// we append OLiA annotations to CoNLL annotations
					ResultSet olia_types= QueryExecutionFactory.create(
							"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
							"SELECT ?concept WHERE { <"+word+"> a ?concept \n"
									+ "FILTER(contains(str(?concept),'http://purl.org/olia'))\n"
									+ "} ORDER BY ASC(?val)",
							m).execSelect();
					while(olia_types.hasNext())
							anno=anno+
								ANSI_RED+
								olia_types.next().get("?concept").toString().replaceFirst("^.*/([^/]*)\\.(owl|rdf)[#/]","$1:")+
								ANSI_RESET+" ";

					// append OLiA features
					ResultSet olia_feats= QueryExecutionFactory.create(
							"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
							"SELECT ?rel ?concept WHERE { <"+word+"> ?rel ?val. ?val a ?concept.\n"
									+ "FILTER(contains(str(?rel),'http://purl.org/olia'))\n"
									+ "FILTER(contains(str(?concept),'http://purl.org/olia'))\n"
									+ "} ORDER BY ASC(?rel)",
							m).execSelect();
					while(olia_feats.hasNext()) {
						QuerySolution next = olia_feats.next();
						anno = anno+
								ANSI_RED+ANSI_ULINE+
								next.get("?rel").toString().replaceFirst("^.*/([^/]*)\\.(owl|rdf)[#/]","$1:")+
								ANSI_RESET+"."+ANSI_RED+
								next.get("?concept").toString().replaceFirst("^.*/([^/]*)\\.(owl|rdf)[#/]","$1:")+
								ANSI_RESET+" ";
					}					
					
					annos.add(anno);
					
					String head = "";
					try {
						head = 
								QueryExecutionFactory.create(
										"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
										"SELECT ?head WHERE { <"+word+"> conll:HEAD ?head} LIMIT 1",
										m).execSelect().next().get("?head").toString();
						if(Integer.parseInt(head.replaceAll("[^0-9]","")) < Integer.parseInt(word.replaceAll("[^0-9]","")))
							headDir.add(" \\ "); 
						else 
							headDir.add(" / ");
					} catch (NumberFormatException e) {
						e.printStackTrace();
						if(head.compareTo(word)<1) headDir.add(" \\ "); else headDir.add(" / ");
					} catch (NoSuchElementException e) {
						headDir.add("   ");
					}
					
					try {
						depth.add(
								Integer.parseInt(QueryExecutionFactory.create(
								"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
								"SELECT (COUNT(DISTINCT ?head) AS ?depth) WHERE { <"+word+"> conll:HEAD+ ?head }",
								m).execSelect().next().get("?depth").toString().replaceFirst("^\"?([0-9]+)[\\^\"].*","$1")));
					} catch(NoSuchElementException e) {
						if(depth.size()==0) depth.add(1);
						else depth.add(depth.get(depth.size()-1));
					}
					maxDepth=Math.max(maxDepth, depth.get(depth.size()-1));


					try { // return the longest edge
						edges.add(
								QueryExecutionFactory.create(
										"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
										"PREFIX fn: <http://www.w3.org/2005/xpath-functions#>\n"+
										"SELECT ?edge ?length WHERE { <"+word+"> conll:EDGE ?edge. BIND(fn:string-length(?edge) AS ?length) } ORDER BY DESC(?length) LIMIT 1",
										m).execSelect().next().get("?edge").toString());
					} catch(NoSuchElementException e) {
						edges.add("");
					}
					maxEdgeLength=Math.max(maxEdgeLength,edges.get(edges.size()-1).length());
					
					String term = "";
					if(includeTermConcepts) {
						ResultSet terms_raw = QueryExecutionFactory.create(
								"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
								"SELECT ?term WHERE { <"+word+"> a ?term \n"
									+ "FILTER(contains(str(?term),'http://purl.org/acoli/open-ie/'))\n"
									+ " } ORDER BY ASC(?term)",
								m).execSelect();
						while(terms_raw.hasNext())
							term=term+terms_raw.next().get("?term").toString().
									replaceFirst("http://purl.org/acoli/open-ie/","")+" ";
									//replaceFirst("http://purl.org/acoli/open-ie/","terms:")+" ";
					}
					terms.add(term.trim());
					maxTermLength=Math.max(maxTermLength, term.trim().length());
					
					word = QueryExecutionFactory.create(
							"PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"+
							"SELECT ?next WHERE { <"+word+"> nif:nextWord ?next } LIMIT 1",
							m).execSelect().next().get("?next").toString();
				}
			} catch (NoSuchElementException e) {
			} catch(Exception e) {
				e.printStackTrace();
			}

			String result = "";
			
			
			for(int i = 0; i<words.size(); i++) {
				result=result+ids.get(i);
				for(int j = ids.get(i).length(); j<maxIdLength; j++)
					result=result+" ";
				result=result+ANSI_WHITE;
				for(int j=depth.get(i);j>0;j--)
					result=result+" .";
				result=result+ANSI_RESET;
				result=result+headDir.get(i);
				result=result+edges.get(i);
				for(int j = maxDepth-depth.get(i);j>0;j--)
					if(depth.get(i)>1) result=result+"--"; else result=result+"  ";
				for(int j = edges.get(i).length();j<maxEdgeLength;j++)
					if(depth.get(i)>1) result=result+"-"; else result=result+" ";
				result=result+" "+words.get(i);
				for(int j = words.get(i).length(); j<maxWordLength; j++)
					result=result+" ";
				result=result+" "+ANSI_YLW_BK+terms.get(i)+ANSI_RESET;
				for(int j = terms.get(i).length(); j<maxTermLength; j++)
					result=result+" ";
				result=result+" "+annos.get(i)+"\n";
			}			
			return result;
		}
			
		/** default: include type assignments */
		protected static String extractTermGraph(String buffer) {
			return extractTermGraph(buffer, true);
		}
		
		protected static String extractTermGraph(String buffer, boolean includeTermConcepts) {
			Model m = ModelFactory.createDefaultModel().read(new StringReader(buffer),null, "TTL");
			String word = null;
			String result = "";
			String s = "";
			String r = "";
			String o = "";
			try {
				// write original sentence
				ResultSet sentence = QueryExecutionFactory.create(
					"PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"
					+ "PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"
					+ "SELECT ?w ?word (COUNT(DISTINCT ?pre) AS ?pos)\n"
					+ "WHERE {\n"
					+ "?w conll:WORD ?word.\n"
					+ "?pre nif:nextWord* ?w.\n"
					+ "} GROUP BY ?w ?word ORDER BY ASC(?pos)",m).execSelect();
				while(sentence.hasNext())
					result=result+sentence.next().get("?word")+" ";							
				
				// write result set
				ResultSet semgraph = QueryExecutionFactory.create(
					"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
					+"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
					+"SELECT DISTINCT ?s ?sl ?r ?o ?ol ?in ?out\n"
					+"WHERE { "
					+ "?s ?r [].\n"
					+ "OPTIONAL { ?s ?r ?o }. \n"			//  ?o can be blank
					+ "FILTER(contains(concat(str(?r),str(?o)),'http://purl.org/acoli/open-ie/') &&\n"
					+ "       !contains(str(?r),'http://ufal.mff.cuni.cz/conll2009-st/task-description.html#'))\n"
					+ "OPTIONAL {?s rdfs:label ?sl }\n"
					+ "OPTIONAL {?o rdfs:label ?ol }\n"
					+ "BIND(xsd:integer(REPLACE(STR(?s),'[^0-9]','')) AS ?snr)\n"
					+ "BIND(xsd:integer(REPLACE(STR(?o),'[^0-9]','')) AS ?onr)\n"
					+ "{ FILTER(!BOUND(?snr)) BIND(?snr AS ?nr) } UNION"
					+ "{ FILTER(BOUND(?snr)) BIND(?onr AS ?nr) } \n"
					+ "OPTIONAL { SELECT ?s (COUNT(DISTINCT *) AS ?in)\n"
					+ "  WHERE { ?sin ?rin ?s FILTER(!ISBLANK(?sin)) FILTER(contains(str(?rin),'http://purl.org/acoli/open-ie/')) } GROUP BY ?s \n"
					+ "}"
					+ "OPTIONAL { SELECT ?s (COUNT(DISTINCT *) AS ?out)\n"
					+ "  WHERE { ?s ?rout ?sout FILTER(!ISBLANK(?sout)) FILTER(contains(str(?rout),'http://purl.org/acoli/open-ie/'))} GROUP BY ?s \n"
					+ "}"
					+ "}"
					+ "ORDER BY ASC(?nr) ASC(?snr) ASC(?onr) ?r ?s ?o",
					m).execSelect();
				while(semgraph.hasNext()) {
					QuerySolution next = semgraph.next();
					RDFNode sNode = next.get("?s");
					String nextS = sNode.toString().replaceAll(".*[#/]","");
					if(!sNode.isURIResource()) nextS="[]";
					if(next.get("?sl")!=null) nextS=nextS+" "+ANSI_CYAN+"\""+next.get("?sl")+"\""+ANSI_RESET;
					if(!nextS.equals(s)) {
						result=result+"\n"+nextS+" ("+
								("0"+next.get("?in")).replaceFirst("[^0-9].*","").replaceFirst("^0*([^0])","$1")+" > node > "+
								("0"+next.get("?out")).toString().replaceFirst("[^0-9].*","").replaceFirst("^0*([^0])","$1")+")";
					}
					String nextR = next.get("?r").toString()
							.replaceAll("http://ufal.mff.cuni.cz/conll2009-st/task-description.html#(.*)$",ANSI_BLUE+ANSI_ULINE+"$1"+ANSI_RESET) 
							.replaceAll("http://purl.org/acoli/open-ie/(.*)",ANSI_YLW_BK+"terms:$1"+ANSI_RESET)
							.replaceAll("http://www.w3.org/1999/02/22-rdf-syntax-ns#type","a");
					
					String nextO = next.get("?o").toString()
							.replaceAll("http://purl.org/acoli/open-ie/(.*)",ANSI_YLW_BK+"terms:$1"+ANSI_RESET)
							.replaceAll("[^ \t]*[#/]","");
					if(next.get("?ol")!=null)
						nextO=nextO+" "+ANSI_CYAN+"\""+next.get("?ol")+"\""+ANSI_RESET;
					
					if(!nextR.equals("a") || includeTermConcepts==true) {
						if(!nextS.equals(s) || !nextR.equals(r))
							result=result+"\n\t"+nextR;
						else if(!nextO.equals(o)) result=result+"; ";
						if(!nextS.equals(s) || !nextR.equals(r) || !nextO.equals(o)) {
							result=result+" "+nextO;
						}
					}
					s=nextS;
					r=nextR;
					o=nextO;
				}
			} catch (NoSuchElementException e) {
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result+"\n";
		}
		
		/** require that every line starts with a subject, sort: @ (prefix) & # (comment) > lines, lines sorted lexiconumerically, i.e., normalize length of integers (regardless of position) before sorting */
		protected static String reorderTTLBuffer(String buffer, List<String> cols) {
			String result ="";
			try {
				BufferedReader in = new BufferedReader(new StringReader(buffer));
				Hashtable<String,String> key2line = new Hashtable<String,String>();
				String line;
				while((line=in.readLine())!=null) {
					line=line.trim();
					if(line.startsWith("@")) result=result+line+"\n"; else
					if(line.startsWith("#")) result=result+line+"\n"; else 
					if(!line.equals("")) {
						//reorder columns according to user list.
						String orderedLine = "";
						List<String> statements = new ArrayList<String>(Arrays.asList(line.substring(0, line.lastIndexOf(".")-1).split(";\\s*\t"))); //TODO: only consider ; not ";"
						List<String> columns = new ArrayList<String>();
						 // Subject is always first. Change if complications occur.
						if (statements.get(0).contains("nif:Word")) {
							//do rdf:type reorder
							List<String> concepts = new ArrayList<String>(Arrays.asList(statements.get(0).split(",")));
							String[] subject = concepts.get(0).split("\\sa\\s");
							if (subject.length == 2) {
								orderedLine += subject[0] + " a nif:Word";
								if (!subject[1].contains("nif:Word")) {
									concepts.set(0, subject[1]);
								} else {
									concepts.remove(0);
								}
							} else {
								orderedLine += concepts.get(0);
								concepts.remove(0);
							}
							for (String concept:concepts) {
								if (concept.contains("nif:Word")) continue;
								orderedLine += ", " + concept.trim();
							}
						} else {
							orderedLine = statements.get(0).trim();
						}
						statements.remove(0);
						//do column reorder
						columns.add("nif:Word");
						columns.add("conll:WORD");
						columns.addAll(cols);
						for (String col:columns) {
							for (int i = 0; i < statements.size();i++) {
								if (statements.get(i).contains(col)) {
									orderedLine += "; " + statements.get(i).trim();
									statements.remove(i);
									break;
								}
							}
						}
						//add rest of columns to the end
						String nifnext = "";
						for (int i = 0; i < statements.size();i++) {
							if (statements.get(i).contains("nif:nextWord")) 
								nifnext = "; " + statements.get(i).trim();
							else
								orderedLine += "; " + statements.get(i).trim();
						}
						if (!orderedLine.equals("")) {
							orderedLine += nifnext + " .";
							line = orderedLine;
						} 
						
						
						//reorder lines
						String tmp=line.replaceAll("\t"," ").replaceAll("([^0-9])([0-9])","$1\t$2").replaceAll("([0-9])([^0-9])","$1\t$2"); 	// key \t-split
						String key="";
						for(String s : tmp.split("\t")) {
							if(s.matches("^[0-9]+$"))
								while(s.length()<64) s="0"+s;
							key=key+s;
						}
						key2line.put(key,line);
					}
				}
				List<String> keys = new ArrayList<String>(key2line.keySet());
				Collections.sort(keys);
				for(String key: keys)
					result=result+key2line.get(key)+"\n";
			} catch (IOException e) {
				e.printStackTrace();
			}
			return result;
		}

		/** note: the last column must contain literal values, not HEAD */
		public static String columnsAsSelect(List<String> cols) {
			String select = ""
			+ "PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"
			+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
			+ "PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"
			+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
			
			+ "SELECT ";
			for (String col:cols) {
				select += "?"+col+" ";
			}
			
			select += "{\n";
			select += "	SELECT \n";
			select += "	?sid ?wid \n";
			
			for (String col:cols) {
				select += "	(group_concat(?"+col+"s;separator='|') as ?"+col+")\n";
			}
			
			String lastCol = cols.get(cols.size()-1);
			
			select += "	WHERE {\n";
			select += "		?word a nif:Word .\n";
			select += "		{\n";
			select += "			SELECT ?word (count(distinct ?preS) as ?sid) (count(distinct ?pre) as ?wid)\n";
			select += "			WHERE {\n";
			select += "				?word a nif:Word .\n";
			select += "				?pre nif:nextWord* ?word .\n";
			select += "             ?word conll:HEAD+ ?s. ?s a nif:Sentence. ?preS nif:nextSentence* ?s.\n";
			select += "			}\n";
			select += "			group by ?word\n";
			select += "		}\n";
			for (String col:cols) {
				if(col.equals(lastCol)) {	// cast to string
					if (col.equals("HEAD")) { //TODO: streamline! only difference to statement below is binding to HEADa instead of HEADs
						select += "		OPTIONAL {\n";
						select += "			?word conll:HEAD ?headurl .\n";
						select += "			bind(replace(str(?headurl), '^.*s[0-9]+_([0-9]+)$', '$1') as ?HEADa) .\n";
						select += "		} .\n";
					} else {
						select += "		OPTIONAL{?word conll:"+col+" ?"+col+"_raw .";
						select += "		 		 BIND(str(?"+col+"_raw) as ?"+col+"a)} .\n";
					}
					select += "     BIND(concat(if(bound(?"+col+"a),?"+col+"a,'_'),\n";
					select += "                 IF(EXISTS { ?word nif:nextWord [] }, '', '\\n')) as ?"+col+"s)\n";
					// we append a linebreak to the value of the last column to generate sentence breaks within a local graph
				} else if (col.equals("HEAD")) {
					select += "		OPTIONAL {\n";
					select += "			?word conll:HEAD ?headurl .\n";
					select += "			bind(replace(str(?headurl), '^.*s[0-9]+_([0-9]+)$', '$1') as ?HEADs) .\n";
					select += "		} .\n";
				} else {
					select += "		OPTIONAL{?word conll:"+col+" ?"+col+"_raw .";
					select += "		 		 BIND(str(?"+col+"_raw) as ?"+col+"s)} .\n";	// cast to string
				}
			}
			select += "	}\n";
			select += "	group by ?word ?sid ?wid\n";
			select += "	order by ?sid ?wid\n";
			select += "}\n";
			
			return select;
		}
		
		/**
		 * FOR LEO: please move whereever you like
		 * @param m
		 *     CoNLL-RDF sentence as Model
		 * @return
		 *     String[0]: all comments + \n
		 *     String[1]: model as Turtle (unsorted)
		 *     concatenate: Full CoNLL-RDF output
		 */
		public static String[] conllRdfModel2String(Model m) {
			String[] out = new String[2];
			
			//generate comments in out[0]
			out[0] = new String();
			String selectComments = "PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"
					+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
					+ "SELECT ?c WHERE {?x a nif:Sentence . ?x rdfs:comment ?c}";
			QueryExecution qexec = QueryExecutionFactory.create(selectComments, m);
			ResultSet results = qexec.execSelect();
			while (results.hasNext()) {
				//please check the regex. Should put a # in front of every line, which does not already start with #.
				out[0] += results.next().getLiteral("c").toString().replaceAll("^([^#])", "#\1")+"\n";
			}
			
			//generate CoNLL-RDF Turtle (unsorted) in out[1]
			StringWriter modelOut = new StringWriter();
			m.write(modelOut, "TTL");
			out[1] = modelOut.toString();
			return out;
		}
		
		/** run either SELECT statement (cf. https://jena.apache.org/documentation/query/app_api.html) and return CoNLL-like TSV or just TTL <br>
		*  Note: this CoNLL-like export has limitations, of course: it will export one property per column, hence, collapsed dependencies or 
		*  SRL annotations cannot be reconverted */		
		public static void printSparql(String buffer, String select, Writer out) throws IOException {
			Model m = ModelFactory.createDefaultModel().read(new StringReader(buffer),null, "TTL");
			String selectComments = "PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"
					+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
					+ "SELECT ?c WHERE {?x a nif:Sentence . ?x rdfs:comment ?c}";
			QueryExecution qexec = QueryExecutionFactory.create(selectComments, m);
			ResultSet results = qexec.execSelect();
			Set<String> comments = new HashSet<>();
			boolean hasGlobalComments = false;
			while (results.hasNext()) {
				for (String result : results.next().getLiteral("c").toString().split("\\\\n")) {
					if (result.trim().matches("^\\s?global\\.columns\\s?=.*") )
						hasGlobalComments = true;
					else
						comments.add(result);
				}
			}
			qexec = QueryExecutionFactory.create(select, m);
			results = qexec.execSelect();
			List<String> cols = results.getResultVars();
			BufferedReader in = new BufferedReader(new StringReader(buffer));
			Hashtable<String,String> key2line = new Hashtable<String,String>();
			String line;
			while((line=in.readLine())!=null) {
				if (line.trim().startsWith("#")) {
					for (String splitComment : line.split("\t")) {
						if (splitComment.trim().matches("^#\\s?global\\.columns\\s?=.*"))
							hasGlobalComments = true;
						else
							comments.add(splitComment.replace("#",""));
					}
				}

			}
			if (hasGlobalComments)
				out.write("# global.columns = " + String.join(" ", cols) + "\n");
			else {
				out.write("# global.columns = "+String.join(" ", cols)+"\n");
			}
			for (String comment : comments) {
				out.write("#"+comment+"\n");
			}

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
		}
		

	/**
	 * Searches a string buffer that is expected to represent a sentence for any
	 * <code>rdfs:comment</code> properties and checks them for a CoNLL-U Plus like global.columns comments.
	 * Defaults to an empty columnNames Array if not present.
	 * @param buffer a string buffer representing a sentence in conll-rdf
	 * @return ArrayList of column names, empty if not present.
	 */
	private List<String> findColumnNamesInRDFBuffer(String buffer) {
			List<String> columnNames = new ArrayList<>();
			Model m = ModelFactory.createDefaultModel().read(new StringReader(buffer),null, "TTL");
			String selectComments = "PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"
					+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
					+ "SELECT ?c WHERE {?x a nif:Sentence . ?x rdfs:comment ?c}";
			QueryExecution qexec = QueryExecutionFactory.create(selectComments, m);
			ResultSet results = qexec.execSelect();
			while (results.hasNext()) {
				String[] comments = results.next().getLiteral("c").toString().split("\\\\n");
				for (String comment : comments) {
					if (comment.matches("^\\s?global\\.columns\\s?=.*")) {
						columnNames.addAll(Arrays.asList(comment.trim()
								.replaceFirst("\\s?global\\.columns\\s?=", "")
								.trim().split(" |\t")));
						LOG.info("Found global columns comment in rdfs:comment");
						return columnNames;
					}
				}
			}
			return columnNames;
		}

	@Override
	protected void processSentenceStream() throws IOException {
		String line;
		String lastLine ="";
		String buffer="";
		BufferedReader in = new BufferedReader(new InputStreamReader(getInputStream()));
		while((line = in.readLine())!=null) {
			line=line.replaceAll("[\t ]+"," ").trim();

			if(!buffer.trim().equals(""))
				if((line.startsWith("@") || line.startsWith("#")) && !lastLine.startsWith("@") && !lastLine.startsWith("#")) { //!buffer.matches("@[^\n]*\n?$")) {
					for (Module m:modules) {
						if(m.getMode()==Mode.CONLLRDF) m.getOutputStream().println(reorderTTLBuffer(buffer, m.getCols()));
						if(m.getMode()==Mode.DEBUG) System.err.println(colorTTL(reorderTTLBuffer(buffer, m.getCols())));
						if(m.getMode()==Mode.CONLL) {
							if (m.getCols().size() < 1) {// no column args supplied
								LOG.info("No column names in cmd args, searching rdf comments..");
								List<String> conllColumns = findColumnNamesInRDFBuffer(buffer);
								if (conllColumns.size()>0) {
									LOG.info("Using #global.comments from rdf");
									m.setCols(conllColumns);
								} else {
									LOG.info("Trying conll columns now..");
									conllColumns = CoNLLStreamExtractor.findFieldsFromComments(new BufferedReader(new StringReader(buffer.trim())), 1);
									if (conllColumns.size()>0) {
										m.setCols(conllColumns);
									}
								}
							}
							if (m.getCols().size() < 1) {
								LOG.info("Supply column names some way! (-conll arg, global.columns or rdf comments");
							}
							else
								printSparql(buffer, columnsAsSelect(m.getCols()), new OutputStreamWriter(m.getOutputStream()));
						}
						if(m.getMode()==Mode.QUERY) printSparql(buffer, m.getSelect(), new OutputStreamWriter(m.getOutputStream()));
						if(m.getMode()==Mode.GRAMMAR) m.getOutputStream().println(extractCoNLLGraph(buffer,true));
						if(m.getMode()==Mode.SEMANTICS) m.getOutputStream().println(extractTermGraph(buffer,true));
						if(m.getMode()==Mode.GRAMMAR_SEMANTICS) {
							m.getOutputStream().println(extractCoNLLGraph(buffer,true));
							m.getOutputStream().println(extractTermGraph(buffer,false));
						}
					}
					buffer="";
				}
				//System.err.println(ANSI_RED+"> "+line+ANSI_RESET);
				if(line.trim().startsWith("@") && !lastLine.trim().endsWith(".")) 
					//System.out.print("\n");
					buffer=buffer+"\n";

				if(line.trim().startsWith("#") && (!lastLine.trim().startsWith("#"))) 
					// System.out.print("\n");
					buffer=buffer+"\n";
				
				//System.out.print("  "+color(line));
				//System.out.print(color(line));
				buffer=buffer+line+"\t";//+"\n";

				if(line.trim().endsWith(".") || line.trim().matches("^(.*>)?[^<]*#")) 
					//System.out.print("\n");
					buffer=buffer+"\n";

				//System.out.println();				
				lastLine=line;
			}
			
		for (Module m:modules) {
			if(m.getMode()==Mode.CONLLRDF) m.getOutputStream().println(reorderTTLBuffer(buffer, m.getCols()));
			if(m.getMode()==Mode.DEBUG) System.err.println(colorTTL(reorderTTLBuffer(buffer, m.getCols())));
			if(m.getMode()==Mode.CONLL) {
				if (m.getCols().size() < 1) {
					LOG.info("No column names in cmd args, searching rdf comments..");
					List<String> conllColumns = findColumnNamesInRDFBuffer(buffer);
					if (conllColumns.size()>0) {
						LOG.info("Using #global.comments from rdf");
						m.setCols(conllColumns);
					} else {
						LOG.info("Trying conll columns now..");
						conllColumns = CoNLLStreamExtractor.findFieldsFromComments(new BufferedReader(new StringReader(buffer.trim())), 1);
						if (conllColumns.size()>0) {
							m.setCols(conllColumns);
						}
					}
				}
				if (m.getCols().size() < 1)
					throw new IOException("-conll argument needs at least one COL to export!");
				else
					printSparql(buffer, columnsAsSelect(m.getCols()), new OutputStreamWriter(m.getOutputStream()));
			}
			if(m.getMode()==Mode.QUERY) printSparql(buffer, m.getSelect(), new OutputStreamWriter(m.getOutputStream()));
			if(m.getMode()==Mode.GRAMMAR) m.getOutputStream().println(extractCoNLLGraph(buffer,true));
			if(m.getMode()==Mode.SEMANTICS) m.getOutputStream().println(extractTermGraph(buffer,true));
			if(m.getMode()==Mode.GRAMMAR_SEMANTICS) {
				m.getOutputStream().println(extractCoNLLGraph(buffer,true));
				m.getOutputStream().println(extractTermGraph(buffer,false));
			}
		}
	}

	public static void main(String[] args) throws IOException {
		final CoNLLRDFFormatter formatter;
		try {
			formatter = new CoNLLRDFFormatterFactory().buildFromCLI(args);
			formatter.setInputStream(System.in);
			formatter.setOutputStream(System.out);
		} catch (ParseException e) {
			LOG.error(e);
			System.exit(1);
			return;
		}
		formatter.processSentenceStream();
	}
}
