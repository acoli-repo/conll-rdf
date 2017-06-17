import java.io.BufferedReader;
import java.io.File;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetAccessor;
import com.hp.hpl.jena.query.DatasetAccessorFactory;
import com.hp.hpl.jena.query.DatasetFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateFactory;

public class CoNLLRDFUpdater {
	public static final Dataset memDataset = DatasetFactory.createMem();
	public static final DatasetAccessor memAccessor = DatasetAccessorFactory.create(memDataset);

	public static void loadGraph(URI url, URI graph) {
		System.err.println("loading...");
		System.err.println(url +" into "+ graph);
		if (graph == null) {
			graph = url;
		}
		Model m = ModelFactory.createDefaultModel();
		m.read(url.toString());
		memAccessor.add(graph.toString(), m);
		System.err.println("done...");
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

	public static void executeUpdate(List<String> updates) {
		for(String update : updates) {
			//System.err.println("executing...");
			//System.err.println(update);
			UpdateAction.execute(UpdateFactory.create(update), memDataset);
		}
	}

	public static void main(String[] argv) throws IOException, URISyntaxException {
		System.err.println("synopsis: CoNLLRDFFormatter [-custom [-model URI [GRAPH]]* -updates [UPDATE]]+\n"
				+ "\t-custom  use custom update scripts. Use -model to load additional Models into local graph\n"
				+ "read TTL from stdin => update CoNLL-RDF");
		String args = Arrays.asList(argv).toString().replaceAll("[\\[\\], ]+"," ").trim().toLowerCase();
		boolean CUSTOM = args.contains("-custom ");
		if(!CUSTOM ) { // no default possible here
			System.err.println("Please specify update script.");
		}

		List<String> updates = new ArrayList<String>();
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
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+updates$")) i++;
			i++;
			while(i<argv.length && !argv[i].toLowerCase().matches("^-+.*$"))
				updates.add(argv[i++]);

			for(i = 0; i<updates.size(); i++) {
				Reader sparqlreader = new StringReader(updates.get(i));
				File f = new File(updates.get(i));
				URL u = null;
				try {
					u = new URL(updates.get(i));
				} catch (MalformedURLException e) {}

				if(f.exists()) {			// can be read from a file
					sparqlreader = new FileReader(f);
					System.err.print("f");
				} else if(u!=null) {
					try {
						sparqlreader = new InputStreamReader(u.openStream());
						System.err.print("u");
					} catch (Exception e) {}
				}

				updates.set(i,"");
				BufferedReader in = new BufferedReader(sparqlreader);
				for(String line = in.readLine(); line!=null; line=in.readLine())
					updates.set(i,updates.get(i)+line+"\n");
				System.err.print(".");
			}

			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			String line;
			String lastLine ="";
			String buffer="";
			while((line = in.readLine())!=null) {
				line=line.replaceAll("[\t ]+"," ").trim();

				if(!buffer.trim().equals(""))
					if((line.startsWith("@") || line.startsWith("#")) && !lastLine.startsWith("@") && !lastLine.startsWith("#")) { //!buffer.matches("@[^\n]*\n?$")) {
						loadBuffer(buffer);
						if(CUSTOM) executeUpdate(updates);
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
			if(CUSTOM) executeUpdate(updates);
			unloadBuffer(buffer, new OutputStreamWriter(System.out));
		}
	}
}
