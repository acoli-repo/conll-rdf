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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.util.Pair;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetAccessor;
import org.apache.jena.query.DatasetAccessorFactory;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.listeners.ChangedListener;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.log4j.Logger;

/**
 *  @author Christian Chiarcos {@literal chiarcos@informatik.uni-frankfurt.de}
 *  @author Christian Faeth {@literal faeth@em.uni-frankfurt.de}
 */
public class CoNLLRDFUpdater {
	
	static final int MAXITERATE = 999; // maximum update iterations allowed until the update loop is cancelled and an error message is thrown - to prevent faulty update scripts running in an endless loop
	
	public static final Dataset memDataset = DatasetFactory.create();
	public static final DatasetAccessor memAccessor = DatasetAccessorFactory.create(memDataset);
	
	@SuppressWarnings("serial")
	private static final List<Integer> CHECKINTERVAL = new ArrayList<Integer>() {{add(3); add(10); add(25); add(50); add(100); add(200); add(500);}};

	private static final Logger LOG = Logger.getLogger(CoNLLRDFUpdater.class.getName());
	
	private static File GRAPHOUTPUTDIR = null;
	
	// get NAME.nam from file:///NAME.nam/
	private static final Pattern URINAMEPATTERN = Pattern.compile("^.*/(.*)[/#]?$");
	
	private static final Pattern FILENAMEENDPATTERN = Pattern.compile("^.*_([^_]*)$");

	private static class Triple<F, S, M> {
		public final F first;
		public final S second;
		public final M third;
		public Triple (F first, S second, M third) {
			this.first = first;
			this.second = second;
			this.third = third;
		}
	}
	
	public static void loadGraph(URI url, URI graph) {
		LOG.info("loading...");
		LOG.info(url +" into "+ graph);
		if (graph == null) {
			graph = url;
		}
		Model m = ModelFactory.createDefaultModel();
		m.read(url.toString());
		memAccessor.add(graph.toString(), m);
		LOG.info("done...");
	}

	public static void loadBuffer(String buffer) {
		Model m = ModelFactory.createDefaultModel().read(new StringReader(buffer),null, "TTL");
		memAccessor.add(m);
		memAccessor.getModel().setNsPrefixes(m.getNsPrefixMap());
	}

	public static void unloadBuffer(String buffer, Writer out) throws IOException {
		BufferedReader in = new BufferedReader(new StringReader(buffer));
		String line;
		while((line=in.readLine())!=null) {
			line=line.trim();
			if(line.startsWith("#")) out.write(line+"\n");
		}
		memAccessor.getModel().write(out, "TTL");
		out.write("\n");
		out.flush();
		memAccessor.deleteDefault();
	}
	
	public static void produceDot(Model m, String update) throws IOException {
		if (GRAPHOUTPUTDIR != null) {
			String baseURI = "file:///sample.ttl/";
			String u = m.getNsPrefixURI("");
			if (u != null) baseURI = u;
			String updateName = (new File(update)).getName();
			updateName = (updateName != null && !updateName.isEmpty()) ? updateName : UUID.randomUUID().toString();
			Matcher ma = URINAMEPATTERN.matcher(baseURI);
			String baseURIName;
			if (ma.matches()) baseURIName = ma.group(1); else baseURIName = UUID.randomUUID().toString();
			File outputFile = getUniqueFileInt(new File(GRAPHOUTPUTDIR, baseURIName+"_"+updateName+"_"+UUID.randomUUID().toString()));
			Writer w = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
			CoNLLRDFViz.produceDot(m, w);
		}		
	}
	
	public static File getUniqueFileInt(File f) {
		File result = f;
		if (result.exists()) {
			Matcher m = FILENAMEENDPATTERN.matcher(result.getName());
			int i;
			try {
				i = Integer.parseInt(m.group(1));
			} catch (NumberFormatException e) {
				i = 0;
			}
			if (i == 0) {
				result = getUniqueFileInt(new File(f.getPath() + "_" + Integer.toString(i)));
			} else {
				String oldI = Integer.toString(i);
				result = getUniqueFileInt(new File(f.getPath().substring(0, f.getPath().length() - oldI.length()) + Integer.toString(i + 1)));
			}
		}
		return result;
	}

	public static List<Pair<Integer, Long> > executeUpdate(List<Triple<String, String, String>> updates) throws IOException {
		List<Pair<Integer,Long> > result = new ArrayList<Pair<Integer,Long> >();
		for(Triple<String, String, String> update : updates) {
			Long startTime = System.currentTimeMillis();
			Model defaultModel = memAccessor.getModel();
			ChangedListener cL = new ChangedListener();
			defaultModel.register(cL);
			String oldModel = "";
			int frq = MAXITERATE, v = 0;
			boolean change = true;
			try {
				frq = Integer.parseInt(update.third);
			} catch (NumberFormatException e) {
				if (!"*".equals(update.third))
					throw e;
			}
			while(v < frq && change) {
				UpdateAction.execute(UpdateFactory.create(update.second), memDataset);
				produceDot(defaultModel, update.first);
				if (oldModel.isEmpty())
					change = cL.hasChanged();
				else {
					change = !defaultModel.toString().equals(oldModel);
					oldModel = "";
				}
				if (CHECKINTERVAL.contains(v))
					oldModel = defaultModel.toString();
				v++;
			}
			if (v == MAXITERATE)
				LOG.warn("Warning: MAXITERATE reached for " + update.first + ".");
			result.add(new Pair<Integer, Long>(v, System.currentTimeMillis() - startTime));
			defaultModel.unregister(cL);
		}
		return result;
	}

	public static void main(String[] argv) throws IOException, URISyntaxException {
		LOG.info("synopsis: CoNLLRDFUpdater [-custom [-model URI [GRAPH]]* -updates [UPDATE]]+\n"
				+ "\t\t-custom  use custom update scripts\n"
				+ "\t\t-model to load additional Models into local graph\n"
				+ "\t\t-graphsout output directory for the .dot graph files\n"
				+ "\t\t-updates followed by SPARQL scripts paired with {iterations/u}\n"
				+ "\t\tread TTL from stdin => update CoNLL-RDF");
		String args = Arrays.asList(argv).toString().replaceAll("[\\[\\], ]+"," ").trim().toLowerCase();
		boolean CUSTOM = args.contains("-custom ");
		if(!CUSTOM ) { // no default possible here
			LOG.error("Please specify update script.");
		}

		List<Triple<String, String, String>> updates = new ArrayList<Triple<String, String, String>>();
		List<String> models = new ArrayList<String>();

		if(CUSTOM) {
			int i = 0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+custom$")) i++;
			i++;
			while(i<argv.length) {
				while(i<argv.length && !argv[i].toLowerCase().matches("^-+model$")) i++;
				i++;
				while(i<argv.length && !argv[i].toLowerCase().matches("^-+.*$"))
					models.add(argv[i++]);
				if (models.size()==1) {
					loadGraph(new URI(models.get(0)), new URI(models.get(0)));
				} else if (models.size()==2){
					loadGraph(new URI(models.get(0)), new URI(models.get(1)));
				} else if (models.size()>2){
					throw new IOException("Error while loading model: Please use -custom [-model URI [GRAPH]]* -updates [UPDATE]+");
				}
				models.removeAll(models);
			}

			i=0;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+custom$")) i++;
			i++;
			if (args.contains("-graphsout ")) {
				i = 0;
				while(i<argv.length && !argv[i].toLowerCase().matches("^-+graphsout$")) i++;
				i++;
				GRAPHOUTPUTDIR = new File(argv[i].toLowerCase());
				if (GRAPHOUTPUTDIR.exists() || GRAPHOUTPUTDIR.mkdirs()) {
					if (! GRAPHOUTPUTDIR.isDirectory()) throw new IOException("Error: Given -graphsout DIRECTORY is not a valid directory.");
				} else throw new IOException("Error: Failed to create given -graphsout DIRECTORY.");
			}
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+updates$")) i++;
			i++;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+.*$")) {
				String freq;
				freq = argv[i].replaceFirst(".*\\{([0-9u*]+)\\}$", "$1");
				if (argv[i].equals(freq)) // update script without iterations in curly brackets defaults to 1
					freq = "1";
				else if (freq.equals("u"))
					freq = "*";
				String update =argv[i++].replaceFirst("\\{[0-9u*]+\\}$", "");
				updates.add(new Triple<String, String, String>(update, update, freq));
			}

			StringBuilder sb = new StringBuilder();
			for(i = 0; i<updates.size(); i++) {
				Reader sparqlreader = new StringReader(updates.get(i).second);
				File f = new File(updates.get(i).second);
				URL u = null;
				try {
					u = new URL(updates.get(i).second);
				} catch (MalformedURLException e) {}
				if(f.exists()) {			// can be read from a file
					sparqlreader = new FileReader(f);
					sb.append("f");
				} else if(u!=null) {
					try {
						sparqlreader = new InputStreamReader(u.openStream());
						sb.append("u");
					} catch (Exception e) {}
				}
				updates.set(i,new Triple<String, String, String>(updates.get(i).first, "", updates.get(i).third));
				BufferedReader in = new BufferedReader(sparqlreader);
				String updateBuff = "";
				for(String line = in.readLine(); line!=null; line=in.readLine())
					updateBuff = updateBuff + line + "\n";
				updates.set(i,new Triple<String, String, String>(updates.get(i).first, updateBuff,updates.get(i).third));
				sb.append(".");
			}
			LOG.debug(sb.toString());

			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			String line;
			String lastLine ="";
			String buffer="";
			List<Pair<Integer,Long> > dRTs = new ArrayList<Pair<Integer,Long> >(); // iterations and execution time of each update in seconds
			while((line = in.readLine())!=null) {
				line=line.replaceAll("[\t ]+"," ").trim();

				if(!buffer.trim().equals(""))
					if((line.startsWith("@") || line.startsWith("#")) && !lastLine.startsWith("@") && !lastLine.startsWith("#")) { //!buffer.matches("@[^\n]*\n?$")) {
						loadBuffer(buffer);
						if(CUSTOM) {
							List<Pair<Integer,Long> > ret = executeUpdate(updates);
							if (dRTs.isEmpty())
								dRTs = ret;
							else
								for (int x = 0; x < ret.size(); ++x)
									dRTs.set(x, new Pair<Integer, Long>(dRTs.get(x).getKey() + ret.get(x).getKey(), dRTs.get(x).getValue() + ret.get(x).getValue()));
						}
						unloadBuffer(buffer, new OutputStreamWriter(System.out));
						buffer="";
					}
				if(line.trim().startsWith("@") && !lastLine.trim().endsWith(".")) 
					buffer=buffer+"\n";

				if(line.trim().startsWith("#") && (!lastLine.trim().startsWith("#"))) 
					buffer=buffer+"\n";

				buffer=buffer+line+"\t";//+"\n";

				if(line.trim().endsWith(".") || line.trim().matches("^(.*>)?[^<]*#")) 
					buffer=buffer+"\n";

				lastLine=line;
			}
			loadBuffer(buffer);
			if(CUSTOM) {
				List<Pair<Integer,Long> > ret = executeUpdate(updates);
				if (dRTs.isEmpty())
					dRTs = ret;
				else
					for (int x = 0; x < ret.size(); ++x)
						dRTs.set(x, new Pair<Integer, Long>(dRTs.get(x).getKey() + ret.get(x).getKey(), dRTs.get(x).getValue() + ret.get(x).getValue()));
			}
			unloadBuffer(buffer, new OutputStreamWriter(System.out));
			if (!dRTs.isEmpty())
				LOG.debug("Done - List of interations and execution times for the updates done (in given order):\n\t\t" + dRTs.toString());
		}
	}
}
