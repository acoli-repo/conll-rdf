
# CoNLL-RDF ontology 
CoNLL-RDF ontology, based on CoNLL Shared Tasks and selected other TSV formats

Formalizes CoNLL data structures and CoNLL properties for 20 CoNLL-TSV and related TSV dialects. This includes all CoNLL Shared Task TSV formats until 2018, CoNLL-U, UniMorph and several PropBank skel formats.


## Notes

1) The set of CoNLL-RDF properties is meant to be extensible. Properties are created from either (a) user-provided column labels, or (b) annotation values of *-ARGs columns. 
   The list of properties is thus necessarily incomplete. 

2) For the moment, we maintain the CoNLL-X URI for the CoNLL vocabulary. To be moved to own namespace with the official release of this ontology.

3) CoNLL-RDF builds on NIF and that we thus provide a copy of the file
https://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core/nif-core.ttl
In theory, that one should be provided via 
https://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core
but the original link does currently not resolve.

## History
2020-01-08 added lexical formats (CC)
2019-08-30 - 2019-09-07 initial submit (CC)

## Contributors
CC - Christian Chiarcos, chiarcos@informatik.uni-frankfurt.de
