#!/bin/bash
#
# CoNLL+XML EXAMPLE: 
# (for SketchEngine/CWB extensions of one-word-per-line ["CoNLL"] formats)
# - read CoNLL file enclosed/interrupted by XML/SGML markup
# - expect markup elements to stand in *one single line*
# - represent CoNLL with CoNLL-RDF and trees with powla:Nodes
# - serialize CoNLL+Trees in the PTB bracket notation
#
# Note that XML markup is usually applied document-wide, and where nodes open and close *before* the current sentence, they
# are included in the sentence parse, but without their dependent sentences. In the bracket export, this means that every
# sentence includes the document markup information. In the RDF representation, this is *not* redundant, as all dependent
# sentences refer to the same URI. This identity information is available in RDF, but necessarily lost in the export to 
# CoNLL bracket notation.
# It can be preserved if the unit of processing is not the sentence, but the text (see CoNLL2RDF.java for that).

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
ROOT=$HOME/..;
DATA=$ROOT/data;
SPARQL=$HOME/sparql;

# 1) read SketchEngine sample file
cat $DATA/SketchEngine.sample | \
#
# 2) heuristically detect bracketing columns and produce powla:Nodes for them, otherwise, produce CoNLL-RDF
#    note that we perform no XML validation, but that we provide techniques for silent recovery (e.g., 
#    where opening and ending markup elements don't match or an opened element isn't closed)
# XMLTSV2RDF creates powla:Nodes types as conll:XML_DATA, tree2bracket.sparql turns this into a property conll:XML_DATA
$ROOT/run.sh XMLTSV2RDF http://replace.me/ WORD POS |\
#
# 3) build PTB bracket notation for all powla:Nodes. 
$ROOT/run.sh CoNLLRDFUpdater -custom \
	-updates $SPARQL/trees/xAttributes2value.sparql \
			 $SPARQL/trees/emptyNode2Word.sparql \
			 $SPARQL/trees/tree2bracket.sparql | \
#
# 4) export selected CoNLL columns
$ROOT/run.sh CoNLLRDFFormatter -conll WORD POS XML_DATA