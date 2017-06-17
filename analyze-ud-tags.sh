#!/bin/bash
# reads UD data from stdin or args, analyses POS tags
#
# UD POS tags are meant to be universal, but they mix (presumably universal) syntactic criteria with (language-specific) morphological/lexical criteria
# we test to what extent syntactic criteria are overridden by other criteria
# approach: map UD tags to syntactic prototypes, derive syntactic prototypes from UD deps, compare

FILES=$*;
HOME=`echo $0 | sed s/'\/[^\/]*$'/'\/'/`./;

if echo $FILES | grep '^$'; then
	FILES=`find $HOME | egrep 'conllu(.gz)?$'`;
fi;

if echo $FILES | grep '^$'; then
	echo 'read from stdin' 1>&2;
	cat;							# no conll files found, read from stdin
else 
	for file in $FILES; do
		if echo $file | grep 'gz$' >&/dev/null; then
			gunzip -c $file; 
		else 	
			cat $file; 
		fi;
	done;
fi	| \
egrep '^[0-9]+\s.*$|^$' |								# remove multiword tokens
(cd ..; 
./run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
		IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE \
		-u sparql/remove-IGNORE.sparql \
		   ud/UPOS-to-POSsynt.sparql \
		   ud/EDGE-to-POSsynt.sparql \
		   ud/consolidate-POSsynt.sparql \
		-s ud/eval-POSsynt.sparql \
	| grep -v '#'
		# | \
# # cat; echo |\
# ./run.sh \
	# CoNLLRDFFormatter # -semantics;
)