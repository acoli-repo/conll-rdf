# Hands-on CoNLL-RDF Tutorial
In this document:
* [CoNLL-RDF with piping in Bash](#i-converting-conll-to-conll-rdf-and-back)
* [CoNLL-RDF Pipeline with SPARQL Updates](#ii-integrating-sparql-into-your-pipeline)
* [CoNLL-RDF Pipeline from JSON](#iii-conll-rdf-manager-conll-rdf-pipelines-with-json)

## Before you start
* follow [the instructions on installing conll-rdf](../README.md#installing)
* the command-examples in this tutorial only work if you're in the correct folder. Chapters (I) and (II) assume your `current working-directory` is `examples/`, (III) assumes you're in the folder above. Make sure you move to the respective directory in the terminal (or adjust the commands to match).
* you might get an error like ```bash: ./test.sh: Permission denied``` when trying to run a script. Use this command to change the filemode: `chmod +x <SCRIPT>`
* In some steps we will write the output directly to the terminal, you can use `CTRL+C` to stop the execution.

## I.: Converting CoNLL to CoNLL-RDF (and back!)
In this first section we will use the CoNLL-RDF API to convert an English corpus provided by Universal Dependencies to CoNLL-RDF.
The raw data can be found in [data/](../data/ud/UD_English-master), the full script is [convert-ud.sh](convert-ud.sh) at which you can look at in case you get stuck. We will focus on the `en-ud-dev.conllu` corpus, beginning with the sentence *"From the AP comes this story: President Bush on Tuesday nominated two [...]"* Here is a step by step walk-through:

1. **unzip the corpus from the data directory:**


`gunzip -c ../data/ud/UD_English-master/en-ud-dev.conllu.gz > en-ud-dev.conllu`.
You now should have the unzipped .conllu file in the `example/` working directory. It should look something like this:

```CoNLL
1	From	from	ADP	IN	_	3	case	_	_
2	the	the	DET	DT	Definite=Def|PronType=Art	3	det	_	_
3	AP	AP	PROPN	NNP	Number=Sing	4	nmod	_	_

[...]
```

As you can see, it's standard CoNLL-U as you might be familiar with.

2. **convert the corpus to CoNLL-RDF with the CoNLLStreamExtractor:**

For conversion we will use the **CoNLLStreamExtractor** which can be found in the src folder. It expects a URI of the resource as the first argument, followed by the column names which will end up in the CoNLL-RDF triplets. We will use `https://github.com/UniversalDependencies/UD_English` as the URI. And since we have standard CoNLL-U, the column names are `ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC`, resulting in:

```shell
cat en-ud-dev.conllu | ../run.sh CoNLLStreamExtractor \
https://github.com/UniversalDependencies/UD_English# ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC 
```

Note that to execute the java classes from terminal you can use the `run.sh` script followed by the class name. The arguments given will be passed on.

The command we just ran will spam your terminal with the converted CoNLL-RDF. (CTRL+C to abort execution) As you might notice, it's rather badly formatted and hard to read. Now we can use the **CoNLLRDFormatter**, which will format our plain RDF into human-readable turtle. We can integrate the **CoNLLRDFFormatter** simply by appending it to our pipeline using the `|` symbol. Finally we write the result to a file with `>`:

```shell
cat en-ud-dev.conllu | ../run.sh CoNLLStreamExtractor \
https://github.com/UniversalDependencies/UD_English# ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC | \
../run.sh CoNLLRDFFormatter -rdf > en-ud-dev.ttl
```

You should now find the `en-ud-dev.ttl` file which should look something like this:

```Turtle
@prefix : <https://github.com/UniversalDependencies/UD_English#> .
@prefix powla: <http://purl.org/powla/powla.owl#> .
@prefix terms: <http://purl.org/acoli/open-ie/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#> .
@prefix x: <http://purl.org/acoli/conll-rdf/xml#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> .
:s1_0 a nif:Sentence .
:s1_1 a nif:Word; conll:WORD "From"; conll:EDGE "case"; conll:HEAD :s1_3; conll:ID "1"; conll:LEMMA "from"; conll:POS "IN"; conll:UPOS "ADP"; nif:nextWord :s1_2 .
:s1_2 a nif:Word; conll:WORD "the"; conll:EDGE "det"; conll:FEAT "Definite=Def|PronType=Art"; conll:HEAD :s1_3; conll:ID "2"; conll:LEMMA "the"; conll:POS "DT"; conll:UPOS "DET"; nif:nextWord :s1_3 .
:s1_3 a nif:Word; conll:WORD "AP"; conll:EDGE "nmod"; conll:FEAT "Number=Sing"; conll:HEAD :s1_4; conll:ID "3"; conll:LEMMA "AP"; conll:POS "NNP"; conll:UPOS "PROPN"; nif:nextWord :s1_4 .

[...]
```

And that's it, you generated your first CoNLL-RDF file!

3. **convert the corpus back to CoNLL:**

To keep CoNLL-RDF integrated with all prior technology that depends on CoNLL, we can use the **CoNLLRDFFormatter** to quickly convert CoNLL-RDF back to CoNLL. To do so, use the `-conll` flag, followed by the column names that you want to transfer to CoNLL. Say we are only interested in the word, lemma and feature columns and want to limit the output to these columns:

```shell
cat en-ud-dev.ttl \
	| ../run.sh CoNLLRDFFormatter -conll ID WORD LEMMA FEAT \
	> en-ud-dev-roundtrip.conll
```

Which will result in the (reduced) CoNLL file:

```CoNLL
# global.columns = ID WORD LEMMA FEAT
1	From	from	_	
2	the	the	Definite=Def|PronType=Art	
3	AP	AP	Number=Sing	

[...]
```

## II.: Integrating SPARQL into your pipeline

Now we can make things more interesting, by adding SPARQL updates to our pipeline. I advise you move to a text editor, since editing the command in the shell should get impractical by now. 

In this example we will perform a soundness check for Universal Dependency POS tags. UD POS tags are meant to be universal, but they mix (presumably universal) syntactic criteria with (language-specific) morphological/lexical criteria. We test to what extent syntactic criteria are overridden by other criteria.
We will do this by mapping UD tags to syntactic prototypes, then deriving syntactic prototypes from UD dependencies and compare them afterwards whether they match.

If you get stuck, see [analyze-ud.sh](analyze-ud.sh) for the full solution.

1. **unzip conll and convert to CoNLL-RDF:**

As in the first example we uncompress our corpus file and run it through the **CoNLLStreamExtractor**. Because we don't need all columns for our analysis, we simply name them IGNORE to later clear them.

```shell
gunzip -c ../data/ud/UD_English-master/en-ud-dev.conllu.gz \
	| ../run.sh CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE 
```

Note, that we did not pipe the output through the **CoNLLRDFFormatter**, thus the different formatting:

```Turtle
@prefix :      <https://github.com/UniversalDependencies/UD_English#> .
@prefix powla: <http://purl.org/powla/powla.owl#> .
@prefix terms: <http://purl.org/acoli/open-ie/> .
@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#> .
@prefix x:     <http://purl.org/acoli/conll-rdf/xml#> .
@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
@prefix nif:   <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> .

:s1_2   a             nif:Word ;
        nif:nextWord  :s1_3 ;
        conll:EDGE    "det" ;
        conll:HEAD    :s1_3 ;
        conll:IGNORE  "2" , "DT" , "Definite=Def|PronType=Art" , "the" ;
        conll:UPOS    "DET" ;
        conll:WORD    "the" .

:s1_7   a             nif:Word ;
        conll:EDGE    "punct" ;
        conll:HEAD    :s1_4 ;
        conll:IGNORE  ":" , "7" ;
        conll:UPOS    "PUNCT" ;
        conll:WORD    ":" .

[...]
```

Now that the data is converted to CoNLL-RDF, we can perform SPARQL update and select statements on it, as explained in the next section.
It's not a problem, if you're not familiar with SPARQL. We have already prepared SPARQL scripts in the `sparql/` folder for you to use and construct a pipeline with.

2. **combining SPARQL updates to form a pipeline**

* Note: a previous version of this document explained how to apply SPARQL updates using the **CoNLLStreamExtractor** class. The following describes the new recommended method of using the dedicated **CoNLLRDFUpdater** class.

Now we have the first part of our pipeline ready. We can now start to append the SPARQL updates to it, as I will explain below. I suggest you add the updates one after the other to see the progress and get a feel how things work. You can `CTRL+C` after a few lines have been written to stdout.

* First, we remove everything that originated from a column that we don't need and named ignore before:

```shell
cat en-ud-dev.conllu \
	| ../run.sh CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE \
	| ../run.sh CoNLLRDFUpdater -custom \
	-updates sparql/remove-IGNORE.sparql
```
```Turtle
[...]

:s1_2   a             nif:Word ;
        nif:nextWord  :s1_3 ;
        conll:EDGE    "det" ;
        conll:HEAD    :s1_3 ;
        conll:UPOS    "DET" ;
        conll:WORD    "the" .

:s1_7   a           nif:Word ;
        conll:EDGE  "punct" ;
        conll:HEAD  :s1_4 ;
        conll:UPOS  "PUNCT" ;
        conll:WORD  ":" .

[...]
```

* Second, we derive syntactic prototypes. First from the UPOS column and then from the EDGE column: 

```shell
	sparql/analyze/UPOS-to-POSsynt.sparql \
	sparql/analyze/EDGE-to-POSsynt.sparql
```
```Turtle
[...]

:s1_2   a                   nif:Word ;
        nif:nextWord        :s1_3 ;
        conll:EDGE          "det" ;
        conll:HEAD          :s1_3 ;
        conll:POSsynt_UD    "AN" ;
        conll:POSsynt_UPOS  "AN" ;
        conll:UPOS          "DET" ;
        conll:WORD          "the" .

:s1_7   a                   nif:Word ;
        conll:EDGE          "punct" ;
        conll:HEAD          :s1_4 ;
        conll:POSsynt_UD    "X" ;
        conll:POSsynt_UPOS  "X" ;
        conll:UPOS          "PUNCT" ;
        conll:WORD          ":" .

[...]
```

* We then compare both syntactic prototypes and create another attribute which is 1 if both match and 0 if they don't. Finally, we generate the output in columns with our first SPARQL select statement.  

```shell
	sparql/analyze/consolidate-POSsynt.sparql
```
```Turtle
[...]

:s1_2   a                    nif:Word ;
        nif:nextWord         :s1_3 ;
        conll:EDGE           "det" ;
        conll:HEAD           :s1_3 ;
        conll:POSsynt_UD     "AN" ;
        conll:POSsynt_UPOS   "AN" ;
        conll:POSsynt_match  "1" ;
        conll:UPOS           "DET" ;
        conll:WORD           "the" .

:s1_7   a                    nif:Word ;
        conll:EDGE           "punct" ;
        conll:HEAD           :s1_4 ;
        conll:POSsynt_UD     "X" ;
        conll:POSsynt_UPOS   "X" ;
        conll:POSsynt_match  "1" ;
        conll:UPOS           "PUNCT" ;
        conll:WORD           ":" .

[...]
```
```shell
	| ../run.sh CoNLLRDFFormatter -query sparql/analyze/eval-POSsynt.sparql
```
```Turtle
# global.columns = word upos udep POSsynt_UPOS POSsynt_UDEP match
From	ADP	case	AN	AN	1	
the	DET	det	AN	AN	1	
AP	PROPN	nmod	N	N	1	
comes	VERB	root	V	V	1	
this	DET	det	AN	AN	1	
story	NOUN	nsubj	N	N	1	
:	PUNCT	punct	X	X	1	

[...]
```

3. **Final pipeline**:

```shell
gunzip -c ../data/ud/UD_English-master/en-ud-dev.conllu.gz > en-ud-dev.conllu

cat en-ud-dev.conllu \
    |../run.sh CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
    IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE \
    |../run.sh CoNLLRDFUpdater -custom \
    -updates sparql/remove-IGNORE.sparql \
        sparql/analyze/UPOS-to-POSsynt.sparql \
        sparql/analyze/EDGE-to-POSsynt.sparql \
        sparql/analyze/consolidate-POSsynt.sparql \
    | ../run.sh CoNLLRDFFormatter -query sparql/analyze/eval-POSsynt.sparql \
    | grep -v '#';
```

## III.: CoNLL-RDF Manager: CoNLL-RDF Pipelines with JSON
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

See [the template](../src/template.conf.json) for a full list of options, and the documentation for in-depth explanations of each.
