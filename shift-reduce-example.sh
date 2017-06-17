#!/bin/bash
# processing English Universal Dependencies, perform OLiA-based shift-reduce parsing
# this is far from perfect, but was built in a day and analyses the first few sentences of UD_en correctly
# gaps: complex verbs (s6), genitive 's (s7), punctuation, etc.
for file in `find ud | grep 'conllu.gz$'`; do gunzip -c $file; done | \
egrep '^[0-9]+\s.*$|^$' |								# remove multiword tokens
./run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
	ID WORD LEMMA IGNORE POS IGNORE IGNORE IGNORE IGNORE IGNORE \
	-u sparql/remove-ID.sparql{1} \
	   sparql/remove-IGNORE.sparql{1} \
	   \
 	   sparql/link-penn-POS.sparql{1} \
	   sparql/link-stanford-EDGE.sparql{0} \
	   sparql/remove-annotation-model.sparql{1} \
	   \
	   shift-reduce/load-OLiA.sparql \
	   sparql/infer-olia-concepts.sparql \
	   shift-reduce/initialize-SHIFT.sparql \
	   shift-reduce/REDUCE-english-1.sparql{5} \
   	   shift-reduce/REDUCE-english-2.sparql{5} \
	   shift-reduce/REDUCE-english-3.sparql{5} \
	   shift-reduce/REDUCE-english-4.sparql{3} \
	   shift-reduce/REDUCE-to-HEAD.sparql \
| ./run.sh CoNLLRDFFormatter 2>&1 	### use this for formatted output 
