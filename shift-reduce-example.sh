#!/bin/bash
# processing English Universal Dependencies, perform OLiA-based shift-reduce parsing
# this is far from perfect, but was built in a day and analyses the first few sentences of UD_en correctly
# gaps: complex verbs (s6), genitive 's (s7), punctuation, etc.
for file in `find ud | grep 'test.conllu.gz$'`; do gunzip -c $file; done | \
egrep '^[0-9]+\s.*$|^$' |								# remove multiword tokens
./run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	ID WORD LEMMA IGNORE POS IGNORE IGNORE IGNORE IGNORE IGNORE \
| ./run.sh CoNLLRDFUpdater -custom \
	-model http://purl.org/olia/penn.owl http://purl.org/olia/penn.owl \
	-model http://purl.org/olia/penn-link.rdf http://purl.org/olia/penn.owl \
	-model http://purl.org/olia/stanford.owl http://purl.org/olia/stanford.owl \
	-model http://purl.org/olia/stanford-link.rdf http://purl.org/olia/stanford.owl \
	-model http://purl.org/olia/olia.owl http://purl.org/olia/olia.owl \
	-updates sparql/remove-ID.sparql{1} \
	   sparql/remove-IGNORE.sparql{1} \
	   \
 	   sparql/link-penn-POS.sparql{1} \
	   sparql/link-stanford-EDGE.sparql{0} \
	   sparql/remove-annotation-model.sparql{1} \
	   \
	   sparql/infer-olia-concepts.sparql \
	   shift-reduce/initialize-SHIFT.sparql \
	   shift-reduce/REDUCE-english-1.sparql{5} \
   	   shift-reduce/REDUCE-english-2.sparql{5} \
	   shift-reduce/REDUCE-english-3.sparql{5} \
	   shift-reduce/REDUCE-english-4.sparql{3} \
	   shift-reduce/REDUCE-to-HEAD.sparql \
| ./run.sh CoNLLRDFFormatter 2>&1 	### use this for formatted output 
