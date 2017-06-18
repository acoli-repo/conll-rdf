#!/bin/bash
# example pipeline for processing German Universal Dependencies

# 1. read
for file in `find ud | grep 'conllu.gz$'`; do echo $file; filename=$(basename "$file"); tmp=UD_${file#*UD_}; lang=${tmp%-master/*}; gunzip -c $file | \
# 2. parse UD data to RDF
./run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/$lang# \
	ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC | \
./run.sh CoNLLRDFUpdater -custom \
	-model http://purl.org/olia/owl/experimental/univ_dep/all_from_rdfa/ud-pos-all.owl http://purl.org/olia/ud-pos-all.owl \
	-model http://purl.org/olia/owl/experimental/univ_dep/all_from_rdfa/ud-pos-all-link.rdf http://purl.org/olia/ud-pos-all.owl \
	-updates olia-linking/link-UPOS-simple.sparql{1} \
	   \
| \
# 3. format
./run.sh CoNLLRDFFormatter -rdf ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC $* > ud/ttl/$filename.linked.ttl \
; done