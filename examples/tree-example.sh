#!/bin/bash
#
# CoNLL-RDF+POWLA EXAMPLE: 
# - read CoNLL file with several phrase structure parses
# - represent parses as powla:Nodes
# - write the first of the parses to stdout

# Treatment of empty nodes (that do not contain a nif:Word) left as an excercise.
# See xml-example.sh for their treatment in XML parsing

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
ROOT=$HOME/..;
DATA=$ROOT/data;
SPARQL=$HOME/sparql;

echo '# using default URIs (all nodes remain distinct)';
#
# 1) read sample file with two alternative parses (WSJ 0655, first sentence, annotations from OntoNotes and PennTreebank) 
cat $DATA/bracketing.sample.conll | \
#
# 2) heuristically detect bracketing columns and produce powla:Nodes for them, otherwise, produce CoNLL-RDF
$ROOT/run.sh CoNLLBrackets2RDF http://replace.me/ WORD POS_ON PARSE_ON POS_PTB PARSE_PTB |\
#
# 3) re-build bracket notation for all powla:Nodes. Note that we do not check whether these form a tree. 
#    If they don't, we still get a result, but it might look strange.
$ROOT/run.sh CoNLLRDFUpdater -custom -updates $SPARQL/trees/tree2bracket.sparql | \
#
# 4) export selected CoNLL columns: here, one of the parses
# alternatively: use -conll WORD PARSE_PTB to get PTB parse
$ROOT/run.sh CoNLLRDFFormatter -conll WORD PARSE_ON

echo;
echo '# using span URIs (co-extensional nodes are lumped together)';
#
# 1) read sample file with two alternative parses (WSJ 0655, first sentence, annotations from OntoNotes and PennTreebank) 
cat $DATA/bracketing.sample.conll | \
#
# 2) heuristically detect bracketing columns and produce powla:Nodes for them, otherwise, produce CoNLL-RDF
$ROOT/run.sh CoNLLBracketsWithSpanURIs2RDF http://replace.me/ WORD POS_ON PARSE_ON POS_PTB PARSE_PTB |\
#
# 3) re-build bracket notation for all powla:Nodes. Note that we do not check whether these form a tree. 
#    If they don't, we still get a result, but it might look strange.
$ROOT/run.sh CoNLLRDFUpdater -custom -updates $SPARQL/trees/tree2bracket.sparql | \
#
# 4) export selected CoNLL columns: here, one of the parses
# alternatively: use -conll WORD PARSE_PTB to get PTB parse
$ROOT/run.sh CoNLLRDFFormatter -conll WORD PARSE_ON

