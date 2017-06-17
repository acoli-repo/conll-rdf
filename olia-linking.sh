#!/bin/bash
# example pipeline for processing German Universal Dependencies

# 1. read
for file in `find ud/1.4 | grep 'test.conllu$'`; do echo $file; tmp=UD_${file#*UD_}; lang=${tmp%/*}; cat $file | \
# 2. parse UD data to RDF
./run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/$lang# \
	ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC | \
\
# 3. format
./run.sh CoNLLRDFFormatter -rdf ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC $* > ud/ttl/$lang.ttl \
; done