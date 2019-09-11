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
import org.apache.log4j.Logger;



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
public class CoNLL2RDF {
	
	private static Logger LOG = Logger.getLogger(CoNLL2RDF.class.getName());
	
	/** can be null */
	private final BufferedWriter out;
	private final String baseURI;
	private final List<String> col2field;
	private final Hashtable<String,Integer> field2col;
	
	/** counter variable for generating continuous sentence IDs */
	protected int sent=1;
	
	/** counter variable for generating token IDs, re-set for every sentence */
	protected int tok=0;

	/** variable for character offsets to generate NIF ids for words and sentences, currently not in use<br> 
	 *  note that these offsets are tokenization-dependent because we assume one white space between every pair of tokens 
	 */
	protected int pos=0; 
	
	private final static Pattern empty = Pattern.compile("^(O|-|--|_|__)?$");	
	
	/**
	 * @param baseURI namespace for word and sentence IDs,<br>
	 * @param fields names for conll columns
	 * <ul>
	 * <li>Make sure to pick valid XML local names for column labels.</li>
	 * <li>We recommend to use unique column labels, additional columns carrying similar information can be numbered.</li>
	 * <li>Column names maintain the original capitalization in the RDF output, but 
	 * 		<code>ID</code>, <code>WORD</code>, <code>HEAD</code>, <code>EDGE</code>, <code>HEAD2</code>, <code>...-ARGs</code>, etc. 
	 * 		are internally processed case-insensitive.
	 * <li>We generate URIs following the TIGER scheme. Subclasses may provide offset-based NIF URIs using the <code>pos</code> variable.
	 *     However, this is not recommended: Character offsets are calculated within the CoNLL file and are thus not compatible with 
	 *     untokenized text.
	 * <li>If an <code>ID</code> column is found, it is concatenated with the sentence number to form the word URI. 
	 * 		Otherwise, the token position within the sentence (starting from 1) is used to calculate <code>ID</code>.
	 *      The sentence (root) itself takes a URI with token number 0.
	 * <li>We provide (informational) character offsets in the output. Their validity presupposes a unique column <code>WORD</code></li>
	 * <li>Use column labels <code>HEAD</code> (and its enumerations) to designate properties that define references to other words 
	 * 		(foreign keys) within the same sentence, e.g., dependency syntax. Other columns
	 * 		will be treated as attribute-value pairs with primitive string values. Exceptions for <i>X</i><code>-ARGs</code> columns apply.
	 * <li>Special treatment of SRL arguments (column <i>X</i><code>-ARGs</code>): These can represent any number of columns, 
	 * 	   must not be followed by other columns.
	 *     Annotations in <i>i</i>th <i>X</i><code>-ARGs</code> column should define argument( span)s for the <i>i</i>th predicate in column <i>X</i>.
	 *     As predicate, we identify word(s) with non-empty annotation in column <i>X</i>.
	 *     For a SRL label <i>role</i>, we create the triple <i>predicate</i> <code>conll:</code><i>role</i> <i>word</i><code>.</code>
	 *     With conventional PropBank/NomBank labels or their IOBES-representation, only valid properties will be created, but it is in the hands of
	 *     the user to make sure these properties aren't conflated with properties generated from column labels.
	 * </ul>
	 * Note that we do not validate, validity of input and arguments is in the hands of the user.
	 * @param out output will be immediately written to out (using a BufferedWriter)
	 */
	public CoNLL2RDF(String baseURI, String[] fields, Writer out) throws IOException {
		this.baseURI=baseURI;
		col2field = Arrays.asList(fields);
		if(out!=null)
			this.out=new BufferedWriter(out);
		else this.out=null;
		
		/*		
		// set file encoding to UTF-8
		System.setProperty("file.encoding","UTF8");
		Field charset = Charset.class.getDeclaredField("defaultCharset");
		charset.setAccessible(true);
		charset.set(null,null);
		 */
		
		// to make sure ID, HEAD and EDGE are upper-case 
		for(int i = 0; i<col2field.size(); i++)
			if(col2field.get(i).toLowerCase().matches("^(id|head[0-9]*|edge[0-9]*)$"))
				col2field.set(i,col2field.get(i).toUpperCase());
		
		field2col = new Hashtable<String,Integer>();
		for(int i = 0; i<col2field.size(); i++)
			field2col.put(col2field.get(i), i);
			
		if(out!=null)
			writePrefixes(out);
	}

	/** for conll2model, we need to write prefixes multiple times */
	protected void writePrefixes(Writer out) throws IOException {
		out.write("\n"+
				"PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#>\n"+ 
				"PREFIX conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#>\n"+
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"+
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
				"PREFIX terms: <http://purl.org/acoli/open-ie/>\n"+
				"PREFIX : <"+baseURI+">\n\n");
		out.flush();
	}
	
	/** same as CoNLL2RDF, but instantiated with a null Writer */
	public CoNLL2RDF(String baseURI, String[] fields) throws IOException {
		this(baseURI, fields, null);
	}
	
	/** synopsis message (arguments only) */
	protected static String synopsis = 
			"CoNLL2RDF: configurable converter for CoNLL-like formats to RDF/Turtle\n"+
					"           reads from stdin and writes to stdout\n"+
					"synopsis: CoNLL2RDF -help\n"+
					"          CoNLL2RDF baseURI Label1 [.. LabelN]\n"
					+ "Note: for practical applications use CoNLLStreamExtractor";	
	/** help message (arguments + explanation) */
	protected static String help =
		synopsis+"\n"+
		"   -help	extended help\n"+
		"	baseURI	base URI to be used in the output data\n"+
		"	LabelI	column label, identifying *exactly* the columns in the data\n"+
//		"       if plain strings are used, we create properties in the connl name-\n"+			// TODO
//		"			space, if URIs are used, these are used directly (e.g. rdfs:label\n"+
//		"			for tokens), if prefixed URIs are used, we use http://prefix.cc\n"+
//		"			to resolve these\n"+
		"		special case $X$-ARGs: for a series of columns referring back\n"+
		"			to (any) column $X$, e.g., for SRL annotation. In these cases,\n"+
		"			the number of $X$-ARG columns per sentence depends on the number\n"+
		"			of non-empty cells in column $X$; replace $X$ by the corresp.\n"+
		"			name\n"+
		"           $X$-ARGs columns must not be followed by another column\n"+
		"		special case ID: identifies tokens in a sentence as used for\n"+
		"			resolving dependency relations, if not provided, we assume\n"+
		"			the first word being 1, the second 2, etc., with the next\n"+
		"			sentence starting in 1, again\n"+
		"		special case HEAD: used for dependency relations to the syntactic head\n"+
		"			values are resolved to explicit or implicit ID rows within the same\n"+
		"			sentence, 0 (Root) is resolved to the sentence\n"+
		"			Note that CoNLL supports secondary HEADs, these are marked by HEAD2,\n"+
		"			we support any number of n-ary heads, and the label HEAD[NUM] is\n"+
		"			reserved for these\n"+
		"		special case EDGE: used for labels of dependency relations, EDGE refers\n"+
		"			to HEAD, EDGE2 to HEAD2, etc.\n"+
		"		Note that labels are case-sensitive and used as provided, but ID, HEAD,\n"+
		"		HEAD[NUM], EDGE, EDGE[NUM] will be transformed to UPPER CASE, for the \n"+
		"		-ARGSs suffix, we perform a case-insensitive match.\n"+
		"		Also note that we *require* <TAB>-separated columns.\n"+
		"		Note that we do not check URI validity and label uniqueness, this is in the\n"+
		"		hands of the user.\n"+
		"Beyond for CoNLL data in a strict sense, we can read any TSV format, including\n"+
		"TreeTagger (chunker) output, and Sketch Engine corpus files. However, XML elements\n"+
		"as provided by the latter are just skipped";
	
	/** @param argv baseURI field1 field2 ... (see variable <code>help</code> and method <code>conll2ttl</code>) */
	// evtl. init classes and properties durch CoNLL-eigenes ersetzen
	public static void main(String[] argv) throws Exception {		
		if(argv.length<2 || argv[0].toLowerCase().matches("^-+h(elp)?$")) {
			System.err.println(help);
			return;
		} else 
			LOG.info(synopsis);

		LOG.info("# created with CoNLL2RDF");
		StringBuilder sb = new StringBuilder();
		for(String a : argv) sb.append(" "+a);
		LOG.info(sb.toString());

		CoNLL2RDF converter = new CoNLL2RDF(argv[0], Arrays.copyOfRange(argv, 1, argv.length), new OutputStreamWriter(System.out));
		converter.conll2ttl(new InputStreamReader(System.in));
	}

	/**
	 * like conll2ttl, but note that we don't write anything to the default Writer out, but rather return a Jena Default Model<br>
	 * of course, we don't get the NLP-friendly output format, then */
	public Model conll2model(Reader in) throws IOException {
		StringWriter stringWriter = new StringWriter();
		this.conll2ttl(in,stringWriter);
		try {
			return ModelFactory.createDefaultModel().read(new StringReader(stringWriter.toString()),baseURI, "TTL");
		} catch (Exception e) {
			e.printStackTrace();
			LOG.info("while processing the following input:\n<code>"+stringWriter.toString()+"</code>");			
			return null;
		}
	}
	
	/**
	 * Process CoNLL data from <code>in</code>, given the baseURI, column labels and writer out specified at the constructor<br>
	 * Note that multiple sources can be processed with the same CoNLL2RDF instance. URIs are generated from sequentially updated
	 * sentence counts. Results are immediately written to the associated Writer.
	 * @param in CoNLL source data
	 */
	public void conll2ttl(Reader in) throws IOException {
		conll2ttl(in,out);
	}

	/**
	 * See conll2ttl(Reader), but note that we write *only* to the specified writer.
	 * If this is not the pre-defined writer out, we define the prefixes.<br>
	 * This handling of writers is done to provide the core functionality for conll2ttl(Reader) and conll2model(Reader).<br>
	 * NOTE: make sure to finish the input with a newline character
	 */
	protected void conll2ttl(Reader in, Writer out) throws IOException {
		if(out!=this.out && out!=null)
			writePrefixes(out);

		BufferedReader bin = new BufferedReader(in);
		String sentence = "";
		ArrayList<String> predicates = new ArrayList<String>();
		String root = ":s"+sent+"_"+0;
		TreeSet<String> argTriples = new TreeSet<String>();
		Set<String> argsProperties = new TreeSet<String>();
		Set<String> headSubProperties = new TreeSet<String>();
		ArrayList<String> comments = new ArrayList<>();

		for(String line = ""; line!=null; line=bin.readLine()) {
			if(line.contains("#"))
				out.write(line.replaceFirst("^[^#]*","")); // keep commentaries
			line=line.replaceAll("<[\\/]?[psPS]( [^>]*>|>)","").trim(); 		// in this way, we can also read sketch engine data and split at s and p elements
			if(!(line.trim().matches("^<[^>]*>$"))) {							// but we skip all other XML elements, as used by Sketch Engine or TreeTagger chunker
				root = ":s"+sent+"_"+0;
				if(line.trim().equals("") && !sentence.equals("")) {
					for(String arg : argTriples)
						sentence=sentence+".\n"+arg;
					sentence=sentence+"."+
							" # offset="+pos+
							"\n";
					// sentence=sentence+".\n";
					argTriples.clear();
					if(col2field.get(col2field.size()-1).toLowerCase().matches(".*args")) {
						for(int i = 0; i<predicates.size(); i++) {
							sentence=sentence.replaceAll("_TMP_"+col2field.get(col2field.size()-1).replaceFirst("[\\-_]*[Aa][rR][gG][sS]$","_"+i),predicates.get(i));
						}
					}
					if (comments.size()>0) {
						out.write(sentence);
						out.write(root+" rdfs:comment \""+(String.join("\\\\n",comments))+"\" ."+"\n");
					} else
						out.write(sentence+"\n");
					predicates.clear();
					sentence="";
					tok=0;
					sent++;
				} else {
					if(line.contains("#")) {
						out.write(line.replaceFirst("^[^#]*#", "#")+"\n");
						comments.add(line.replaceFirst("^[^#]*#", "#"));
					}
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
							" # offset="+pos+
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
				out.write(sentence+"\n");
			out.write("\n");
			for(String p : headSubProperties) 
				out.write(p.trim()+"\n");
			out.write("\n");
			for(String p : argsProperties)
				out.write(p.trim()+"\n");
			out.flush();			
		}
			if(tok>0) {
				sent++;
				tok=0;
			}
	}
	/**
	 * Searches a BufferedReader for a global.columns = field to extract the column names from (CoNLL-U Plus feature).
	 * We allow for arbitrary lines to search, however as of September 2019, CoNLL-U Plus only allows first line.
	 * Does NOT validate if custom columns are in a separate name space, as required by the format.
	 * @see <a href="https://universaldependencies.org/ext-format.html">CoNLL-U Plus Format</a>
	 * @param inputStream an untouched BufferedReader
	 * @param fields The List the found field names will be written to
	 * @param maxLinesToSearch how many lines to search. CoNLL-U Plus requires this to be 1.
	 */
	static List<String> findFieldsFromComments(BufferedReader inputStream, int maxLinesToSearch) {
		List<String> fields = new ArrayList<>();
		int readAheadLimit = 10000;
		float meanByteSizeOfLine = 0.0f;
		int m = 0;
		int peekedChars = 0;
		if (!inputStream.markSupported()) {
			LOG.warn("Marking is not supported, could lead to cutting off the beginning of the stream.");
		}
		try {
			LOG.info("Testing for CoNLL-U Plus");
			inputStream.mark(readAheadLimit);

			String peekedLine;
			while ((peekedLine = inputStream.readLine()) != null && m < maxLinesToSearch) {
				m++;
				meanByteSizeOfLine = ((meanByteSizeOfLine * (m - 1)) + peekedLine.length()) / m;
				LOG.debug("Mean Byte size: " + meanByteSizeOfLine);
				peekedChars += peekedLine.length();
				if ((peekedChars + meanByteSizeOfLine) > readAheadLimit) {
					LOG.info("Couldn't find CoNLL-U Plus columns.");
					inputStream.reset();
					return fields;
				}
				LOG.debug("Testing line: " + peekedLine);
				if (peekedLine.matches("^#\\s?global\\.columns\\s?=.*")) {
					fields.addAll(Arrays.asList(peekedLine.trim()
							.replaceFirst("#\\s?global\\.columns\\s?=", "")
							.trim().split(" |\t")));
					inputStream.reset();
					LOG.info("Success");
					return fields;
				}

			}
			inputStream.reset();
		} catch (IOException e) {
			LOG.error(e);
			LOG.warn("Couldn't figure out CoNLL-U Plus, searched for " + m + " lines.");
		}
		return fields;
	}
}