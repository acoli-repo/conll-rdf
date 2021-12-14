package org.acoli.conll.rdf;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TenTen2XMLTSV {


	List<String> tagsContainingData = Arrays.asList("kwik", "left", "right");
	private static Logger LOG = LogManager.getLogger(TenTen2XMLTSV.class.getName());
	public boolean isKEEP() {
		return KEEP;
	}
	public void setKEEP(boolean KEEP) {
		this.KEEP = KEEP;
	}
	public void setREPAIR(boolean REPAIR) { this.REPAIR = REPAIR;}

	private boolean KEEP = false;
	private boolean REPAIR = false;

	TenTen2XMLTSV() {
	}
	TenTen2XMLTSV(List<String> tags, boolean keep, boolean repair) {
		if (tags.size() > 0)
			this.tagsContainingData = tags;
		this.KEEP = keep;
		this.REPAIR = repair;
		LOG.info("Tags: "+this.tagsContainingData);
		LOG.info("Keep: "+this.KEEP);
		LOG.info("Repair: "+this.REPAIR);
	}
	class Line {
		boolean isOpening = false;
		boolean isClosing = false;
		boolean isSelfClosing = false;
		boolean isCoNLL = false;
		final String data;
		Line (String str) {
			if (str.trim().matches("<[^/]*>")) {
				LOG.debug(str+" is opening bracket");
				this.isOpening = true;
			} else if (str.trim().matches("</(.*)>")) {
				LOG.debug(str+" is closing bracket");
				this.isClosing = true;
			} else if (str.trim().matches("<(.*)/>")){
				LOG.debug(str+" is self-closing bracket");
				this.isSelfClosing = true;
			} else {
				LOG.debug(str+" is CoNLL");
				this.isCoNLL = true;
			}
			if (isClosing || isOpening || isSelfClosing) {
				this.data = str.trim();
			} else {
				this.data = str;
			}
		}

		@Override
		public String toString() {
			return data;
		}

		public String getName() {
			if (isSelfClosing || isOpening || isClosing) {
				return data.trim().replaceAll("[<>/]","");
			} else {
				return null;
			}
		}
		public boolean sameName(Line other) {
			return this.getName().equals(other.getName());
		}
		public Line toOpening() {
			if (!isCoNLL) {
				return new Line(data.trim().replace("/",""));
			} else {
				return null;
			}
		}
		@Override
		public boolean equals(Object other){
			if((other == null) || (getClass() != other.getClass())){
				return false;
			}
			else{
				Line otherLine = (Line) other;
				return data.equals(otherLine.data);
			}
		}
		@Override
		public int hashCode() {
			return getName().hashCode();
		}
	}

	/**
	 * Splits stuff like </s><s> into two separate lines.
	 * @param line
	 * @return
	 */
	List<Line> splitMultipleBracketsInSingleLine(String line) {
		List<Line> result = new ArrayList<>();
		for (String elem : line.split(">")) {
			LOG.debug("Bracket in buffer element: "+elem+">");
			result.add(new Line(elem+">".trim()));
		}
		return result;
	}

	List<Line> splitEmbeddedCoNLL(String conll) {
		List<Line> result = new ArrayList<>();
		StringBuilder buffer = new StringBuilder();
		boolean insideBracket = false;
		for (int i = 0; i < conll.length(); i++) {
			Character charAt = conll.charAt(i);

			if (insideBracket) {
				if (charAt.toString().equals(">")){
					insideBracket = false;
				}
				buffer.append(charAt);
			} else {
				if (charAt.toString().equals("<")){
					insideBracket = true;
				}

				if (charAt.toString().equals(" ") && buffer.toString().trim().length() > 0) {
					if (StringUtils.countMatches(buffer.toString(), "/") >= 8) { // TODO: parameterize
						LOG.debug("Data conll line in buffer: "+buffer.toString());
						result.add(new Line(buffer.toString().trim()));
					} else {
						// embeddings
						if (StringUtils.countMatches(buffer.toString(), ">") > 1) {
							// we have more than one embedding, we have to split them
							result.addAll(splitMultipleBracketsInSingleLine(buffer.toString()));
						} else {
							LOG.debug("Bracket in buffer: "+buffer.toString());
							result.add(new Line(buffer.toString().trim()));
						}
					}
					buffer = new StringBuilder();
				}
				buffer.append(charAt);
			}
		}
		if (StringUtils.countMatches(buffer.toString(), "/") < 8 && StringUtils.countMatches(buffer.toString(), ">") > 1) {
			result.addAll(splitMultipleBracketsInSingleLine(buffer.toString()));
		} else {
			result.add(new Line(buffer.toString()));
		}
		return result;
	}

	String embeddedCoNLL2CoNLL(String embeddedCoNLL) {
		if (embeddedCoNLL.contains("<") && embeddedCoNLL.contains(">")) {
			// There is some bracketing going on..
			if (StringUtils.countMatches(embeddedCoNLL, "/") > 1) {
				// It's one of those weird heading lines
				boolean insideBracket = false;
				StringBuilder conll = new StringBuilder();
				for (int i = 0; i < embeddedCoNLL.length(); i++) {
					Character charAt = embeddedCoNLL.charAt(i);
					if (insideBracket) {
						if (charAt.toString().equals(">")) {
							insideBracket = false;
						}
						conll.append(charAt);
					} else {
						if (charAt.toString().equals("<")) {
							insideBracket = true;
						}
						if (charAt.toString().equals("/")) {
							conll.append("\t");
						} else {
							conll.append(charAt);
						}
					}
				}
				return conll.toString();
			} else {
				// It's a single xml bracket, but we properly split them in single one line elements
				return embeddedCoNLL.trim();
			}
		} else {
			// Finally the simple and raw data row, we replace / and be done with it.
			return  embeddedCoNLL.replace("/", "\t");
		}
	}

	Matcher matchEmbeddedCoNLL(String line) {
		for (String tag : this.tagsContainingData) {
			Pattern p = Pattern.compile("<"+tag+">(.*)</"+tag+">");
			Matcher m = p.matcher(line);
			if (m.matches()) {
				return m;
			}
		}
		return null;
	}

	void tenten2ttl(Reader in, Writer out) throws IOException {

		BufferedReader bin = new BufferedReader(in);
		Pattern p = Pattern.compile("<left>(.*)</left>");
		List<Line> brackets = new ArrayList<>();
		LOG.info("Reading..");
		for (String line = ""; line!=null; line=bin.readLine()) {
			Matcher m = matchEmbeddedCoNLL(line.trim());
			if (m != null && m.matches()) {
				String content = m.group(1);
				LOG.debug("Content is: "+content);
				String withEscaped = replaceEscapes(content);
				LOG.debug("Escaped is: "+withEscaped);
				List<Line> split = splitEmbeddedCoNLL(withEscaped);
				LOG.debug("Splitted into: "+split);
				for (Line spltStr : split) {
					if (!spltStr.isCoNLL) {
						LOG.debug("Before: " + spltStr);
					}
					String result = embeddedCoNLL2CoNLL(spltStr.data);
					if (!spltStr.isCoNLL && this.REPAIR) {
						if (spltStr.isOpening) {
							brackets.add(spltStr);
						}
						if (spltStr.isClosing) {
							if (!brackets.contains(spltStr.toOpening())) {
								out.write("<"+spltStr.getName()+">");
								out.write("\n");
								LOG.debug("Artificially opened for "+spltStr+", brackets: "+brackets);
							} else {
								brackets.remove(spltStr.toOpening());
							}
						}
					}
					out.write(result);
					out.write("\n");

				}

			} else {
				if (isKEEP()) {
					out.write(line+"\n");
				} else {
					LOG.debug("Skipping "+line);
				}
			}
		}
		out.flush();
		LOG.info("Done.");
	}

	private String replaceEscapes(String content) {
		return content.replaceAll("&lt;","<")
				.replaceAll("&gt;",">")
				.replaceAll("&quot;","\"");
	}

	public static void main(String[] argv) throws Exception {

		boolean keep = false;
		boolean repair = false;
		boolean silent = false;
		List<String> dataTags = new ArrayList<>();

		for (int i = 0; i<argv.length; i++) {
			if (argv[i].equals("--keep") || argv[i].equals("-k"))
				keep = true;
			else if (argv[i].equals("--repair") || argv[i].equals("-r"))
				repair = true;
			else if (argv[i].equals("--silent") || argv[i].equals("-s"))
				silent = true;
			else
				dataTags.add(argv[i]);
		}
		if (silent) {
			// Not part of the public API. See: https://logging.apache.org/log4j/2.x/faq.html#reconfig_level_from_code
			Configurator.setLevel(LOG.getName(), Level.OFF);
		}

		LOG.info("synopsis: CoNLLStreamExtractor ELEMENT1[.. ELEMENTn] [--keep|-k] [--repair|-r] [--silent|-s]\n"+
			"\tELEMENTi      Name of XML Node to extract embedded CoNLL from\n"+
			"\t--keep        Will keep XML that surrounds embedded CoNLL\n"+
			"\t--repair      Will insert artificial opening brackets in case a closing one is encountered without opening\n"+
			"\t--silent      Will silence all logging (Also this synopsis!)\n");
		TenTen2XMLTSV tt2xt = new TenTen2XMLTSV(dataTags, keep, repair);

		InputStreamReader in = new InputStreamReader(System.in);
		tt2xt.tenten2ttl(in, new OutputStreamWriter(System.out));
	}
}
