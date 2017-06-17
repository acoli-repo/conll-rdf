import java.io.*;
import java.util.*;

public class DictBasedTagger {
	public static void main(String[] argv) throws Exception {
		System.err.println("DictBasedTagger dict.txt col\n"+
			"\tdict.txt space-separated list of words (lemmas), tags and (optional) frequency\n"+
			"\t         if frequency is ommitted, assume 1, for multiple word-tag pairs,\n"+
			"\t         use sum\n"+
			"\tcol      column number with lemmas in annotated (CoNLL/TSV) file\n"+
			"read conll format from stdin and write to stdout, TSV format, append dict-based POS");

		String dict = argv[0];
		int col = Integer.parseInt(argv[1]);
			
		System.err.print("read dict ..");
		Hashtable<String,Hashtable<String, Integer>> lem2pos2freq = new Hashtable<String,Hashtable<String, Integer>>();
		BufferedReader in = new BufferedReader(new FileReader(argv[0]));
		int entries = 0;
		for(String line =""; line!=null; line=in.readLine()) {
			String[] fields = line.split("[ \t]+");
			if(fields.length>1) {
				String lem = fields[0];
				String pos = fields[1];
				Integer freq = 1;
				try {
					freq = Integer.parseInt(fields[2]);
				} catch (Exception e) {}
				if(lem2pos2freq.get(lem)==null) lem2pos2freq.put(lem, new Hashtable<String,Integer>());
				if(lem2pos2freq.get(lem).get(pos)!=null) freq += lem2pos2freq.get(lem).get(pos);
				lem2pos2freq.get(lem).put(pos,freq);
				entries++;
				if(entries/1000>(entries-1)/1000) System.err.print(".");
			}
		}
		System.err.println(". ok ["+lem2pos2freq.size()+" lemmas, "+entries+" entries]");
		in.close();
		
		System.err.print("process stdin ..");
		in = new BufferedReader(new InputStreamReader(System.in));
		for(String line = ""; line!=null; line=in.readLine()) {
			String[] fields = line.split("\t");
			if(fields.length > col) {
				System.out.print(line+"\t");
				String lem = fields[col];
				String pos = "<UNK>";
				if(lem2pos2freq.get(lem)==null)
					lem=lem.toLowerCase();
				if(lem2pos2freq.get(lem)!=null) {
					int mfreq = 0;
					for(String cand : lem2pos2freq.get(lem).keySet()) 
						if(lem2pos2freq.get(lem).get(cand) > mfreq) {
							mfreq = lem2pos2freq.get(lem).get(cand);
							pos=cand;
						}
				}
				System.out.println(pos);
			}
			else System.out.println(line);
		}
	}
}