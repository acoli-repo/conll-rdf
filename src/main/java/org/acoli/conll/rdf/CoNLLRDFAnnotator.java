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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;


/** a simple, shell-based annotator for CoNLL-RDF, requires Un*x shells with color highlighting<br/>
 *  reads CoNLL-RDF sentence-wise from stdin, writes it formatted to stdout<br/>
 *  allows to create, set and overwrite properties in the conll: namespace<br/>
 *  also allows to define macros<br/>
 *  a pre-provided macro facilitates annotating dependency syntax (i.e. HEAD+EDGE combinations)
 *  @author Christian Chiarcos {@literal chiarcos@informatik.uni-frankfurt.de}
 */
public class CoNLLRDFAnnotator extends CoNLLRDFFormatter {
	private static Logger LOG = LogManager.getLogger(CoNLLRDFAnnotator.class);
	
	public static void main(String[] args) throws IOException {
		BufferedReader in;
		BufferedReader commands = new BufferedReader(new InputStreamReader(System.in));
		try {
			final CommandLine cmd = new CoNLLRDFCommandLine("CoNLLRDFAnnotator file.ttl",
					"Manual, sentence-wise annotation of a canonically formatted CoNLL TTL File. Visualization like CoNLLRDFFormatter -grammar. Writes CoNLL-RDF to stdout (make sure to write this to a file, your annotation is only contained here)",
					new Option[] {}, LOG).parseArgs(args);

			List<String> argList = cmd.getArgList();
			if (argList.isEmpty()) {
				throw new ParseException("Missing required argument file.ttl.");
			}
			in = new BufferedReader(new FileReader(argList.remove(0)));
		} catch (ParseException e) {
			LOG.error(e);
			System.exit(1);
			return;
		}

			String line;
			String lastLine ="";
			String buffer="";

		String command = "";
			String macros = "^([0-9]+) ([0-9]+) (.+)$\t$1/HEAD=$2/EDGE=$3\n";
			while((line = in.readLine())!=null) {
				line=line.replaceAll("[\t ]+"," ").trim();

				if(!buffer.trim().equals(""))
					if((line.startsWith("@") || line.startsWith("#")) && !lastLine.startsWith("@") && !lastLine.startsWith("#")) {
					while(!command.trim().equals(">")) {
							System.err.print(
							  "actions ............................................................................................................\n"+
							  "        : "+ANSI_BLUE+"$nr/$att=$val"+ANSI_RESET+"   for element number $nr, set CoNLL property $att to $val, e.g., \"1/POS=NOUN\"              :\n"+
								//"        :                 $nr element number (starting with 1), e.g., 1 for the first                              :\n"+
								//"        :                 $att local name of a CoNLL property, e.g., POS                                           :\n"+
								//"        :                 $val string value of the CoNLL property, e.g., NOUN                                      :\n"+
								"        :                 for HEAD, enter the number of the head node, will be expanded to URI                     :\n"+
								"        : "+ANSI_BLUE+"$nr/$p1[/$p2..]"+ANSI_RESET+" multiple $att=$val patterns $p1, $p2, ... for $nr can be provided as ,-separated list    :\n"+
								"        :                 e.g., \"1/HEAD=0/EDGE=root\"; NOTE: $val must not contain /                                :\n"+
								"        : "+ANSI_BLUE+">"+ANSI_RESET+"               write and go to next sentence                                                            :\n"+
								"        : "+ANSI_BLUE+"m"+ANSI_RESET+"               define or undefine a macro (a regex for preprocessing your input)                        :\n"+
								"        : "+ANSI_BLUE+"<CTRL>+C"+ANSI_RESET+"        quit                                                                                     :\n"+
								"        :..........................................................................................................:\n");
						  if(macros.trim().length()>0)
								System.err.println("macros    "+ANSI_RED+macros.replaceAll("\n",ANSI_RESET+"\n          "+ANSI_RED).replaceAll("\t","\t"+ANSI_RESET+"=>\t"+ANSI_BLUE)+ANSI_RESET);
							System.err.print("| ----------------------------\n| "+CoNLLRDFFormatter.extractCoNLLGraph(buffer,true).replaceAll("\n","\n| ")+"-----------------------------\n"+
								"command: ");
						command=commands.readLine().trim();
						command=applyMacros(macros,command);
						if(command.equals(">")) {
								System.out.println(buffer+"\n");
								buffer="";
						} else if(command.equals("m")) {
								System.err.print("left hand side (what is entered):               ");
							String lhs = commands.readLine().replaceAll("\t"," ");
								System.err.print("right hand side (or <ENTER> to delete macro):   ");
							String rhs=commands.readLine().replaceAll("\t"," ");
								if(rhs.equals("")) {
									String tmp = "";
									for(String macro : macros.split("\n"))
										if(!macro.replaceFirst("\t.*","").equals(lhs))
											tmp=tmp+macro+"\n";
									macros=tmp;
								} else {
									macros=macros+lhs+"\t"+rhs+"\n";
								}
							} else {
							buffer=updateBuffer(buffer,command);
							}
						}
					command = "";
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
		commands.close();
			in.close();
			LOG.info("done");
	}

  protected static String applyMacros(String macros, String cmd) {
		String orig = cmd;
		for(String macro : macros.split("\n")) {
			String lhs = macro.replaceFirst("\t.*","");
			String rhs = macro.replaceFirst("^[^\t]*\t","");
			cmd=cmd.replaceAll(lhs,rhs);
		}
		if(!cmd.equals(orig))
			System.err.println("macro expansion: "+ANSI_RED+orig+ANSI_RESET+"\t=>\t"+ANSI_BLUE+cmd+ANSI_RESET);
		return cmd;
	}

  /** note: this requires canonically formatted CoNLL-RDF */
	public static String updateBuffer(String conllRDFBuffer, String cmd) {
		if(cmd==null || cmd.trim().length()==0)
			return conllRDFBuffer;
		if(!cmd.matches("^[0-9]+/[^=]*=.*")) {
			LOG.warn("CoNLLRDFAnnotator.updateBuffer(String,\""+cmd+"\") with invalid command");
			return conllRDFBuffer;
		}
		String prolog = "";
		Vector<String> words = new Vector<String>();
		String[] buffer = conllRDFBuffer.split("[\r]?\n");
		int i = 0;
		String sentence = ""; // note that we assume this is all from one sentence, i.e., subject of the nif:Sentence triple
		while(i<buffer.length && !buffer[i].contains("nif:Word")) {
			prolog=prolog+buffer[i]+"\n";
			if(buffer[i].contains("nif:Sentence")) sentence = buffer[i].replaceAll("nif:Sentence.*","").trim().replaceFirst("[ \t].*","");
			i++;
		}
	  while(i<buffer.length) {
			if(buffer[i].contains("nif:Sentence")) sentence = buffer[i].replaceAll("nif:Sentence.*","").trim().replaceFirst("[ \t].*","");
			if(!buffer[i].contains("nif:Word")) {
			  words.setElementAt(words.get(words.size()-1)+"\n"+buffer[i++],words.size()-1);
			} else
				words.add(buffer[i++]);
		}
		int address = Integer.parseInt(cmd.replaceFirst("/.*",""))-1;
		String[] ops = cmd.replaceFirst("^[^/]*/","").split("[ \t]*/[ \t]*");
		for(String op : ops) {
			try {
				String prop=op.replaceFirst("=.*","".trim());
				String val=op.replaceFirst("^[^=]*=","").trim();
				if(prop.equals("HEAD")) {
						if(val.equals("0")) val=sentence;
						else
							val=words.get(Integer.parseInt(val)-1).trim().replaceAll("[ \t].*","");	// subject of buffer[val]
				} else {
					val="\""+val+"\"";
			  }
				prop="conll:"+prop;
				if(words.get(address).contains(" "+prop+" ")) {
	 				words.setElementAt(
						words.get(address)
							.replaceAll("[ \t]+"+prop+"[ \t]+[^ \t\"][^\";\\.]+", " "+prop+" "+val)
							.replaceAll("[ \t]+"+prop+"[ \t]+\"[^\"]+\"", " "+prop+" "+val)
						, address);
				} else {
					words.setElementAt(words.get(address).replace("nif:Word","nif:Word; "+prop+" "+val),address);
				}

				conllRDFBuffer=prolog;
				for(String s : words)
					conllRDFBuffer=conllRDFBuffer+s+"\n";

			} catch (Exception e) {
				e.printStackTrace();
				LOG.error("while processing \""+address+"/"+op);
			}
		}


		return conllRDFBuffer;
	}
}
