#!/bin/bash
# reads UD data from stdin or args, analyses POS tags
#
# UD POS tags are meant to be universal, but they mix (presumably universal) syntactic criteria with (language-specific) morphological/lexical criteria
# we test to what extent syntactic criteria are overridden by other criteria
# approach: map UD tags to syntactic prototypes, derive syntactic prototypes from UD deps, compare

for file in `find ud | grep 'dev.conllu.gz$'`; do gunzip -c $file; done | \
egrep '^[0-9]+\s.*$|^$' |								# remove multiword tokens
./run.sh \
	CoNLLStreamExtractor https://github.com/UniversalDependencies/UD_English# \
		IGNORE WORD IGNORE UPOS IGNORE IGNORE HEAD EDGE IGNORE IGNORE \
		-u sparql/remove-IGNORE.sparql \
		   analyze-ud-tags/UPOS-to-POSsynt.sparql \
		   analyze-ud-tags/EDGE-to-POSsynt.sparql \
		   analyze-ud-tags/consolidate-POSsynt.sparql \
		-s analyze-ud-tags/eval-POSsynt.sparql \
	| grep -v '#'
		# | \
# # cat; echo |\
# ./run.sh \
	# CoNLLRDFFormatter # -semantics;
)