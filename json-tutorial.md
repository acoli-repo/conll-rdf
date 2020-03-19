#CoNLL-RDF Pipelines with Json
CoNLL-RDF tools can be called from the shell individually and chained with pipes, but there is a better method.
##CoNLLRDFManager
You can run the CoNLL-RDF_Manager just like the other Java-classes.
```bash
./run.sh CoNLLRDFManager -c examples/analyze-ud.json
```
But unlike the other tools, the configuration isn't done in the shell.  
The entire Pipeline is configured in the json document that we pass along with the `-c` flag.

Let's take a look at the structure:
```json
{
```
The outermost struct is a dictionary with the keys "input", "output", "pipeline".
```json
"input" : "data/ud/UD_English-master/en-ud-dev.conllu.gz"
```
"input" in this case has the path to a .connlu.gz file.  
The tool recognizes the file-extension and decompresses the file before passing the stream to the first class in the pipeline.
```json
, "output" : "System.out"
```
"output" has the literal "System.out".  
System.out doesn't represent a file, but a stream to the shell. The last tool in the pipeline will stream its output to the shell.
```json
, "pipeline" : [
```
"pipeline" stores a list of dicts. The order within the list is the order in which the tools are chained.
```json
	{ "class" : "CoNLLStreamExtractor",
		"baseURI" : "https://github.com/UniversalDependencies/UD_English#",
		"columns" : ["IGNORE", "WORD", "IGNORE", "UPOS", "IGNORE", "IGNORE", "HEAD", "EDGE", "IGNORE", "IGNORE"]
	},
	
	{ "class" : "CoNLLRDFUpdater"
		, "updates" : [
			{"path":"examples/sparql/remove-IGNORE.sparql", "iter":"1"},
			{"path":"examples/sparql/analyze/UPOS-to-POSsynt.sparql", "iter":"1"},
			{"path":"examples/sparql/analyze/EDGE-to-POSsynt.sparql", "iter":"1"},
			{"path":"examples/sparql/analyze/consolidate-POSsynt.sparql", "iter":"1"}
		]
	} ,
	
	{ "class" : "CoNLLRDFFormatter"
		, "modules" : [
			{"mode":"SPARQLTSV", "select": "examples/sparql/analyze/eval-POSsynt.sparql"}
		]
	}
]
}
```

Each entry in the list contains the key "class", which stores the name of the class to be used. The remaining keys vary depending on this class.

See [the template](src/template.conf.json) for more options.

```json
{
"input" : "PATH"
, "output" : "System.out"
, "pipeline" : [ 

	{ "class" : "CoNLLStreamExtractor",
		"baseURI" : "URI",
		"columns" : ["COL1", "COL2"]
	},
	
	{ "class" : "CoNLLRDFUpdater"
		, "updates" : [
			{"path":"PATH", "iter":"1"}, 
			{"path":"sparql/test.sparql", "iter":"*"}
		]
		, "models" : [
			{"source":"URI", "graph":"URI"},
			{"source":"URI", "graph":"URI"}
		]
		//OPTIONAL for cross-sentence processing
		//, "lookahead" : "0" 
		//, "lookback" : "0" 
		//OPTIONAL for debugging
		//, "threads" : "default" 
		//, "graphsoutDIR" : "PATH"
		//, "graphsoutSNT" : ["s1","s5"]
		//, "triplesoutDIR" : "PATH"
		//, "triplesoutSNT" : ["s1","s5"]
	} ,
	
	{ "class" : "CoNLLRDFFormatter",
		// must be called LAST in pipeline --> else, ERROR
		// multiple outputs can be generated simultaneously. (but need distinct outstreams, else ERROR)
		// if only one mode w/o specific outstream, use default output.
		// if NO mode: use "RDF" + default output
		"modules" : [
				// DEBUG always writes to System.err
				{"mode":"DEBUG"}
				, {"mode":"RDF", "columns": ["COL1", "COL2"], "output":"PATH"}
				, {"mode":"CONLL", "columns": ["COL1", "COL2"], "output":"PATH"}
				, {"mode":"SPARQLTSV", "select": "PATH", "output":"PATH"}
				// GRAMMAR and SEMANTICS can be combined
				, {"mode":"GRAMMAR", "output":"PATH"}
				, {"mode":"SEMANTICS", "output":"PATH"}
				, {"mode":"GRAMMAR+SEMANTICS", "output":"PATH"}
		]
	}
]
}
```
