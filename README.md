# CoNLL-RDF
__conll-rdf__ is a tool package for converting between formats of annotated linguistic corpora and annotations, as well as linking and enriching these with external ontologies.  
It consists of a number of JAVA modules which can be chained to construct a data processing pipeline. 

CoNLL-RDF can read and write any annotation format that fulfills the following criteria:
* tab-separated columns (TSV format, i.e., [CSV](https://datatracker.ietf.org/doc/html/rfc4180) with TAB as separator)
* every row represents one unit of annotation (e.g., a word) along with its annotations
* every column represents one type of annotation (e.g., string form, lemma, parts-of-speech, ...)
* empty lines as sentence separators (optional) 
* comments and headlines marked by `#` (optional)

In particular, it supports _all_ TSV formats used in previous shared tasks of the Conference of Natural Language Learning ([CoNLL](https://www.conll.org)), the formats of the Universal Dependencies ([UD: CoNLL-U, CoNLL-U Plus](https://universaldependencies.org/)), Universal Morphologies ([UniMorph](https://unimorph.github.io/)) and Universal Propositions ([UP: skel format](https://github.com/System-T/UniversalPropositions)) initiatives, and those of [SketchEngine](https://www.sketchengine.eu/) and [CWB](http://cwb.sourceforge.net/). Some of these formats introduce special notations that extend the basic TSV model (e.g., XML tags). CoNLL-RDF does support these, as well.

## What it can do
* convert _any_ CoNLL-like TSV format (e.g. `.conll`, `.conllu`, `.tsv`, `.skel`) to conll-rdf (`.ttl`).
* perform SPARQL Updates on conll-rdf data.
* visualize conll-rdf structure.
* convert conll-rdf back to (any) CoNLL format.

In particular, it serves as a ***generic converter*** between various CoNLL/TSV formats. For doing so, it requires the user to provide column labels. Also see [CoNLL-Transform](https://github.com/acoli-repo/conll-transform) for an effort on _automated_ transformation and transformation validation on this basis.

## How it works
In general, we read data line by line from `stdin`, process it and write results to `stdout`.  
For quick set-up, we recommend using `.sh` scripts to pipe your data through the tools.  
Each pipeline element can be called via `./run.sh $CLASS [args]`.  

Another method is to configure the pipeline in a config-json and call the entire pipeline with `./run.sh CoNLLRDFManager -c $config-json`.

Of course you can also use the provided classes within java as any other library.

## Installing
Download the repository from GitHub: `git clone https://github.com/acoli-repo/CoNLL-RDF.git`.
Then use `git submodule update --init --recursive` to clone the fintan-core submodule.

The project is compiled automatically whenever you run the tool via the `./run.sh` bash script.  
Experienced users may wish to modify the script to skip this for a small speed-up.

You can manually (re)compile it by calling the `./compile.sh` script or by using maven directly with `mvn clean compile` from the project root.

### Requirements
* Apache Maven (Latest release recommended. Expected to work from 3.3+).
  * run `mvn -version` to check if Maven is installed.
  * Maven will use the JDK in your JAVA_HOME system variable, so the java version listed here can differ from the one in your PATH.
* JDK (OpenJDK or Java SE), version 1.8 or higher.
  * run `java -version` to check if java is installed and in your PATH.
  * run `javac -version` to check if your version of the compiler is sufficient.

## Common Issues
* if maven doesn't find/ use the correct java installation, [try setting your JAVA_HOME](https://www.baeldung.com/maven-different-jdk)
* **if your pipelines broke with an update** in 2020-09 or soon after, you're likely calling the classes directly with `java`and not via `./run.sh`. You can change your scripts to call `./run.sh` (or copy the changes we made to `run.sh` into your scripts).
* you might get an error like `bash: ./../test.sh: Permission denied` when trying to run a script. Use this command to change the filemode: `chmod +x <SCRIPT>`
* an error starting like `ERROR CoNLLRDFUpdater :: SPARQL parse exception for Update No. 0: DIRECTUPDATE [...]` when running the RDFUpdater can be raised if the path to a sparql query is wrong. Check for extra or missing `../`.
* an error like `org.apache.jena.riot.RiotException: [line: 3, col: 12] Undefined prefix: rdfs` from CoNLLRDFUpdater even though the namespace in question is defined in the input: Make sure to use RDF 1.0 Turtle notation for prefixes (i.e., `@prefix bla: <...> .`), not the SPARQL-style notation (i.e., `PREFIX bla: <...>`) introduced with RDF 1.1 (cf. [issue #80](https://github.com/acoli-repo/conll-rdf/issues/80)). This can happen only if externally produced CoNLL-RDF data is consumed.

## Getting Started
All relevant classes are in [src/](./src/org/acoli/conll/rdf). Documentation for them is found in [doc/](doc/).

A [hands-on tutorial](./examples/README.md) with a variety of sample-pipelines, which you can adapt to your needs, can be found in [examples/](./examples). These convert data found in [data/](./data/ud/UD_English-master).

In case your corpus directly corresponds to a format found there you can directly convert it with given scripts into conll-rdf.

### Example
Suppose we have a corpus `example.conll` and want to convert it to conll-rdf to make it compatible with a given LLOD technology. We can do this with a simple shell-command:

```shell
cat example.conll | ./run.sh CoNLLStreamExtractor my-baseuri.org/example.conll# \
    ID WORD LEMMA POS_COARSE POS FEATS HEAD EDGE > example.ttl
```

This will create a new file `example.ttl` in conll-rdf by simply providing

* a base-URI (ideally a resolvable URL to adhere to the five stars of LOD).
* the names of the CoNLL columns from left to right.

### run.sh
`run.sh` is a wrapper script to make things feel more bash-like. It updates class files if necessary and runs the specified java class with the provided arguments.
* eg. `cat foo.ttl | ./run.sh CoNLLRDFFormatter > foo_formatted.ttl` would pipe `foo.ttl` through `CoNLLRDFFormatter` into `foo_formatted.ttl`.
* If necessary `run.sh`, will compile java classes from source. You may also run `compile.sh` independently.

## Features
In-depth information on all the classes of **conll-rdf** can be found in [the documentation.](doc/classes.md)

IMPORTANT USAGE HINT: All given data is parsed sentence-wise (if applicable). Meaning that for CoNLL data as input a newline is considered as a sentence boundary marker (in regard to the CoNLL data model). The ID column (if present) must contain sentence internal IDs (if this is not the case this column must be renamed before conversion/parsing) - if no such column is provided sentence internal IDs will be generated. Please refer to the paper mentioned below under Reference.

### CoNLLRDFManager
`CoNLLRDFManager` processes a pipeline provided as JSON.  
Synopsis: `CoNLLRDFManager -c [JSON-config]`

### CoNLLStreamExtractor
`CoNLLStreamExtractor` expects CoNLL from `stdin` and writes conll-rdf to `stdout`.  
Synopsis: `CoNLLStreamExtractor baseURI FIELD1[.. FIELDn] [-s SPARQL_SELECT]`

### CoNLLRDFUpdater
`CoNLLRDFUpdater` expects conll-rdf from `stdin` and writes conll-rdf to `stdout`. It is designed for updating existing conll-rdf files and is able to load external ontologies or RDF data into separate Graphs during runtime. This is especially useful for linking CoNLL-RDF files to other ontologies.  
It can also output `.dot` graph-files (or triples `stdout`).  
Synopsis: `CoNLLRDFUpdater -custom [-model URI [GRAPH]] [-updates [UPDATE]]`

### CoNLLRDFFormatter
`CoNLLRDFFormatter` expects conll-rdf in `.ttl` and writes to different formats. Can also visualize your data.  
Synopsis: ```CoNLLRDFFormatter [-rdf [COLS]] [-debug] [-grammar] [-semantics] [-conll COLS] [-query SPARQL]```

It can write:
* canonical conll-rdf as `.ttl`.
* `.conll` of specified columns.
* TSV to `stdout` based on a given sparql select query.
* `debug` highlighted `.ttl` to `stderr`, e.g. highlighting triples representing conll columns or sentence structure differently.
> ![-debug](doc/img/conllrdfformatter-debug.png)  
> Example from universal dependencies.
* `grammar`: writes conll data structure in tree-like format to `stdout`. (`/` resp. `\` are pointing in direction of `conll:HEAD`)
> ![-grammar](doc/img/conllrdfformatter-grammar.png)  
> Example from universal dependencies.
* `semantics` seperate visualization of object properties of `conll:WORD` using `terms:` namespace, useful for visualizing knowledge graphs. **`EXPERIMENTAL`**

### CoNLLRDFAnnotator
* can be used to manually annotate / change annotations in .ttl files.
* will visualize input just like `CoNLLRDFFormatter -grammar`. Will not make in-place changes but write the changed file to `stdout` (e.g. `./run.sh CoNLLRDFAnnotator file_old.ttl > file_new.ttl`)
	* **Note**: Make sure that input file (`file_old.ttl`) and output file (`file_new.ttl`) are **different**. Piping output into old file in this way is **not** supported (by your operating system)! Will result in data loss.

### Other
* `CoNLL2RDF` contains the central conversion functionality. For practical uses, interface with its functionality through CoNLLStreamExtractor. Arguments to CoNLLStreamExtractor will be passed through.
* `CoNLLRDFViz` is an auxiliary class for the future development of debugging and visualizing complex SPARQL Update chains in their effects on selected pieces of CoNLL(-RDF) data
* conll-rdf assumes UTF-8.

## Authors and Maintainers
* **Christian Chiarcos** - chiarcos@informatik.uni-frankfurt.de
* **Christian Fäth** - faeth@em.uni-frankfurt.de
* **Benjamin Kosmehl** (2017-2018) - bkosmehl@gmail.com
* **Luis Glaser** (2018-2020)
* [**Leona 'leo' Gottfried**](https://github.com/leogott) (2020-)

See also the list of [contributors](https://github.com/acoli-repo/conll-rdf/graphs/contributors) who participated in this project.

## Reference
* Chiarcos C., Fäth C. (2017), CoNLL-RDF: Linked Corpora Done in an NLP-Friendly Way. In: Gracia J., Bond F., McCrae J., Buitelaar P., Chiarcos C., Hellmann S. (eds) Language, Data, and Knowledge. LDK 2017. pp 74-88.

## Acknowledgments
This repository has been created in context of
* Applied Computational Linguistics ([ACoLi](http://acoli.cs.uni-frankfurt.de))
* Specialised Information Service Linguistics ([FID](https://www.ub.uni-frankfurt.de/projekte/fid-linguistik_en.html))
  * funded by the German Research Foundation (DFG, funding code CH1413/2-1)
* Linked Open Dictionaries ([LiODi](http://www.acoli.informatik.uni-frankfurt.de/liodi/))
  * funded by the German Federal Ministry of Education and Research (BMBF, funding code 01UG1631)
* QuantQual@CEDIFOR ([QuantQual](http://acoli.cs.uni-frankfurt.de/projects.html#quantqual))
  * funded by the Centre for the Digital Foundation of Research in the Humanities, Social and Educational Science (CEDIFOR, funding code 01UG1416A)
* Prêt-á-LLOD. Ready-to-use Multilingual Linked Language Data for Knowledge Services across Sectors ([Pret-a-LLOD](https://cordis.europa.eu/project/id/825182/results))
  * Research and Innovation Action of the H2020 programme (ERC, grant agreement 825182)
  * In this project, CoNLL-RDF has been applied/developed/restructured to serve as backend of the Flexible Integrated Transformation and Annotation Engineering ([FINTAN](https://github.com/Pret-a-LLOD/Fintan)) Platform.

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
└── run.sh  
```
### LICENCE.data (CC-BY 4.0)
```
├── data/  
└── examples/  
	└── sparql/  
```
Please cite *Chiarcos C., Fäth C. (2017), CoNLL-RDF: Linked Corpora Done in an NLP-Friendly Way. In: Gracia J., Bond F., McCrae J., Buitelaar P., Chiarcos C., Hellmann S. (eds) Language, Data, and Knowledge. LDK 2017. pp 74-88*.
