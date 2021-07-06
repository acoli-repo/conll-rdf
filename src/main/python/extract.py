# requires python3, bash, java, Unix-style file system
import os,sys,re,traceback,argparse,subprocess,io,time
from io import StringIO,TextIOWrapper

# Python wrapper for CoNLLStreamExtractor
# requires Unix environment
# supports stream processing
# not thoroughly tested, yet
# minor reformatting of the output: skip line breaks after lines ending in ; and normalize whitespaces, however, note that we don't do any reordering (such as CoNLLRDFFormatter)

def _flatten(mylist):
	""" this is for postprocessing the result of argparse append lists (emulating the expand action) """
	if(mylist==None):
		return mylist
	elif(type(mylist)==list):
		if len(mylist)==0:
			return []
		elif len(mylist)==1:
			return _flatten(mylist[0])
		else:
			return _flatten(mylist[0]) + _flatten(mylist[1:])
	else:
		return [mylist]

class Extractor:

	extractor_call=None # for restarting (currently not used)
	extractor=None		# the actual process
	output=None
	return_value=False
	reduce_prefixes=False
	prefixes=[]
	
	def __init__(self, path, incols, return_value=True, output=None, baseURI="#", sparql=[], reduce_prefixes=True):
		""" path: path to run.sh
			incols: columns of input
			output: output stream or None, if not None, process() will flush all results immediately to the stream
			baseURI: baseURI, defaults to "#"
			sparql: sparql updates (optional) 
			return_value: if True, process() will aggregate and return a result string, disable this for processing longer streams
			reduce_prefixes: if True, minimize the spellout of prefixes when writing into a stream
			"""
		self.output=output
		self.return_value=return_value
		self.reduce_prefixes=reduce_prefixes
		self.extractor_call=str(\
			"stdbuf -oL -eL "+str(os.path.join(path, "run.sh"))+" "+\
			"CoNLLStreamExtractor "+\
			baseURI+" "+\
			" ".join(incols))
			
		if sparql!=None and len(sparql)>0:
			self.extractor_call=self.extractor_call+" "+\
				"-u "+\
					" ".join(sparql)

		self.extractor=subprocess.Popen(self.extractor_call.split(),
				 stdin=subprocess.PIPE,
                 stdout=subprocess.PIPE, #sys.stdout, # 
                 stderr=sys.stderr, #subprocess.PIPE,
                 universal_newlines=True,
                 bufsize=0)

	def _write(self, txt, output=None):
		if output==None:
			output=self.output
		if(output!=None):
			if len(txt.strip())==0:
				output.write(txt)
			else:
				txt=str(txt).rstrip()
				for line in txt.split("\n"):
					if self.reduce_prefixes and (line.lower().startswith("@prefix") or line.upper().startswith("PREFIX")):
						if not line.strip() in self.prefixes:
							self.prefixes.append(line.strip())
							output.write(line.rstrip()+"\n")
					else:
						if len(line.rstrip())>0 and line.rstrip()[-1] in [",",";"]:
							line=line.rstrip()
						output.write(line)
						
			output.flush()
	
	def _return(self, result):
		if self.return_value:
			result_string=StringIO()
			self._write(result,output=result_string)
			return result_string.getvalue()
		else:
			return None

	def _process(self, buffer):
		""" buffer is a string that represents exactly one sentence """
		# send to process
		self.extractor.stdin.write(buffer+"\n")
		self.extractor.stdin.write("#_END_\n\n")
		self.extractor.stdin.flush()
		
		# read from process
		result=[]
		line=""
		while True:
			line = self.extractor.stdout.readline()
			if line.startswith("#_END_"):
				break
			if line == '' and self.extractor.poll() is not None:
				break
			if len(line.rstrip())>0 and line.rstrip()[-1] in [";",","]:
				line=line.rstrip()
			result.append(line)
			self._write(line)
				
		result="".join(result)+"\n"
		self._write("\n")
		return self._return(result)
			
	def process(self, input):
		""" input is list of files, file, stream (TextIOWrapper/StringIO), list of strings or string
			input is considered to be in a CoNLL/TSV format consistent with incols as given to init()
			
			Note: if input is a string, we expect this to be at least one complete sentence
			so, feeding every line individually will automatically introduce sentence boundaries between them
		"""
		output=self.output
		
		result=""
		if type(input)==list:								# list of files (or sentences)
			for x in input:
				result=result+str(self.process(x))+"\n"
				self._write("\n")
			return result
		elif type(input)==str and os.path.exists(input):	# CoNLL file
			with open(input,"r") as input:
				return self.process(input)
		elif type(input)==str:								# CoNLL string (at least one sentence)
			return self.process(StringIO.StringIO(input))
		elif type(input) in [StringIO,TextIOWrapper]:		# input stream, break at sentence boundaries
			buffer=""
			for line in input:
				buffer=buffer+line
				line=line.strip()
				if line=="":
					if len(buffer.strip())>0:
						result=result+str(self._process(buffer))+"\n"
						self._write("\n")
						buffer=""
		
			if len(buffer.strip())>0:
				result=result+str(self._process(buffer))+"\n"
				self._write("\n")

		
		return _return(result)

if __name__ == "__main__":
	########
	# INIT #
	########
	# args
	args=argparse.ArgumentParser("wrapper for CoNLLStreamExtractor")

	args.add_argument("files", metavar="N", nargs="*", help="input files, if missing, read from stdin")
	args.add_argument("-p", "--path", type=str, default=os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),"../../../")), help="CoNLL-RDF installation directory (where run.sh is), defaults to %(default)s")
	args.add_argument("-uri","--baseURI",type=str,default="#",help="base URI, if missing, defaults to '#'")
	args.add_argument("-u","--updates",nargs="*", action="append", help="sparql updates, note that we expect these to be files, not plain text")
	args.add_argument("-cols", "--columns", nargs="+", action="append", help="CoNLL column labels")
	
	args = args.parse_args()
	
	# emulate the expand action using the append keyword
	args.updates=_flatten(args.updates)
	args.columns=_flatten(args.columns)
	
	if not os.path.exists(args.path):
		sys.stderr.write("error: CoNLL-RDF home directory "+str(args.path)+" not found, please provide it via -p/--path flag\n")
		sys.stderr.flush()
		sys.exit(1)

	if not os.path.exists(os.path.join(args.path,"run.sh")):
		sys.stderr.write("error: did not find run.sh in CoNLL-RDF home directory "+str(args.path)+" not found, please check or correct the path using the -p/--path flag\n")
		sys.stderr.flush()
		sys.exit(2)
	
	if args.updates==None:
		args.updates=[]
	
	if args.columns==None or len(args.columns)==0:
		sys.stderr.write("error: please provide column labels using the -cols/--columns flag\n")
		sys.stderr.flush()
		sys.exit(3)
		
	if args.files==None or len(args.files)==0:
		sys.stderr.write("reading from stdin, terminate with <CTRL>+C or EOF\n")
		sys.stderr.flush()
		args.files=[sys.stdin]
	
	extractor=Extractor(args.path,args.columns, return_value=False, output=sys.stdout, baseURI=args.baseURI,sparql=args.updates)

	extractor.process(args.files)
	sys.stderr.write("done\n")
	sys.stderr.flush()