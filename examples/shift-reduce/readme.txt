SPARQL-based Shift-Reduce Parsing

- für jedes nif:nextWord erzeuge ein parse:shift
- handgeschriebene regeln, bottom-up, lösche shift, erzeuge conll:HEAD mit edge REDUCE
- greedy