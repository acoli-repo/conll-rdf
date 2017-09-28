SPARQL-based (kinda) Shift-Reduce Parsing

- for every nif:nextWord create a SHIFT property
- hand-crafted rules, bottom-up, greedy, deterministic: delete SHIFT, insert REDUCE
- replace REDUCE by conll:HEAD
