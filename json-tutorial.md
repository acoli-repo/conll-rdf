# CoNLL-RDF Manager: CoNLL-RDF Pipelines with JSON
One of the recommended ways to run a CoNLL-RDF pipeline is to use the CoNLL-RDF Manager.

You can run the CoNLL-RDF Manager from the shell, just like the other Java-classes.
```bash
./run.sh CoNLLRDFManager -c examples/analyze-ud.json
```
The Class takes a single argument: `-c`, followed by the path to the `.json` configuration file.

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
In `"input"` we store the path to a `.connlu.gz` file.  
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
As our input is CoNLLU, the first Class needs to be the Stream Extractor.  
We provide a base-URI and an Array of column names.
```json
"class" : "CoNLLRDFUpdater",
"updates" : [
  {"path":"examples/sparql/remove-IGNORE.sparql", "iter":"1"},
  {"path":"examples/sparql/analyze/UPOS-to-POSsynt.sparql", "iter":"1"},
  {"path":"examples/sparql/analyze/EDGE-to-POSsynt.sparql", "iter":"1"},
  {"path":"examples/sparql/analyze/consolidate-POSsynt.sparql", "iter":"1"}
]
```
The resulting RDF is passed to the Updater. Several SPARQL Update Queries are applied to strip the ignored data, and to compare the values in the UPOS and EDGE columns.
```json
"class" : "CoNLLRDFFormatter",
"modules" : [
  {"mode":"SPARQLTSV", "select": "examples/sparql/analyze/eval-POSsynt.sparql"}
] 
```
Lastly the RDF is passed to the Formatter, which is configured to output the result of a SPARQL Select Query as Tab-seperated Values.  
The output is streamed to the console.

See [the template](src/template.conf.json) for a full list of options, and the documentation for in-depth explanations of each.
