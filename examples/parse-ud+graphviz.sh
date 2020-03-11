#!/bin/bash
#
# CoNLL-RDF EXAMPLE: rule-based, unlexicalized, cross-tagset parsing
# 
# illustrates complex graph rewriting using the English Universal Dependencies
# - strip original dependency annotation (column label IGNORE and remove-IGNORE.sparql)
# - link the Penn Treebank annotations to the Ontologies of Linguistic Annotation (OLiA)
#   (for linking other annotation schemes see http://purl.org/olia)
# - apply graph rewriting rules that emulate a shift-reduce parser
#   takes only sequence and POS concepts as information
#   note the OLiA concepts for POS are independent from the original string presentation
#   so, this is a cross-tagset unlexicalized parser
#
# note1: this requires an internet connection to work: the ontologies are directly loaded from the LLOD cloud
# 
# note2: This is a showcase built in half a day for the first few UD_en sentences. It is *known* 
#       to be far from perfect, but the point of this script is not coverage, but ease of development ;)
# known issues: appositions (s3), complex verbs (s6), genitive 's (s7), punctuation, etc.
# inherent limitations: this is a deterministic parser
#
# homework: improve coverage

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
ROOT=$HOME/..;
DATA=$ROOT/data;
SPARQL=$HOME/sparql;

if [ ! -e $DATA/ud/graphsout ]; then
	mkdir $DATA/ud/graphsout;
fi;

gunzip -c $DATA/ud/UD_English-master/en-ud-dev.conllu.gz | \
egrep -m 58 '^' | 										# until line 50, we're ok, increase this limit to analyze more data
egrep '^[0-9]+\s.*$|^$' |								# remove multiword tokens
$ROOT/run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	ID WORD LEMMA IGNORE POS IGNORE IGNORE IGNORE IGNORE IGNORE \
| \
$ROOT/run.sh CoNLLRDFUpdater -custom \
	-model http://purl.org/olia/penn.owl http://purl.org/olia/penn.owl \
	-model http://purl.org/olia/penn-link.rdf http://purl.org/olia/penn.owl \
	-model http://purl.org/olia/olia.owl http://purl.org/olia/olia.owl \
	-graphsout $DATA/ud/graphsout s2_0 s3_0 \
	-updates \
	   $SPARQL/remove-ID.sparql \
	   $SPARQL/remove-IGNORE.sparql \
	   \
 	   $SPARQL/link/link-penn-POS.sparql \
	   $SPARQL/link/remove-annotation-model.sparql \
	   $SPARQL/link/infer-olia-concepts.sparql \
	   \
	   $SPARQL/parse/initialize-SHIFT.sparql \
	   $SPARQL/parse/REDUCE-english-1.sparql{5} \
   	   $SPARQL/parse/REDUCE-english-2.sparql{5} \
	   $SPARQL/parse/REDUCE-english-3.sparql{5} \
	   $SPARQL/parse/REDUCE-english-4.sparql{3} \
	   $SPARQL/parse/REDUCE-to-HEAD.sparql \
| \
$ROOT/run.sh CoNLLRDFFormatter -grammar 2>&1 	### use -grammar for formatted output (this is made default here)

for file in `find $DATA/ud/graphsout/ | grep '.dot$'`; do \
dot -Tpng $file -o $file.png & sleep 5;
# paralellized because it sometimes fails with a segfault
done
