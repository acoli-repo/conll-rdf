## Getting Started
Check out [the readme.](../README.md)

We also provide a [hands-on tutorial](./examples) which comes with prepared scripts and data.

### run.sh
`run.sh` is used to make things feel more bash-like. It determines the classpath, updates class files if necessary and runs the specified java class with the provided arguments. 
* eg. `cat foo.ttl | ./run.sh CoNLLRDFFormatter > foo_formatted.ttl` would pipe `foo.ttl` through `CoNLLRDFFormatter` into `foo_formatted.ttl`.
* In case the respective `.class` files cannot be found, `run.sh` calls `compile.sh` to compile the java classes from source. Of course, you may also run `compile.sh` independently.

## Classes
In this document you can find detailed information on all functionalities of **conll-rdf**.

All relevant classes are in [src/](../src/org/acoli/conll/rdf). Sample scripts in [examples/](../examples). These convert data found in [data/](../data/ud/UD_English-master). In case your corpus directly corresponds to a format found there you can directly convert it with given scripts into conll-rdf. 

IMPORTANT USAGE HINT: All given data is parsed sentence-wise (if applicable). Meaning that for CoNLL data as input a newline is considered as a sentence boundary marker (in regard to the CoNLL data model). The ID column (if present) must contain sentence internal IDs (if this is not the case this column must be renamed before conversion/parsing) - if no such column is provided sentence internal IDs will be generated. Please refer to the paper mentioned below under Reference.

### Universal Flags
All conll-rdf pipeline components accept the following flags:

* `help` Display usage help and exit.
* `silent` Do not show info messages. Only messages of level warn and above will be logged to console.

### CoNLLRDFManager
`CoNLLRDFManager` processes a pipeline provided as JSON.
Synopsis: `CoNLLRDFManager -c [JSON-config]`
* `-c [JSON-config]` (required): provide the path to a json-file.

### CoNLLStreamExtractor
`CoNLLStreamExtractor` expects CoNLL from `stdin` and writes conll-rdf to `stdout`.  
Synopsis: ```CoNLLStreamExtractor baseURI FIELD1[.. FIELDn] [-u SPARQL_UPDATE1..m] [-s SPARQL_SELECT]```

* `baseURI` (required): ideally a resolvable URL to adhere to the five stars of LOD.
* `FIELD1[.. FIELDn]`: name each column of input conll.
       * this option overrides any column names specified in the comments of the input.
       * If no fields are provided here, we check the first line of the input for a `# global.columns = [FIELDS]` comment, as specified in [CoNLL-U Plus](https://universaldependencies.org/ext-format.html).
	* note that `CoNLLStreamExtractor` will not check if the fields match the input. Make sure the number of fields matches the number of columns of your CoNLL input. 
* `[-u SPARQL_UPDATE1 .. m]` (**deprecated**): It is recommended you use`CoNLLRDFUpdater -custom -updates [SPARQL_UPDATE1 .. m]` instead.
* `[-s SPARQL_SELECT]` (optional): select query for generating TSV output.

### CoNLLRDFUpdater
`CoNLLRDFUpdater` expects conll-rdf from `stdin` and writes conll-rdf to `stdout`. It is designed for updating existing conll-rdf files and is able to load external ontologies or RDF data into separate Graphs during runtime. This is especially useful for linking CoNLL-RDF files to other ontologies.  
Synopsis:
```
CoNLLRDFUpdater [-loglevel LEVEL] [-threads T] [-lookahead N] [-lookback N]
	[-custom
		[-model URI [GRAPH]]*
		[-graphsout DIR [SENT_ID]] [-triplesout DIR [SENT_ID]]
		-updates [UPDATE]]
```
#### optimisation:
* `loglevel LEVEL`: set log level to LEVEL
* `threads T`: use at most T threads
* `lookahead N`: cache N following sentences in lookahead graph
* `lookback N`: cache N preceeding sentences in lookback graph
             default: half of available logical processor cores

#### common:
* `custom`: required command-line argument for any of the arguments below
* `updates [SPARQL_UPDATE]+`: applies updates to the CoNLL-RDF.
  * `[SPARQL_UPDATE]` (required): List of paths to SPARQL Updates to apply.
	* `{x}` (optional for each): max. number of times to apply the update for.  
	e.g. `-updates examples/sparql/remove-ID.sparql{5}`. Will repeat the update x times or until model stops changing.
	* `{u}` (optional): for unlimited will repeat up to 999 times.
  * Hint: `SPARQL_UPDATE` can be an update-query as a String. If this String is passed on by BASH/SHELL the String must be enclosed in \`-quotation marks (will be otherwise split into several arguments)!
* `model URI [GRAPH]` (optional): List of external resources to be loaded before updating.
	* `URI` (required): Path to external ontology. Will be pre-loaded by the Updater and available for the whole runtime.
	* `GRAPH` (optional): GRAPH into which the ontology should be loaded. If empty: `URI` is used as graph name.

#### graphs:
* `graphsout DIR [SENT_ID]`: create .dot graph files for sentence models
  * `DIR` (required): Path of the directory to output the graphs to.
  * `SENT_ID` (optional): IDs of sentences to visualize.  
  default: first sentence only
* `triplesout`: same as graphsout but write N-TRIPLES for text debug instead.  
otherwise identical to `graphsout`.

### CoNLLRDFFormatter
`CoNLLRDFFormatter` expects conll-rdf in `.ttl` and writes to different formats. Can also visualize your data.  
Synopsis: ```CoNLLRDFFormatter [-rdf [COLS]] [-debug] [-grammar] [-semantics] [-conll COLS] [-query SPARQL]```

* `rdf` (default): writes canonical conll-rdf as .ttl.
* `conll [COLS]`: writes .conll of specified columns in order of arguments. 
  * If no cols are provided, we assume the original conll was [CoNLL-U Plus](https://universaldependencies.org/ext-format.html).
    We first search for a `rdfs:comment` property with a `global.columns` comment, then the raw conll-like comments.
  * Note that we will overwrite any `global.columns` comments with the new column names, so you may read them with the `CoNLLStreamExtractor` again right away.
* `query [SPARQL-SELECT-FILE]`: writes TSV to `stdout` based on a given sparql select query.
  * column sequence is based on selector sequence within the sparql query.
  * writes the column names as a comment line before every sentence.
* _DEPRECATED_ `sparqltsv` was deprecated in favor of `query`.
  * functionally identical to `query`.
  * has some undocumented behavior that can't be tested easily and was left untouched.
* `debug`: writes highlighted .ttl to `stderr`, e.g. highlighting triples representing conll columns or sentence structure differently. 
  * does not work in combination with `-conll`.
  * to add custom highlighting you can add rules to `colorTTL(String buffer)` in `CoNLLRDFFormatter.java`. Don't forget to recompile!
> ![-debug](img/conllrdfformatter-debug.png)  
> Example from universal dependencies.

* `grammar`: writes conll data structure in tree-like format to `stdout`.
  * Indents are based on `conll:HEAD` relation.
  * `/` resp. `\` are pointing in direction of `conll:HEAD`.
> ![-grammar](img/conllrdfformatter-grammar.png)  
> Example from universal dependencies.
> 
* `semantics`: seperate visualization of object properties of `conll:WORD` using `terms:` namespace, useful for visualizing knowledge graphs. **`EXPERIMENTAL`**

### CoNLLRDFAnnotator
* can be used to manually annotate / change annotations in `.ttl` files. 
* will visualize input just like `CoNLLRDFFormatter -grammar`. Will not make in-place changes but write the changed file to `stdout`
  * e.g. `./run.sh CoNLLRDFAnnotator file_old.ttl > file_new.ttl`.
* **Note**: Piping output into old file is **not** supported! Will result in data loss.

### Other
* `CoNLL2RDF` contains the central conversion functionality. For practical uses, interface with its functionality through CoNLLStreamExtractor. Arguments to CoNLLStreamExtractor will be passed through.
* `CoNLLRDFViz` is an auxiliary class for the future development of debugging and visualizing complex SPARQL Update chains in their effects on selected pieces of CoNLL(-RDF) data
* conll-rdf assumes UTF-8.
