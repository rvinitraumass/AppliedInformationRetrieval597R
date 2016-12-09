package indexer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import static indexer.PRIOR.random;

public class Main {

    public static void main(String[] args) {
		long startTime, endTime;
		Indexer Index = new Indexer();
		String filename = args.length >= 1 ? args[0] : "./shakespeare-scenes.json";
		String manifest = args.length >= 2 ? args[1] : "manifest.txt";
		PRIOR priorType = args.length >= 3 ? PRIOR.valueOf(args[2]) : random;
		Index.parseAndBuildIndex(filename, true, manifest, priorType);

		Indexer newIndex = new Indexer();
		newIndex.loadFromFile(manifest);
		String priorFilename = priorType.toString()+".trecrun";
		BufferedWriter outputFile = null;
		try {
			outputFile = new BufferedWriter(new FileWriter(priorFilename));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		ArrayList<DocumentScore> documents;
		StringBuilder Str = new StringBuilder();
		startTime = System.currentTimeMillis();
		documents = newIndex.retrieve("the king queen royalty");
		endTime = System.currentTimeMillis();
		for (int it = 0; it < documents.size(); it++) {
			DocumentScore doc = documents.get(it);
			String docName = newIndex.documentIdMap.get(doc.documentId);
			System.out.println("Q1 skip "+docName + " "+ (it+1) + " "+ doc.score+" vramasubrama-"+priorType);
			Str.append("Q1 skip "+docName + " "+ (it+1) + " "+ doc.score+" vramasubrama-"+priorType+"\n");
		}
		try {
			outputFile.write(Str.toString());
			outputFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Q1 \"the king queen royalty\" retrieval from " + newIndex.filenamePrefix + "\" index ="  + (endTime - startTime) + "ms");
		try {
			outputFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}