#!/bin/bash
#
# CoNLL-RDF EXAMPLE: convert a corpus to RDF
# 
# example pipeline for the RDF edition of Universal Dependencies (UD) corpora 
# cf. link-ud.sh for linking such data

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
ROOT=$HOME/..;
DATA=$ROOT/data;

# 1. read
for file in `find $DATA/ud | grep 'conllu.gz$'`; do \
	echo $file; 
	filename=$(basename "$file"); 
	tmp=UD_${file#*UD_}; 
	lang=${tmp%-master/*}; 
	gunzip -c $file | \
# 2. parse UD data to RDF
	$ROOT/run.sh \
		CoNLLStreamExtractor https://github.com/UniversalDependencies/$lang# \
		ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC | \
	\
# 3. format
	$ROOT/run.sh CoNLLRDFFormatter -rdf ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC $* > $DATA/ud/UD_English-master/$filename.ttl \
; done
