
# conll-rdf

tool package for converting .conll files into conll-rdf and back. We support a variety of CoNLL formats. conll-rdf consists of a number of java modules which can be stacked to construct a data processing pipeline. 

## Getting Started

### Installing


java is required, use `java --version` to check if you have java installed.

All required java libraries are contained in [lib/](./lib).

* `run.sh` is used to make things feel more bash-like. It determines the classpath, updates class files if necessary and runs the specified java class with the provided arguments. 
* eg. `cat foo.ttl | ./run.sh CoNLLRDFFormatter > foo_formatted.ttl` would pipe `foo.ttl` through `CoNLLRDFFormatter` into `foo_formatted.ttl`.
* In case the respective `.class` files cannot be found, `run.sh` calls `compile.sh` to compile the java classes from source. Of course, you may also run `compile.sh` independently.

### How it works


Getting **conll-rdf** ready can be acomplished easily, see [Installing](#Installing). In general, we read data line by line from `stdin`, process it and write results to `stdout`. For quick setup we recommend using .sh scripts to direct your data through your pipeline. Then, each pipeline element can be called via `./run $CLASS [args]`. A variety of sample scripts can be found in [examples/](./examples) which you can adapt to your needs. Of course you can also use the provided classes within java as any other library.

### What it can do


* convert any CoNLL-like tsv format to conll-rdf.
* perform SPARQL Updates on conll-rdf data.
* visualize conll-rdf structure.
* convert conll-rdf back to conll.

### Example


Suppose we have a corpus `example.conll` and want to convert it to conll-rdf to make it compartible with a given LLOD technology. We can do this with a simple shell-command:

```shell
cat example.conll | ./run.sh CoNLLStreamExtractor my-baseuri.org/example.conll# \
    ID WORD LEMMA POS_COARSE POS FEATS HEAD EDGE > example.ttl
```

This will create a new file `example.ttl` in conll-rdf by simply providing

* a base-URI (ideally a resolvable URL to adhere to the five stars of LOD).
* the names of the CoNLL columns from left to right. 


## Usage


In this chapter you can find detailed information on all functionalities of **conll-rdf**.

All relevant classes are in [src/](./src/org/acoli/conll/rdf). Sample scripts in [examples/](./examples). These convert data found in [data/](./data/ud/UD_English-master). In case your corpus directly corresponds to a format found there you can directly convert it with given scripts into conll-rdf. 

### CoNLLStreamExtractor


`CoNLLStreamExtractor` expects CoNLL from `stdin` and writes conll-rdf to `stdout`.   
Synopsis: ```CoNLLStreamExtractor baseURI FIELD1[.. FIELDn] [-u SPARQL_UPDATE1..m] [-s SPARQL_SELECT]```

* `baseURI` (required): ideally a resolvable URL to adhere to the five stars of LOD.
* `FIELD1[.. FIELDn]` (required): name each column of input conll. 
	* at least one column name is required.
	* note that `CoNLLStreamExtractor` will not check the number of columns. Make sure the list of fields match the number of columns of your CoNLL input. 
* `[-u SPARQL_UPDATE1 .. m]`: execute sparql updates before writing to stdout. 
	* can be followed by an optional integer x in {}-parentheses = number of repetitions e.g. `-u examples/sparql/remove-ID.sparql{5}`. Will repeat the update x times or until model stops changing.
		* `{u}` for unlimited will repeat 999 times.
	* See [examples/](./examples) for reference. 
* `[-s SPARQL_SELECT]`: Optional select statement for generating TSV output.

### CoNLLRDFUpdater


`CoNLLRDFUpdater` expects conll-rdf from `stdin` and writes conll-rdf to `stdout`. It is designed for updating existing conll-rdf files and is able to load external ontologies or RDF data into separate Graphs during runtime. This is especially useful for linking CoNLL-RDF files to other ontologies.  
Synopsis: ```CoNLLRDFUpdater -custom [-model URI [GRAPH]]* -updates [UPDATE]+```

* `-custom [-model URI [GRAPH]]* -updates [UPDATE]+` : for executing customized set of SPARQL updates
	* `-updates [SPARQL_UPDATE1 .. m]` (required): List of paths to SPARQL scripts. Will be executed before writing to stdout.
		* can be followed by an optional integer x in {}-parentheses = number of repetitions e.g. `-u examples/sparql/remove-ID.sparql{5}`. Will repeat the update x times or until model stops changing.
			* `{u}` for unlimited will repeat 999 times.
	* `-model URI [GRAPH]` (optional): List of external resources to be loaded before updating.
		* `URI` (required): Path to external ontology. Will be pre-loaded by the Updater and available for the whole runtime.
		* `GRAPH` (optional): GRAPH into which the ontology should be loaded. If empty: `URI` is used as graph name.


### CoNLLRDFFormatter

`CoNLLRDFFormatter` expects conll-rdf in `.ttl` and writes to different formats. Can also visualize your data.

* `-rdf` (default): writes canonical conll-rdf as .ttl.
* `-conll [COLS]`: writes .conll of specified columns in order of arguments.  At least one COL must be provided, otherwise writes original conll-rdf.
* `-debug`: writes highlighted .ttl to `stderr`, e.g. highlighting triples representing conll columns or sentence structure differently. 
  * does not work in combination with `-conll`.
  * to add custom highlighting you can add rules to `colorTTL(String buffer)` in `CoNLLRDFFormatter.java`. Don't forget to recompile!
  
> ![-debug](doc/img/conllrdfformatter-debug.png)  
> Example from universal dependencies.

* `-grammar`: writes conll data structure in tree-like format to `stdout`.
  * Indents are based on `conll:HEAD` relation.
  * `/` resp. `\` are pointing in direction of `conll:HEAD`.

> ![-grammar](doc/img/conllrdfformatter-grammar.png)  
> Example from universal dependencies.

* `-sparqltsv [SPARQL-SELECT-FILE]`: writes TSV to `stdout` based on a given sparql select query.
  * column sequence is based on selector sequence within the sparql query.
  * writes the column names as a comment line before every sentence.

* `-semantics`: seperate visualization of object properties of `conll:WORD` using `terms:` namespace, useful for visualizing knowledge graphs. **`EXPERIMENTAL`**

### CoNLLRDFAnnotator


* can be used to manually annotate / change annotations in .ttl files. 
* will visualize input just like `CoNLLRDFFormatter -grammar`. Will not make in-place changes but write the changed file to `stdout`
  * e.g. `./run.sh CoNLLRDFAnnotator file_old.ttl > file_new.ttl`.
* **Note**: Piping output into old file is **not** supported! Will result in data loss.

### Other


* `CoNLL2RDF` contains the central conversion functionality. For practical uses, interface with its functionality through CoNLLStreamExtractor. Arguments to CoNLLStreamExtractor will be passed through.
* conll-rdf assumes UTF-8.

## Authors

* **Christian Chiarcos** - chiarcos@informatik.uni-frankfurt.de
* **Christian Fäth** - faeth@em.uni-frankfurt.de
* **Benjamin Kosmehl** - bkosmehl@gmail.com

See also the list of [contributors](https://github.com/acoli-repo/conll-rdf/graphs/contributors) who participated in this project.

## Reference


* Chiarcos C., Fäth C. (2017), CoNLL-RDF: Linked Corpora Done in an NLP-Friendly Way. In: Gracia J., Bond F., McCrae J., Buitelaar P., Chiarcos C., Hellmann S. (eds) Language, Data, and Knowledge. LDK 2017. pp 74-88.

## Acknowledgments


This repository has been created in context of 
* Applied Computational Linguistics ([ACoLi](http://acoli.cs.uni-frankfurt.de))
* Specialised Information Service Linguistics ([FID](https://www.ub.uni-frankfurt.de/projekte/fid-linguistik_en.html)) - CH1413/2-1
* Linked Open Dictionaries ([LiODi](http://www.acoli.informatik.uni-frankfurt.de/liodi/)) - 01UG1631
* QuantQual@CEDIFOR ([QuantQual](http://acoli.cs.uni-frankfurt.de/projects.html#quantqual)) - 01UG1416A


## Licenses


This repository is being published under two licenses. Apache 2.0 is used for code, see [LICENSE.main](LICENSE.main.txt). CC-BY 4.0 for all data from universal dependencies and SPARQL scripts, see [LICENSE.data](LICENSE.data.txt).

### LICENCE.main (Apache 2.0)
```
├── src/  
├── lib/  
├── examples/  
│	├── analyze-ud.sh  
│	├── convert-ud.sh  
│	├── link-ud.sh  
│	└── parse-ud.sh  
├── compile.sh  
├── run.sh  
```
### LICENCE.data (CC-BY 4.0)
```
├── data/  
├── examples/  
	└── sparql/  
```

Please cite *Chiarcos C., Fäth C. (2017), CoNLL-RDF: Linked Corpora Done in an NLP-Friendly Way. In: Gracia J., Bond F., McCrae J., Buitelaar P., Chiarcos C., Hellmann S. (eds) Language, Data, and Knowledge. LDK 2017. pp 74-88*.

