#!/bin/bash
# example pipeline for processing German Universal Dependencies

# 1. read
for file in `find ud | grep 'conllu.gz$'`; do echo $file; filename=$(basename "$file"); tmp=UD_${file#*UD_}; lang=${tmp%-master/*}; gunzip -c $file | \
# 2. parse UD data to RDF
./run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/$lang# \
	ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC | \
\
# 3. format
./run.sh CoNLLRDFFormatter -rdf ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC $* > ud/ttl/$filename.ttl \
; done