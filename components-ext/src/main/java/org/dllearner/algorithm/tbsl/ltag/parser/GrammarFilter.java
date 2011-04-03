package org.dllearner.algorithm.tbsl.ltag.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.dllearner.algorithm.tbsl.ltag.data.Category;
import org.dllearner.algorithm.tbsl.ltag.data.LTAG_Tree_Constructor;
import org.dllearner.algorithm.tbsl.ltag.data.TreeNode;
import org.dllearner.algorithm.tbsl.ltag.reader.ParseException;
import org.dllearner.algorithm.tbsl.sem.util.Pair;
import org.dllearner.algorithm.tbsl.templator.SlotBuilder;

/**
 * GrammarFilter implements the grammar filtering of an LTAG parser. The input
 * string is iteratively partitioned into shrinking n-grams and compared to the
 * existing anchors of the trees in the grammar. For example, the input string
 * "a b c" is compared to the grammar with the following n-grams: "a b c",
 * "a b", "a", "b c", "b", "c"; If no trees are found for the exact n-gram, the
 * grammar filter supports the .+ wildcard. If an anchor of a tree in the
 * grammar contains the .+ wildcard the input n-gram "a b x y c" matches the
 * anchor "a b .+ c".
 */
class GrammarFilter {

	final static String[] NAMED_Strings = {"named", "called"};
	final static String NAME_PREDICATE = "SLOT.pred:title_name";
	
	static ParseGrammar filter(String taggeduserinput,LTAGLexicon grammar,List<Integer> temps) {
	
		SlotBuilder slotbuilder = new SlotBuilder();
		
		List<String> input = getWordList(taggeduserinput);
		input.add(0,"#");  // This is important. Don't mess with the parser!
		
		ParseGrammar parseG = new ParseGrammar(input.size());
		LTAG_Tree_Constructor c = new LTAG_Tree_Constructor();

		short localID = 0;
		int start = 1; // Because # does not have a tree.
		int end = input.size();
		
		List<String> unknownTokens = new ArrayList<String>();
		List<String> coveredTokens= new ArrayList<String>(); 
		
		while (start <= input.size()) {

			end = input.size();

			boolean foundCandidates = false;
			
			while (end > start) {

				String token = join(input.subList(start,end)," ").trim().toLowerCase();

				// check for trees in the grammar with token as anchor
				List<Pair<Integer,TreeNode>> candidates = grammar.getAnchorToTrees().get(token); 
				
				/* 
				 * check for a token that matches a pattern like "named <STRING>+";
				 * if so, the function checkForNamedString returns the appropriate tree.
				 * The tree and the corresponding semantics are added below.
				 */
				List<Pair<String,String>> named = checkForNamedString(token);

				if (candidates != null) {
					foundCandidates = true;
					coveredTokens.add(token);
					for (Pair<Integer,TreeNode> p : candidates) {
						add(parseG, p.getSecond(), p.getFirst(), localID);
						localID++;
					}

				} else if (named != null) {
					
					for (Pair<String,String> p : named) {
						try {
							TreeNode tree = c.construct(p.getFirst());
							
							int gid = grammar.addTree(grammar.size(), new Pair<String,TreeNode>(token,tree), Collections.singletonList(p.getSecond()));
							add(parseG, tree, gid-1, localID);
							temps.add(gid-1);
							localID++;
							
							foundCandidates = true;
							coveredTokens.add(token);
						} catch (ParseException e) {
							e.printStackTrace();
						}
					}
					
				
				} else if (token.matches("[0-9]+[,\\.]?[0-9]*")) {
					/*
					 * The following if clause adds support for numbers. If the
					 * input token matches the regular expression "\d", a new
					 * auxiliary tree is added to the parseGrammar.
					 */

					try {

						TreeNode tree = c.construct("(DP NUM:'" + token + "' NP[noun])");

						int gid = grammar.addTree(grammar.size(), new Pair<String,TreeNode>(token,tree), 
								Collections.singletonList("<x,l1,<<e,t>,t>,[l1:[ x | count(x,c), equal(c," + token + ")]],[(l2,x,noun,<e,t>)],[l2=l1],[]>"));
						add(parseG, tree, gid-1, localID);
						localID++;
						
						foundCandidates = true;
						coveredTokens.add(token);

					} catch (ParseException e) {
						e.printStackTrace();
					}
					
				} else {
					/*
					 * Check if the grammar contains an anchor with a wildcard
					 */
					String[] tokenParts = token.split(" ");
					if (tokenParts.length > 2) {
						
						for (String anchor : grammar.getWildCardAnchors()) {
							
							if (token.matches(anchor)) {
								
								foundCandidates = true;
								coveredTokens.add(token);
								
								for (Pair<Integer, TreeNode> p : grammar.getAnchorToTrees().get(anchor)) {
									add(parseG, p.getSecond(), p.getFirst(),localID);
									localID++;
								}
							}
						}
					} 			
					else if (!foundCandidates) {						
						unknownTokens.add(token);
					}
				}
				end--;
			}			
			foundCandidates = false;
			start++;
		}
		
		System.out.println("\ncovered tokens: " + coveredTokens);

		/* construct slots for all unknown tokens */
		
		List<String> coveredWords = new ArrayList<String>();
		for (String	ct : coveredTokens) {
			for (String ctPart : ct.split(" ")) {
				coveredWords.add(ctPart.trim());
			}
		}
		
		List<String> unknownWords = new ArrayList<String>();	
		for (String t : unknownTokens) {	
			String[] tParts = t.split(" ");
			for (String s : tParts) {
				if (!coveredWords.contains(s) && !unknownWords.contains(s)) {
					unknownWords.add(s);
				}
			}
		}
		System.out.println("unknown words:  " + unknownWords);
		
		List<Pair<String,String>> buildSlotFor = preprocess(taggeduserinput,unknownWords);	
		System.out.println("build slot for: " + buildSlotFor + "\n");
			
		List<String[]> entries = slotbuilder.build(taggeduserinput,buildSlotFor);
				
		try {	
				for (String[] entry : entries) {
					String anchor = entry[0];
					String treestring = entry[1];
					String dude = entry[2];
							
					TreeNode tree = c.construct(treestring);
					int gid = grammar.addTree(grammar.size(), new Pair<String,TreeNode>(anchor,tree), 
							Collections.singletonList(dude));
					add(parseG, tree, gid-1, localID); 
					temps.add(gid-1);
					localID++;
				}
		} catch (ParseException e) {
			e.printStackTrace();
		} 
		
		return parseG;
	}

	private static List<Pair<String,String>> checkForNamedString(String token) {

		String[] split = token.split(" ");
		
		if (split.length > 1 && split.length < 5) {
			
			for (String w : NAMED_Strings) {
				
				if (split[0].trim().equals(w)) {
					
					List<Pair<String,String>> out = new ArrayList<Pair<String,String>>(2);
					String rawNames = "";
					String semName = "";
					for (int i=1;i<split.length;i++) {
						semName += "_" + split[i];
						rawNames += "DP:'" + split[i] + "' ";
					}
					semName = semName.substring(1);
					out.add(new Pair<String,String>("(NP NP* ADJ:'"+ w +"' " + rawNames + ")", "<x,l1,<e,t>,[ l1:[ | " + NAME_PREDICATE + "(x,'" + semName + "') ] ], [],[],[]>"));
					out.add(new Pair<String,String>("(DP DP* ADJ:'"+ w +"' " + rawNames + ")", "<x,l1,<<e,t>,t>,[ l1:[ | " + NAME_PREDICATE + "(x,'" + semName + "') ] ], [],[],[]>"));
					
					return out;
					
				}
				
			}
			
		}
		
		return null;
	}

	private static void add(ParseGrammar parseG, TreeNode t, int globalID,
			short localID) {

		t = t.clone();

		// add to parseGrammar
		parseG.add(new Pair<TreeNode, Short>(t, localID));

		if (t.isAuxTree()) {
			// add to list of auxiliary trees
			parseG.getAuxTrees().add(new Pair<TreeNode, Short>(t, localID));

		} else if ((t.getCategory() == Category.S)) {
			// add to list of init trees
			parseG.getInitTrees().add(new Pair<TreeNode, Short>(t, localID));
		}

		parseG.getIndex().put(localID, t);

		parseG.getLocalIdsToGlobalIds().put(localID, globalID);

	}

	public static String join(List<String> list, String delimiter) {
		if (list.isEmpty())
			return "";
		Iterator<String> iter = list.iterator();
		StringBuffer buffer = new StringBuffer(iter.next());
		while (iter.hasNext())
			buffer.append(delimiter).append(iter.next());
		return buffer.toString();
	}
	
	private static List<String> getWordList(String string) {
		
		List<String> result = new ArrayList<String>();
		
		for (String s : string.split(" ")) {
			result.add(s.substring(0,s.indexOf("/")));
		}
		
		return result;
	}
	
	private static List<Pair<String,String>> preprocess(String taggedstring,List<String> unknownwords) {
				
		List<Pair<String,String>> result = new ArrayList<Pair<String,String>>();
		
		if (unknownwords.isEmpty()) {
			return result;
		}
		
		/* condense newtaggedstring: x/RBR adj/JJ > adj/JJR, x/RBS adj/JJ > adj/JJS */ 
		String condensedstring = taggedstring;
		
		String compAdjPattern = "[a-zA-Z_0-9]+/RBR.[a-zA-Z_0-9]+/JJ";
		String superAdjPattern = "[a-zA-Z_0-9]+/RBS.[a-zA-Z_0-9]+/JJ";
		String howAdjPattern = "[a-zA-Z_0-9]+/WRB.[a-zA-Z_0-9]+/JJ"; 
		
		if (condensedstring.matches(".*" + compAdjPattern + ".*")) {
			int begin = condensedstring.indexOf("RBR") + 4;
			int end = begin + condensedstring.substring(condensedstring.indexOf("RBR")).indexOf("/JJ") - 4;
			String adj = condensedstring.substring(begin,end);
			condensedstring = condensedstring.replaceFirst(compAdjPattern,adj+"/JJR");
		}
		if (condensedstring.matches(".*" + superAdjPattern + ".*")) {
			int begin = condensedstring.indexOf("RBS") + 4;
			int end = begin + condensedstring.substring(condensedstring.indexOf("RBS")).indexOf("/JJ") - 4;
			String adj = condensedstring.substring(begin,end);
			condensedstring = condensedstring.replaceFirst(superAdjPattern,adj+"/JJS");
		}
		if (condensedstring.matches(".*" + howAdjPattern + ".*")) {
			int begin = condensedstring.indexOf("WRB") + 4;
			int end = begin + condensedstring.substring(condensedstring.indexOf("WRB")).indexOf("/JJ") - 4;
			String adj = condensedstring.substring(begin,end);
			condensedstring = condensedstring.replaceFirst(howAdjPattern,adj+"/JJH");
		}
		
		/* remove known parts */
		String newtaggedstring = "";
		String[] condensedparts = condensedstring.split(" ");
		for (String part : condensedparts) {
			if (unknownwords.contains(part.substring(0,part.indexOf("/")).toLowerCase())) {
				newtaggedstring += part + " ";
			}
		}
		newtaggedstring = newtaggedstring.trim();
		
		/* build token-POStag-pairs */
		String[] newparts = newtaggedstring.trim().split(" ");
		for (String s : newparts) {
			if (s.contains("/")) {
				result.add(new Pair<String,String>(s.trim().substring(0,s.indexOf("/")),s.trim().substring(s.indexOf("/")+1)));
			} else {
				System.out.println("Look at that, " + s + " has no POS tag!"); // DEBUG
			}
		}
		result = extractNominalPhrases(result);
		return result;
	}
	
	private static List<Pair<String,String>> extractNominalPhrases(List<Pair<String,String>> tokenPOSpairs){
		List<Pair<String,String>> test = new ArrayList<Pair<String,String>>();
		
		String nounPhrase = "";
		String phraseTag = "";
		for(Pair<String,String> pair : tokenPOSpairs){
			if(pair.snd.startsWith("NNP")){
				if(phraseTag.equals("NN")){
					if(!nounPhrase.isEmpty()){
						test.add(new Pair<String, String>(phraseTag.trim(), "NN"));
						nounPhrase = "";
					}
				}
				phraseTag = "NNP";
	    		nounPhrase += " " + pair.fst;
			} else if(pair.snd.startsWith("NN")){
				if(phraseTag.equals("NNP")){
					if(!nounPhrase.isEmpty()){
						test.add(new Pair<String, String>(phraseTag.trim(), "NNP"));
						nounPhrase = "";
					}
				}
				phraseTag = "NN";
	    		nounPhrase += " " + pair.fst;
			} else {
				if(!nounPhrase.isEmpty()){
	    			test.add(new Pair<String, String>(nounPhrase.trim(), phraseTag));
	    			nounPhrase = "";
	    		}
				test.add(pair);
			}
		}
		if(!nounPhrase.isEmpty()){
			test.add(new Pair<String, String>(nounPhrase.trim(), phraseTag));
			nounPhrase = "";
		}
		
		return test;
	}

}