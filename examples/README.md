# Hands-on CoNLL-RDF Tutorial

## Requirements

* make sure to download the repository from GitHub: `git clone https://github.com/acoli-repo/CoNLL-RDF.git`.
* java is required, use `java --version` to check if you have java installed. 
* all paths up from this point assume you are in `examples/`, so make sure you move to this directory in the terminal.
* In case you are are not able to execute .sh scripts due to missing permission, this helps: `chmod +x <SCRIPT>`
* so in this tutorials case: `chmod +x convert-ud.sh analyze-ud.sh ../run.sh ../compile.sh` 
* Note: In some steps we will write the output directly to the terminal, use CTRL+C to stop the execution.

## I.: Converting CoNLL to CoNLL-RDF (and back!)
In this first section we will use the CoNLL-RDF API to convert an English corpus provided by Universal Dependencies to CoNLL-RDF.
The raw data can be found in [data/](../data/ud/UD_English-master), the full script is [convert-ud.sh](convert-ud.sh) at which you can look at in case you get stuck. We will focus on the `en-ud-dev.conllu` corpus, beginning with the sentence *"From the AP comes this story: President Bush on Tuesday nominated two [...]"* Here is a step by step walk-through:

1. **unzip the corpus from the data directory:**


`gunzip -c ../data/ud/UD_English-master/en-ud-dev.conllu.gz > en-ud-dev.conllu`.
You now should have the unzipped .conllu file in the `example/` working directory. It should look something like this:

```CoNLL
1       From    from    ADP     IN      _       3       case    _       _
2       the     the     DET     DT      Definite=Def|PronType=Art       3       det     _       _
3       AP      AP      PROPN   NNP     Number=Sing     4       nmod    _       _

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
@prefix : <https://github.com/UniversalDependencies/UD_English> .
@prefix terms: <http://purl.org/acoli/open-ie/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> .
:s1_0 a nif:Sentence .
:s1_1 a nif:Word; conll:WORD "From"; conll:ID "1"; conll:LEMMA "from"; conll:UPOS "ADP"; conll:POS "IN"; conll:HEAD :s1_3; conll:EDGE "case"; nif:nextWord :s1_2 .
:s1_2 a nif:Word; conll:WORD "the"; conll:ID "2"; conll:LEMMA "the"; conll:UPOS "DET"; conll:POS "DT"; conll:FEAT "Definite=Def|PronType=Art"; conll:HEAD :s1_3; conll:EDGE "det"; nif:nextWord :s1_3 .
:s1_3 a nif:Word; conll:WORD "AP"; conll:ID "3"; conll:LEMMA "AP"; conll:UPOS "PROPN"; conll:POS "NNP"; conll:FEAT "Number=Sing"; conll:HEAD :s1_4; conll:EDGE "nmod"; nif:nextWord :s1_4 .
```

And that's it, you generated your first CoNLL-RDF file!

3. **convert the corpus back to CoNLL:**

To keep CoNLL-RDF integrated with all prior technology that depends on CoNLL, we can use the **CoNLLRDFFormatter** to quickly convert CoNLL-RDF back to CoNLL. To do so, use the `-conll` flag, followed by the column names that you want to transfer to CoNLL. Say we are only interested in the word, lemma and feature columns and want to limit the output to these columns:

```shell
cat en-ud-dev.ttl | ../run.sh CoNLLRDFFormatter \
-conll ID WORD LEMMA FEAT > en-ud-dev-roundtrip.conll
```

Which will result in the (reduced) CoNLL file:

```CoNLL
# ID	WORD	LEMMA	FEAT	
1	From	from	_	
2	the	the	Definite=Def|PronType=Art	
3	AP	AP	Number=Sing	

[...]
```

## II.: Integrating SPARQL into your pipeline

Now we can make things more interesting, by adding SPARQL updates to our pipeline. I advice you move to a text editor, since putting everything in one shell command should get impractical by now. 

In this example we will perform a soundness check for Universal Dependency POS tags. UD POS tags are meant to be universal, but they mix (presumably universal) syntactic criteria with (language-specific) morphological/lexical criteria. We test to what extent syntactic criteria are overridden by other criteria.
We will do this by mapping UD tags to syntactic prototypes, then deriving syntactic prototypes from UD dependencies and compare them afterwards whether they match.

If you get stuck, see [analyze-ud.sh](analyze-ud.sh) for the full solution.

1. **unzip conll and convert to CoNLL-RDF:**

As in the first example we uncompress our corpus file and run it through the **CoNLLStreamExtractor**. Because we don't need all columns for our analysis, we simply name them IGNORE to later clear them.

```shell
gunzip -c ../data/ud/UD_English-master/en-ud-dev.conllu.gz | \
	../run.sh CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE 
```

Note, that we did not pipe the output through the **CoNLLRDFFormatter**, thus the different formatting:

```Turtle
@prefix :      <https://github.com/UniversalDependencies/UD_English#> .
@prefix terms: <http://purl.org/acoli/open-ie/> .
@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix conll: <http://ufal.mff.cuni.cz/conll2009-st/task-description.html#> .
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

```

We now get to use another functionality of the **CoNLLStreamExtractor**, namely performing SPARQL update and select statements. We do this via the `-u` argument for updates and `-s` for a select statement. It's not a problem, if you're not familiar with SPARQL. We have already prepared SPARQL scripts in the `sparql/` folder for you to use and construct a pipeline with.

2. **combining SPARQL updates to form a pipeline**

Now we have the first part of our pipeline ready. We can now start to append the SPARQL updates to it, as I will explain below. I suggest you add the updates one after the other to see the progress and get a feel how things work. You can `CTRL+C` after a few lines have been written to stdout.


* First, we remove everything that originated from a column that we don't need and named ignore before:

```shell
gunzip -c ../data/ud/UD_English-master/en-ud-dev.conllu.gz | \
	../run.sh CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE \
	-u sparql/remove-IGNORE.sparql
```

* Second, we derive syntactic prototypes. First from the UPOS column and then from the EDGE column: 

```shell
	sparql/analyze/UPOS-to-POSsynt.sparql \
	sparql/analyze/EDGE-to-POSsynt.sparql
```

* We then compare both syntactic prototypes and create another attribute which is 1 if both match and 0 if they don't. Finally, we generate the output in columns with our first SPARQL select statement.  

```shell
	sparql/analyze/consolidate-POS-synt.sparql \
	-s sparql/analyze/eval-POSsynt.sparql
```

3. **Final pipeline**:

```shell
gunzip -c ../data/ud/UD_English-master/en-ud-dev.conllu.gz | \
	../run.sh CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE \
	-u 	sparql/remove-IGNORE.sparql \
		sparql/analyze/UPOS-to-POSsynt.sparql \
		sparql/analyze/EDGE-to-POSsynt.sparql \
		sparql/analyze/consolidate-POSsynt.sparql \
	-s 	sparql/analyze/eval-POSsynt.sparql \
	| grep -v '#';
```
