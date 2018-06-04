package org.acoli.conll.rdf;
import java.io.*;
import java.util.*;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceRequiredException;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateFactory;

/* auxiliary class to develop DOT/graphviz viz for CoNLL-RDF */
public class CoNLLRDFViz {

	protected static String dotId(Resource r) {
		return "B"+r.getNameSpace()+r.getLocalName();
	}
	
	protected static String name(Resource r) {
		if(r.isAnon()) return "_:"+r.getId();
		return (r.getModel().getNsURIPrefix(r.getNameSpace())+":"+r.getLocalName()).replaceFirst("^null:", "");
	}
	
	public static void produceDot(Reader in, Writer out) throws IOException {
		
		Model m = ModelFactory.createDefaultModel().read(in,null,"TTL");
		
		ResIterator sbjs;

		// header
		out.write("digraph {\n"+
				  "charset=\"utf-8\";\n"+
				"#rankdir=LR;\n"+
				"\n");

		// upper subgraph: dep view
		
		String DEP_SFX = "_conll"; // ""; // "_conll"; // set to "" to merge both graphs
		out.write("subgraph cluster_deps {\n"
				+ "graph [ label=<<b>CoNLLView</b>>, size=\"12,12\", color=\"white\" ];\n");
		

		Property rdfType = m.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
		Resource nifWord = m.createResource("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Word");
		Resource nifSentence = m.createResource("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Sentence");
		Property nifNextSentence = m.createProperty("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#nextSentence");
		Property nifNextWord = m.createProperty("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#nextWord");
		Property conllEDGE = m.createProperty("http://ufal.mff.cuni.cz/conll2009-st/task-description.html#EDGE");
		
		sbjs=m.listSubjectsWithProperty(
				rdfType, 
				nifWord);
		while(sbjs.hasNext()) {
			Resource sbj = sbjs.next();
			String dotid = dotId(sbj);
			String label = "<table border='0' cellborder='0' cellspacing='0'>";
			if(!sbj.isAnon())
				label=label+"<tr><td colspan='2' align='center'>"+name(sbj)+"</td></tr>";
			if(sbj.hasProperty(rdfType)) {
				label=label+"<tr><td colspan='2' align='center'>a ";
				StmtIterator props = sbj.listProperties(rdfType);
				while(props.hasNext()) {
					label=label+name(props.next().getObject());
					if(props.hasNext())
						label=label+", ";
				}
				label=label+"</td></tr>";
			}
			for(Statement st : sbj.listProperties().toList())
				if(st.getObject().isLiteral())
					label=label+"<tr><td align='left'>"+st.getObject()+"</td><td align='right'><sub>"+name(st.getPredicate())+"</sub></td></tr>";

			label=label+"</table>";

			out.write("\""+dotid+DEP_SFX+"\" [label=<"+label+">,shape=box,color=gray];\n");
		}

		// edges
		StmtIterator stmts = m.listStatements();
		while(stmts.hasNext()) {
			Statement stmt = stmts.next();

			try {
				Resource sbj = stmt.getSubject();
				RDFNode obj = stmt.getObject();
				Property prop = stmt.getPredicate();
				if(m.contains(sbj, rdfType, nifWord)
						&& m.contains(obj.asResource(), rdfType, nifWord)
						) {
					String s = dotId(sbj);
					String p = name(prop);
					String o = dotId(obj);
					out.write("\""+s+DEP_SFX+"\" -> \""+o+DEP_SFX+"\" ");
					if(p.equals("nif:nextWord"))
						out.write("[label=\" \", color=\"gray\", weight=\"10\"];\n");
					else if(p.equals("conll:HEAD")) {
						String edge = "null";
						try {
							if(sbj.hasProperty(conllEDGE))
								edge = sbj.getProperty(conllEDGE).getLiteral().toString();
						} catch(NullPointerException e) {
							e.printStackTrace();
						}
						out.write("[weight=\"0\", constraint=\"false\", label=\""+edge+"\"];\n");
					} else 
						out.write("[weight=\"0\", constraint=false, color=\"blue\", fontfolor=\"blue\",label=\""+p+"\"];\n");
				};
			} catch (ResourceRequiredException e) { // obj.asResource()
				// e.printStackTrace();
			}
		}
						
		out.write("}\n\n");
				
		// right subgraph: graph view

		out.write("subgraph cluster_graph {\n"
					+ "graph [ label=<<b>GraphView</b>>, size=\"12,12\", color=\"white\" ];\n");
		
		sbjs=m.listSubjects();
		while(sbjs.hasNext()) {
			Resource sbj = sbjs.next();
			String dotid = dotId(sbj);
			String label = "<table border='0' cellborder='0' cellspacing='0'>";
			if(!sbj.isAnon())
				label=label+"<tr><td colspan='2' align='center'>"+name(sbj)+"</td></tr>";
			if(sbj.hasProperty(rdfType)) {
				label=label+"<tr><td colspan='2' align='center'>a ";
				StmtIterator props = sbj.listProperties(rdfType);
				while(props.hasNext()) {
					label=label+name(props.next().getObject());
					if(props.hasNext())
						label=label+", ";
				}
				label=label+"</td></tr>";
			}
			for(Statement st : sbj.listProperties().toList())
				if(st.getObject().isLiteral())
					label=label+"<tr><td align='left'>"+st.getObject()+"</td><td align='right'><sub>"+name(st.getPredicate())+"</sub></td></tr>";

			label=label+"</table>";

			out.write("\""+dotid+"\" [label=<"+label+">,shape=box,color=");
			if(m.contains(sbj,rdfType,nifWord)) out.write("gray");
			else if(m.contains(sbj,rdfType,nifSentence) || sbj.hasProperty(nifNextSentence))
				out.write("black");
			else out.write("blue");
			out.write("];\n");
		}
		
		// all being equally ranked
		sbjs=m.listSubjectsWithProperty(
				m.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), 
				m.createResource("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Word"));
	
		out.write("\"B0\" [label=\"\", shape=box, color=invis];\n");
		while(sbjs.hasNext())
			out.write("\"B0\" -> \""+dotId(sbjs.next())+"\" [color=\"invis\"];\n");
	
		// all being equally ranked
		sbjs=m.listSubjectsWithProperty(
				m.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), 
				m.createResource("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Word"));
	
		out.write("{ rank=same ");
		while(sbjs.hasNext())
			out.write("\""+dotId(sbjs.next())+"\" ");
		out.write("};\n");

		// edges
		stmts = m.listStatements();
		while(stmts.hasNext()) {
			Statement stmt = stmts.next();

			try {
				Resource sbj = stmt.getSubject();
				RDFNode obj = stmt.getObject();
				Property prop = stmt.getPredicate();
				String s = dotId(sbj);
				String p = name(prop);
				String o = dotId(obj);
				if(obj.isResource() && obj.asResource().listProperties().hasNext()) {
					if(!prop.equals(rdfType) && obj.isResource()
							&& (!p.equals("conll:HEAD") || !m.contains(obj.asResource(),rdfType,nifWord))
							) {
						out.write("\""+s+"\" -> \""+o+"\" ");
						if(p.equals("nif:nextWord"))
							out.write("[color=\"invis\", weight=\"10\"];\n");
						else if(p.equals("nif:nextSentence")) {
							out.write("[label=\""+p+"\", color=\"black\"];\n");
						} else if(p.equals("conll:HEAD")) { // no head, this is dealt with in the conll view
							String edge = "null";
							try {
								if(sbj.hasProperty(conllEDGE))
									edge = sbj.getProperty(conllEDGE).getLiteral().toString();
							} catch(NullPointerException e) {
								e.printStackTrace();
							}
							out.write("[label=\""+edge+"\"];\n");
						} else 
							out.write("[weight=\"100\", color=\"blue\", fontfolor=\"blue\",label=\""+p+"\"];\n");
					}
				};
			} catch (ResourceRequiredException e) { // obj.asResource()
				// e.printStackTrace();
			}
		}
		
		// connect all nif:nextWord pairs by adding a common invisible node they dominate
		int i = 0;
		for(Statement st : m.listStatements((Resource)null, nifNextWord, (RDFNode)null).toSet()) {
			out.write("\"B"+(++i)+"\" [label=\"\", shape=box, color=invis];\n");
			out.write("\""+dotId(st.getSubject())+"\" -> \"B"+i+"\" [color=\"invis\",weight=\"10\"];\n");
			out.write("\""+dotId(st.getObject())+"\" -> \"B"+i+"\" [color=\"invis\",weight=\"10\"];\n");
		}
		

		// connect all nif:nextSentence pairs by setting them to equal rank
		for(Statement st : m.listStatements((Resource)null, nifNextSentence, (RDFNode)null).toSet())
			if(st.getObject().asResource().listProperties().hasNext()) {
				out.write("{ rank=same ");
				out.write("\""+dotId(st.getSubject())+"\" ");
				out.write("\""+dotId(st.getObject())+"\" };\n");
			}

						
		out.write("}\n\n");
		
	}
	
	private static String name(RDFNode object) {
			return name(object.asResource());
	}

	private static String dotId(RDFNode object) {
			return dotId(object.asResource());
	}

	public static void main(String[] argv) throws Exception {
		System.err.println("CoNLLRDFViz\n"
				+ "reads CoNLL-RDF from stdin and produces a dot graph, should be run on a single sentence\n"
				+ "we recommended to use this in combination with command line tools that filter out individual sentences\n"
				+ "e.g., by using egrep -B 10 -i 's1_|prefix' to retrieve the first sentence from canonically formatted CoNLLRDF\n"
				+ "The output can be serialized to gif (and other formats) by feeding it into  dot -Tgif > tiger.gif\n"+
				"Also cf. http://www.webgraphviz.com/ or https://github.com/mdaines/viz.js/.\n"
				+ "Note that we provide two separate subgraphs: \n"+
				" the CoNLL View visualizes annotations of and relations between nif:Words, and\n"
				+ "the graph view visualizes (any kind of) other relations.\n"
				+ "Also note that this comes without warranty and the rendering of complex files may fail in GraphViz.");
		produceDot(new InputStreamReader(System.in), new OutputStreamWriter(System.out));
	}

}
