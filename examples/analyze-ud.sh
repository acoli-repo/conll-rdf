#!/bin/bash
#
# CoNLL-RDF EXAMPLE: corpus analysis
# 
# reads UD data from stdin or args, analyses POS tags
#
# usecase: (extract data to perform) a soundness check for Universal Dependency POS tags
# UD POS tags are meant to be universal, but they mix (presumably universal) syntactic criteria with (language-specific) morphological/lexical criteria
# we test to what extent syntactic criteria are overridden by other criteria
# approach: map UD tags to syntactic prototypes, derive syntactic prototypes from UD deps, compare

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
ROOT=$HOME/..;
DATA=$ROOT/data;
SPARQL=$HOME/sparql;

for file in `find $DATA/ud | grep 'dev.conllu.gz$'`; do gunzip -c $file; done | \
egrep '^[0-9]+\s.*$|^$' |								# remove multiword tokens
$ROOT/run.sh CoNLLStreamExtractor \
    https://github.com/UniversalDependencies/UD_English# \
	IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE \
    |../run.sh CoNLLRDFUpdater -custom \
    -updates sparql/remove-IGNORE.sparql \
        sparql/analyze/UPOS-to-POSsynt.sparql \
        sparql/analyze/EDGE-to-POSsynt.sparql \
        sparql/analyze/consolidate-POSsynt.sparql \
    | ../run.sh CoNLLRDFFormatter -query sparql/analyze/eval-POSsynt.sparql \
	| grep -v '#';
