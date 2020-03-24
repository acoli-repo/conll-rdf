# CoNLL-RDF Manager: CoNLL-RDF Pipelines with JSON
One of the recommended ways to run a CoNLL-RDF pipeline is to use the CoNLL-RDF Manager.

You can run the CoNLL-RDF Manager from the shell, just like the other Java-classes.
```bash
./run.sh CoNLLRDFManager -c examples/analyze-ud.json
```
The Class takes a single argument: `-c`, followed by the path to the JSON-formatted configuration file.

Let's take a look at the structure:
```json
{
"input" : "",
"output" : "",
"pipeline" : [ ]
}
```
The entire configuration is stored in the `.json` file as an Object with the Keys `"input"`, `"output"` and `"pipeline"`.
```json
"input" : "data/ud/UD_English-master/en-ud-dev.conllu.gz"
```
In this case `"input"` is paired with the path to a `.connlu.gz` file.  
The tool recognizes the file-extension and decompresses the file before passing the stream to the first class in the pipeline.
```json
"output" : "System.out"
```
Here `"output"` is paired with the String `"System.out"`.  
System.out is a string with a special meaning. It tells the CoNLL-RDF-Manager that the last tool in the pipeline should default to streaming its output to the shell.
```json
"pipeline" : [
  { "class" : "CoNLLStreamExtractor", "..." },
  { "class" : "CoNLLRDFUpdater", "..." },
  { "class" : "CoNLLRDFFormatter", "..." }
]
```
The Key `"pipeline"` stores an Array of Objects. The order within the Array is the order in which the tools are chained.
```json
{ "class" : "CoNLLStreamExtractor"
  , "baseURI" : ""
  , "columns" : [ ]
},

{ "class" : "CoNLLRDFUpdater"
  , "updates" : [
      {"path":"", "iter":"1"},
	]
} ,

{ "class" : "CoNLLRDFFormatter"
  , "modules" : [
      {"mode":"SPARQLTSV", "select": "examples/sparql/analyze/eval-POSsynt.sparql"}
	]
}
```
Each Object in the Array corresponds to a class in the Pipeline, and contains the Key `"class"`, which stores the name of the class. The remaining Keys are specific to the class.
```json
"class" : "CoNLLStreamExtractor",  
"baseURI" : "https://github.com/UniversalDependencies/UD_English#",  
"columns" : ["IGNORE", "WORD", "IGNORE", "UPOS", "IGNORE", "IGNORE", "HEAD", "EDGE", "IGNORE", "IGNORE"]
```

```json
"class" : "CoNLLRDFUpdater",
"updates" : [
  {"path":"examples/sparql/remove-IGNORE.sparql", "iter":"1"},
  {"path":"examples/sparql/analyze/UPOS-to-POSsynt.sparql", "iter":"1"},
  {"path":"examples/sparql/analyze/EDGE-to-POSsynt.sparql", "iter":"1"},
  {"path":"examples/sparql/analyze/consolidate-POSsynt.sparql", "iter":"1"}
]
```

```json
"class" : "CoNLLRDFFormatter",
"modules" : [
  {"mode":"SPARQLTSV", "select": "examples/sparql/analyze/eval-POSsynt.sparql"}
] 
```
See [the template](src/template.conf.json) for a full list of options, and the documentation for in-depth explanations of each.

```yaml
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
		#OPTIONAL for cross-sentence processing
		#, "lookahead" : "0" 
		#, "lookback" : "0" 
		#OPTIONAL for debugging
		#, "threads" : "default" 
		#, "graphsoutDIR" : "PATH"
		#, "graphsoutSNT" : ["s1","s5"]
		#, "triplesoutDIR" : "PATH"
		#, "triplesoutSNT" : ["s1","s5"]
	} ,
	
	{ "class" : "CoNLLRDFFormatter",
		# must be called LAST in pipeline --> else, ERROR
		# multiple outputs can be generated simultaneously. (but need distinct outstreams, else ERROR)
		# if only one mode w/o specific outstream, use default output.
		# if NO mode: use "RDF" + default output
		"modules" : [
				# DEBUG always writes to System.err
				{"mode":"DEBUG"}
				, {"mode":"RDF", "columns": ["COL1", "COL2"], "output":"PATH"}
				, {"mode":"CONLL", "columns": ["COL1", "COL2"], "output":"PATH"}
				, {"mode":"SPARQLTSV", "select": "PATH", "output":"PATH"}
				# GRAMMAR and SEMANTICS can be combined
				, {"mode":"GRAMMAR", "output":"PATH"}
				, {"mode":"SEMANTICS", "output":"PATH"}
				, {"mode":"GRAMMAR+SEMANTICS", "output":"PATH"}
		]
	}
]
}
```
