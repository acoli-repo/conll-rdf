# Experiment for testing XML bracketing extension.
# Requires a part of the TenTen Corpus. We first extract all bracketing annotation from the
# TenTen files and then convert it to CoNLL-RDF using the presented XMLTSV2RDF class.
# Note that the TenTen Corpus allows for closing xml tags without opening. Using the
# --repair argument we insert artificial opening nodes where needed.

TENTEN=$1;
CONLL="$TENTEN".conll
TTL="$TENTEN".ttl

if [[ $TENTEN == "" ]]; then
  echo "Provide the path to the TenTen file, please."
  else
if [ ! -e "$TTL" ]; then
  cat $TENTEN | \
#    head -n 2500 | \
    ./run.sh TenTen2XMLTSV -r | tee "$CONLL" | \
    ./run.sh XMLTSV2RDF http://replace.me TOK LEM POS_COARSE POS MORPH HEAD_A HEAD_B LEM_ALT LEM_ALT2 > "$TTL"
fi;

## Count the number of XML nodes in the intermediate file.
echo "Before conversion: $(awk -F'<' 'NF==2' "$CONLL" |\
  grep -c "/>\|<[^/]")"

## Count the number of XML:data in the resulting file
echo "After conversion: $(arq --data="$TTL" \
    --query=sparql/count_xml_triples.sparql \
    --results=TSV | grep -v '^?')"
fi;