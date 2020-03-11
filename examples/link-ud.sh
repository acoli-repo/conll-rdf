#!/bin/bash
#
# CoNLL-RDF EXAMPLE: (convert and) link a corpus
# 
# example pipeline for the LLOD edition of Universal Dependencies (UD) corpora and 
# linking their POS tags only with the Ontologies of Linguistic Annotation
# 
# homework : link edge labels yourself ;)

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
ROOT=$HOME/..;
DATA=$ROOT/data;
SPARQL=$HOME/sparql;

# 1. read
for file in `find $DATA/ud | grep 'conllu.gz$'`; do \
	filename=$(basename "$file"); 
	tmp=UD_${file#*UD_}; 
	lang=${tmp%-master/*}; 
	TGT=$DATA/ud/ttl/$filename.linked.ttl;
	echo -n convert $file to $TGT 1>&2; 

	gunzip -c $file | \
# 2. parse UD data to RDF
	$ROOT/run.sh \
		CoNLLStreamExtractor \
		https://github.com/UniversalDependencies/$lang# \
		ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC | \
	$ROOT/run.sh CoNLLRDFUpdater \
	    -custom \
		-model http://purl.org/olia/owl/experimental/univ_dep/all_from_rdfa/ud-pos-all.owl http://purl.org/olia/ud-pos-all.owl \
		-model http://purl.org/olia/owl/experimental/univ_dep/all_from_rdfa/ud-pos-all-link.rdf http://purl.org/olia/ud-pos-all.owl \
		-updates $SPARQL/link/link-UPOS-simple.sparql \
		   \
	| \
# 3. format
	$ROOT/run.sh CoNLLRDFFormatter -rdf ID WORD LEMMA UPOS POS FEAT HEAD EDGE DEPS MISC $* > $TGT;
done