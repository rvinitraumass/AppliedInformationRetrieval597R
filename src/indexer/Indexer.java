package indexer;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.collections4.*;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Indexer {
    HashMap<String, Double> statistics;
    HashMap<String, Integer> sceneLength;
    HashMap<String, Integer> playLength;
    HashMap<String, Integer> termsInDocument;
    HashSet<String> documents;
    HashSet<String> terms;
    HashMap<String, Integer> documentFrequency;
    HashMap<String, Integer> termFrequency;
    HashMap<Integer, String> documentIdMap;
    HashMap<Integer, Double> priorProbability;
    BidiMap<Integer, String> termIdMap;
    HashMap<String, List<Element>> indexer;
    HashMap<Integer, HashMap<Integer, Double>> documentVector;
    HashMap<String, Long> indexerMap;
    HashMap<Integer, Long> documentVectorOffset;
    String filenamePrefix;
    String indexFilename;
    String documentVectorFilename;
    Boolean compressed;
    Double k1;
    Double k2;
    Double lambda;
    Double mu;
    Random R = new Random(999);
    
    public Indexer() {
		statistics = new HashMap<String, Double>();
		sceneLength = new HashMap<String, Integer>();
		playLength = new HashMap<String, Integer>();
		termsInDocument = new HashMap<String, Integer>();
		statistics.put("vocabulary", 0.0);
		statistics.put("collection", 0.0);
		statistics.put("documents", 0.0);
		documents = new HashSet<String>();
		termFrequency = new HashMap<String, Integer>();
		documentFrequency = new HashMap<String, Integer>();
		documentIdMap = new HashMap<Integer, String>();
		termIdMap = new DualHashBidiMap<Integer, String>();
		documentVector = new HashMap<Integer, HashMap<Integer, Double>>();
		indexer = new HashMap<String, List<Element>>();
		indexerMap = new HashMap<String,Long>();
		documentVectorOffset = new HashMap<Integer,Long>();
        priorProbability = new HashMap<Integer, Double>();
		compressed = false;
		k1 = 1.2;
		k2 = 1.0;
		lambda = 0.7;
		mu = 2500.0;
    }

    public void parseAndBuildIndex(String filename, boolean compressed, String manifest, PRIOR priorType) {
		this.compressed = compressed;
		JSONParser parser = new JSONParser();
		JSONObject corpus = null;
		try {
			corpus = (JSONObject) parser.parse(new FileReader(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		JSONArray array = (JSONArray) corpus.get("corpus");
		int documentId = 1, termId = 1;
		for(Object item: array) {
				String sceneId = ((JSONObject) item).get("sceneId").toString();
				String playId = ((JSONObject) item).get("playId").toString();
				String text = ((JSONObject) item).get("text").toString();
				String[] words = text.split("\\s+");
				int position = 1;
					documentIdMap.put(documentId,sceneId);
					HashMap<Integer, Double> termInDocFrequency = new HashMap<Integer, Double>();
				for (String s: words) {
					List<Element> elementList;
					if (indexer.containsKey(s)) {
					elementList = indexer.get(s);
					Element lastElement = elementList.get(elementList.size()-1);
					if (lastElement.docId == documentId) {
						int prevPosition = lastElement.positions.stream().mapToInt(Integer::intValue).sum();
						lastElement.positions.add(position - prevPosition);
						lastElement.tf++;
						elementList.set(elementList.size()-1,lastElement);
					}
					else {
						Element element = new Element();
						element.docId = documentId;
						element.tf = 1;
						element.positions = new ArrayList<Integer>();
						element.positions.add(position);
						elementList.add(element);
					}
					int tf = termFrequency.get(s);
					tf++;
					termFrequency.put(s, tf);
					}
					else {
					elementList = new ArrayList<Element>();
					Element element = new Element();
					element.docId = documentId;
					element.tf = 1;
					element.positions = new ArrayList<Integer>();
					element.positions.add(position);
					elementList.add(element);
					termFrequency.put(s, 1);
					}
					if (!termIdMap.containsValue(s)) {
					termIdMap.put(termId++, s);
					}
					int termIdKey = termIdMap.inverseBidiMap().get(s);
					termInDocFrequency.put(termIdKey, termInDocFrequency.containsKey(termIdKey) ? termInDocFrequency.get(termIdKey)+1.0 : 1.0);
					position++;
					indexer.put(s,elementList);
					documentFrequency.put(s, elementList.size());
					if (!documents.contains(sceneId)) {
					documents.add(sceneId);
					Double documentCount = statistics.get("documents");
					documentCount++;
					statistics.put("documents", documentCount);
					}
					Double collectionCount = statistics.get("collection");
					collectionCount++;
					statistics.put("collection", collectionCount);
				}
				if (sceneLength.containsKey(sceneId)) {
					int sLength = sceneLength.get(sceneId);
					sLength+=words.length;
					sceneLength.put(sceneId, sLength);
				}
				else {
					sceneLength.put(sceneId, words.length);
				}
				if (playLength.containsKey(playId)) {
					int pLength = playLength.get(playId);
					pLength+=words.length;
					playLength.put(playId, pLength);
				}
				else {
					playLength.put(playId, words.length);
				}
				documentVector.put(documentId, termInDocFrequency);
				termsInDocument.put(documentIdMap.get(documentId), termInDocFrequency.size());
				documentId++;
		}
		generatePriors(priorType);
		statistics.put("vocabulary",(double) indexer.keySet().size());
		statistics.put("avgSceneLength", statistics.get("collection")/statistics.get("documents"));
		terms = new HashSet<String>(indexer.keySet());
        try {
            BufferedWriter priorFile = new BufferedWriter(new FileWriter(priorType.toString() + ".prior"));
            StringBuilder stringToWrite = new StringBuilder();
            for(Map.Entry<Integer,Double> entry: priorProbability.entrySet()) {
                stringToWrite.append(documentIdMap.get(entry.getKey())+" "+entry.getValue()+"\n");
            }
            priorFile.write(stringToWrite.toString());
            priorFile.flush();
            priorFile.close();

        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
		writeToFile(manifest);
    }

    public void generatePriors(PRIOR priorType) {
        int numOfDocs = documentIdMap.size();
        List<Integer> freq = new ArrayList<>();
        int sum = 0;
        switch (priorType) {
            case uniform: Integer[] prob = new Integer[numOfDocs];
                        Arrays.fill(prob, new Integer(1));
                        freq = Arrays.asList(prob);
                        sum = numOfDocs;
                        break;
            default: for(int i=1; i<=numOfDocs; i++) {
                        int r = R.nextInt(Integer.MAX_VALUE/numOfDocs);
                        freq.add(r);
                        sum+=r;
                    }
                    break;
        }
        int i = 0;
        for(Integer docId: documentIdMap.keySet()) {
            priorProbability.put(docId, Math.log((double)freq.get(i++)/sum));
        }
    }
    
    public void writeToFile(String manifest) {
		filenamePrefix = compressed ? "compressed" : "uncompressed";
		FileOutputStream out = null;
		try {
			indexFilename = filenamePrefix + "Index.txt";
			out = new FileOutputStream(indexFilename);
			for(HashMap.Entry<String, List<Element>> entry: indexer.entrySet()) {
			indexerMap.put(entry.getKey(), out.getChannel().position());
			for(Element e: entry.getValue()) {
				if (compressed) {
				VByte.encode(out, e.docId);
				out.flush();
				VByte.encode(out, e.tf);
				out.flush();
				for(Integer p: e.positions) {
					VByte.encode(out, p);
					out.flush();
				}
				}
				else {
				StringBuilder str = new StringBuilder();
				str.append(e.docId + " " + e.tf);
				for(Integer p: e.positions) {
					str.append(" "+ p);
				}
				str.append("\n");
				out.write(str.toString().getBytes());
				out.flush();
				}
			}
			}
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileOutputStream vectorOut = null;
		try {
			documentVectorFilename = filenamePrefix + "DocumentVector.txt";
			vectorOut = new FileOutputStream(documentVectorFilename);
			for(Entry<Integer, HashMap<Integer, Double>> entry: documentVector.entrySet()) {
			documentVectorOffset.put(entry.getKey(), vectorOut.getChannel().position());
			for(Entry<Integer, Double> e: entry.getValue().entrySet()) {
				if (compressed) {
				VByte.encode(vectorOut, e.getKey());
				vectorOut.flush();
				VByte.encode(vectorOut, e.getValue().longValue());
				vectorOut.flush();
				}
				else {
				StringBuilder str = new StringBuilder();
				str.append(e.getKey() + " " + e.getValue());
				str.append("\n");
				vectorOut.write(str.toString().getBytes());
				vectorOut.flush();
				}
			}
			}
			vectorOut.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			FileOutputStream statsMap = new FileOutputStream(filenamePrefix + "StatisticsMap.txt");
			ObjectOutputStream statsObjStr = new ObjectOutputStream(statsMap);
			statsObjStr.writeObject(statistics);
			statsObjStr.flush();
			statsObjStr.close();
			FileOutputStream sceneLengthMap = new FileOutputStream(filenamePrefix + "SceneLengthMap.txt");
			ObjectOutputStream sceneLengthObjStr = new ObjectOutputStream(sceneLengthMap);
			sceneLengthObjStr.writeObject(sceneLength);
			sceneLengthObjStr.flush();
			sceneLengthObjStr.close();
			FileOutputStream playLengthMap = new FileOutputStream(filenamePrefix + "PlayLengthMap.txt");
			ObjectOutputStream playLengthObjStr = new ObjectOutputStream(playLengthMap);
			playLengthObjStr.writeObject(playLength);
			playLengthObjStr.flush();
			playLengthObjStr.close();
			FileOutputStream docIdMap = new FileOutputStream(filenamePrefix + "DocumentIdMap.txt");
			ObjectOutputStream objStr = new ObjectOutputStream(docIdMap);
			objStr.writeObject(documentIdMap);
			objStr.flush();
			objStr.close();
			FileOutputStream indexerOffsetMap = new FileOutputStream(filenamePrefix + "IndexerOffsetMap.txt");
			ObjectOutputStream indObjStr = new ObjectOutputStream(indexerOffsetMap);
			indObjStr.writeObject(indexerMap);
			indObjStr.flush();
			indObjStr.close();
			FileOutputStream documentVectorOffsetMap = new FileOutputStream(filenamePrefix + "DocumentVectorOffsetMap.txt");
			ObjectOutputStream docVecObjStr = new ObjectOutputStream(documentVectorOffsetMap);
			docVecObjStr.writeObject(documentVectorOffset);
			docVecObjStr.flush();
			docVecObjStr.close();
			FileOutputStream docFreqMap = new FileOutputStream(filenamePrefix + "DocumentFrequencyMap.txt");
			ObjectOutputStream docFreqObjStr = new ObjectOutputStream(docFreqMap);
			docFreqObjStr.writeObject(documentFrequency);
			docFreqObjStr.flush();
			docFreqObjStr.close();
			FileOutputStream termFreqMap = new FileOutputStream(filenamePrefix + "TermFrequencyMap.txt");
			ObjectOutputStream termFreqObjStr = new ObjectOutputStream(termFreqMap);
			termFreqObjStr.writeObject(termFrequency);
			termFreqObjStr.flush();
			termFreqObjStr.close();
			FileOutputStream termsIn = new FileOutputStream(filenamePrefix + "Terms.txt");
			ObjectOutputStream termsObjStr = new ObjectOutputStream(termsIn);
			termsObjStr.writeObject(terms);
			termsObjStr.flush();
			termsObjStr.close();
			FileOutputStream documentsIn = new FileOutputStream(filenamePrefix + "Documents.txt");
			ObjectOutputStream documentsObjStr = new ObjectOutputStream(documentsIn);
			documentsObjStr.writeObject(documents);
			documentsObjStr.flush();
			documentsObjStr.close();
			FileOutputStream termIdMapOut = new FileOutputStream(filenamePrefix + "TermIdMap.txt");
			ObjectOutputStream termObjStr = new ObjectOutputStream(termIdMapOut);
			termObjStr.writeObject(termIdMap);
			termObjStr.flush();
			termObjStr.close();
			FileOutputStream termsInDocumentMap = new FileOutputStream(filenamePrefix + "TermsInDocumentMap.txt");
			ObjectOutputStream termsInDocObjStr = new ObjectOutputStream(termsInDocumentMap);
			termsInDocObjStr.writeObject(termsInDocument);
			termsInDocObjStr.flush();
			termsInDocObjStr.close();
            FileOutputStream priorProbabilityMap = new FileOutputStream(filenamePrefix + "PriorProbabilityMap.txt");
            ObjectOutputStream priorProbabilityMapObjStr = new ObjectOutputStream(priorProbabilityMap);
            priorProbabilityMapObjStr.writeObject(priorProbability);
            priorProbabilityMapObjStr.flush();
            priorProbabilityMapObjStr.close();
			HashMap<String, String> manifestMap = new HashMap<String, String>();
			manifestMap.put("statisticsMap", filenamePrefix+"StatisticsMap.txt");
			manifestMap.put("sceneLengthMap", filenamePrefix+"SceneLengthMap.txt");
			manifestMap.put("playLengthMap", filenamePrefix+"PlayLengthMap.txt");
			manifestMap.put("indexOffsetMap", filenamePrefix+"IndexerOffsetMap.txt");
			manifestMap.put("documentIdMap", filenamePrefix+"DocumentIdMap.txt");
			manifestMap.put("termIdMap", filenamePrefix + "TermIdMap.txt");
			manifestMap.put("documentFrequencyMap", filenamePrefix+"DocumentFrequencyMap.txt");
			manifestMap.put("termFrequencyMap", filenamePrefix+"TermFrequencyMap.txt");
			manifestMap.put("terms", filenamePrefix+"Terms.txt");
			manifestMap.put("documents", filenamePrefix+"Documents.txt");
			manifestMap.put("index", indexFilename);
			manifestMap.put("documentVectorindex", documentVectorFilename);
			manifestMap.put("compressed", compressed.toString());
			manifestMap.put("documentVectorOffsetMap", filenamePrefix + "DocumentVectorOffsetMap.txt");
			manifestMap.put("termsInDocumentMap", filenamePrefix+"TermsInDocumentMap.txt");
            manifestMap.put("priorProbabilityMap", filenamePrefix+"PriorProbabilityMap.txt");
			FileOutputStream mani = new FileOutputStream(manifest);
			ObjectOutputStream fileMapObjStr = new ObjectOutputStream(mani);
			fileMapObjStr.writeObject(manifestMap);
			fileMapObjStr.flush();
			fileMapObjStr.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    public void loadFromFile(String manifest) {
		FileInputStream mani = null, indexOffsetIn = null, docFreqIn=null, stats = null, termFreqIn = null, docIdIn = null, termsIn = null, docsIn = null, sceneLengthIn = null, playLengthIn = null;
		HashMap<String, String> fileMap = new HashMap<String,String>();
		try {
			mani = new FileInputStream(manifest);
			ObjectInputStream objStr = new ObjectInputStream(mani);
			fileMap = (HashMap<String, String>) objStr.readObject();
			objStr.close();
			compressed = Boolean.valueOf(fileMap.get("compressed"));
			filenamePrefix = compressed ? "compressed" : "uncompressed";
			stats = new FileInputStream(fileMap.get("statisticsMap"));
			ObjectInputStream statsObjStr = new ObjectInputStream(stats);
			statistics.clear();
			statistics = (HashMap<String, Double>) statsObjStr.readObject();
			statsObjStr.close();
			sceneLengthIn = new FileInputStream(fileMap.get("sceneLengthMap"));
			ObjectInputStream sceneLengthObjStr = new ObjectInputStream(sceneLengthIn);
			sceneLength.clear();
			sceneLength = (HashMap<String, Integer>) sceneLengthObjStr.readObject();
			sceneLengthObjStr.close();
			playLengthIn = new FileInputStream(fileMap.get("playLengthMap"));
			ObjectInputStream playLengthObjStr = new ObjectInputStream(playLengthIn);
			playLength.clear();
			playLength = (HashMap<String, Integer>) playLengthObjStr.readObject();
			playLengthObjStr.close();
			indexOffsetIn = new FileInputStream(fileMap.get("indexOffsetMap"));
			ObjectInputStream indOffsetObjStr = new ObjectInputStream(indexOffsetIn);
			indexerMap.clear();
			indexerMap = (HashMap<String, Long>) indOffsetObjStr.readObject();
			InputStream docVectorOffsetIn = new FileInputStream(fileMap.get("documentVectorOffsetMap"));
			ObjectInputStream docVecOffsetObjStr = new ObjectInputStream(docVectorOffsetIn);
			documentVectorOffset.clear();
			documentVectorOffset = (HashMap<Integer, Long>) docVecOffsetObjStr.readObject();
			docIdIn = new FileInputStream(fileMap.get("documentIdMap"));
			ObjectInputStream docIdObjStr = new ObjectInputStream(docIdIn);
			documentIdMap.clear();
			documentIdMap = (HashMap<Integer, String>) docIdObjStr.readObject();
			FileInputStream termIdIn = new FileInputStream(fileMap.get("termIdMap"));
			ObjectInputStream termIdObjStr = new ObjectInputStream(termIdIn);
			termIdMap.clear();
			termIdMap = (BidiMap<Integer, String>) termIdObjStr.readObject();
			FileInputStream termsInDocIn = new FileInputStream(fileMap.get("termsInDocumentMap"));
			ObjectInputStream termInDocObjStr = new ObjectInputStream(termsInDocIn);
			termsInDocument.clear();
			termsInDocument = (HashMap<String, Integer>) termInDocObjStr.readObject();
            FileInputStream priorProbabilityMapIn = new FileInputStream(fileMap.get("priorProbabilityMap"));
            ObjectInputStream priorProbabilityMapObjStr = new ObjectInputStream(priorProbabilityMapIn);
            priorProbability.clear();
            priorProbability = (HashMap<Integer, Double>) priorProbabilityMapObjStr.readObject();
			docFreqIn = new FileInputStream(fileMap.get("documentFrequencyMap"));
			ObjectInputStream docFreqObjStr = new ObjectInputStream(docFreqIn);
			documentFrequency.clear();
			documentFrequency = (HashMap<String, Integer>) docFreqObjStr.readObject();
			termFreqIn = new FileInputStream(fileMap.get("termFrequencyMap"));
			ObjectInputStream termFreqObjStr = new ObjectInputStream(termFreqIn);
			termFrequency.clear();
			termFrequency = (HashMap<String, Integer>) termFreqObjStr.readObject();
			termsIn = new FileInputStream(fileMap.get("terms"));
			ObjectInputStream termsObjStr = new ObjectInputStream(termsIn);
			if (terms == null) {
			terms = new HashSet<String>();
			}
			terms.clear();
			terms = (HashSet<String>) termsObjStr.readObject();
			docsIn = new FileInputStream(fileMap.get("documents"));
			ObjectInputStream docsObjStr = new ObjectInputStream(docsIn);
			documents.clear();
			documents = (HashSet<String>) docsObjStr.readObject();
			indexFilename = fileMap.get("index");
			documentVectorFilename = fileMap.get("documentVectorindex");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
    }
    
    public ArrayList<Element> retrieveTermFromFile(String word) {
		FileInputStream in = null;
		ArrayList<Element> elements = new ArrayList<Element>();
		try {
			in = new FileInputStream(indexFilename);
			long position = indexerMap.get(word);
			in.getChannel().position(position);
			int numOfDocs = documentFrequency.get(word);
			if (compressed) {
			for(int i=1; i<=numOfDocs; i++) {
				Element element = new Element();
				element.docId = (int) VByte.decode(in);
				element.tf = (int) VByte.decode(in);
				element.positions = new ArrayList<Integer>();
				int pos = (int) VByte.decode(in);
				element.positions.add(pos);
				for(int j=2; j<=element.tf; j++) {
				pos+= (int) VByte.decode(in);
				element.positions.add(pos);
				}
				elements.add(element);
			}
			}
			else {
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line;
			for(int i=1; i<=numOfDocs; i++) {
				Element element = new Element();
				if ((line = br.readLine()) != null) {
				String[] lineArr = line.split(" ");
				element.docId = Integer.parseInt(lineArr[0]);
				element.tf = Integer.parseInt(lineArr[1]);
				element.positions = new ArrayList<Integer>();
				int pos = Integer.parseInt(lineArr[2]);
				element.positions.add(pos);
				for(int j=3; j<element.tf + 2; j++) {
					pos+=Integer.parseInt(lineArr[j]);
					element.positions.add(pos);
				}
				}
				elements.add(element);
			}
			br.close();
			}
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return elements;
    }
    
    public HashMap<String, Integer> retrieveDocumentFromFile(Integer documentId) {
		FileInputStream in = null;
		HashMap<String, Integer> termInDocFrequency = new HashMap<String,Integer>();
		try {
			in = new FileInputStream(documentVectorFilename);
			long position = documentVectorOffset.get(documentId);
			in.getChannel().position(position);
			int numOfTerms = termsInDocument.get(documentIdMap.get(documentId));
			if (compressed) {
			for(int i=1; i<=numOfTerms; i++) {
				int termId = (int) VByte.decode(in);
				int tf = (int) VByte.decode(in);
				termInDocFrequency.put(termIdMap.get(termId),tf);
			}
			}
			else {
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line;
			for(int i=1; i<=numOfTerms; i++) {
				if ((line = br.readLine()) != null) {
				String[] lineArr = line.split(" ");
				int termId = Integer.parseInt(lineArr[0]);
				int tf = Integer.parseInt(lineArr[1]);
				termInDocFrequency.put(termIdMap.get(termId),tf);
				}
			}
			br.close();
			}
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return termInDocFrequency;
    }

    public void retrieveDocumentVectorFromFile() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(documentVectorFilename);
            for(int documentId: documentIdMap.keySet()) {
                HashMap<Integer, Double> termInDocFrequency = new HashMap<Integer,Double>();
                long position = documentVectorOffset.get(documentId);
                in.getChannel().position(position);
                int numOfTerms = termsInDocument.get(documentIdMap.get(documentId));
                if (compressed) {
                    for (int i = 1; i <= numOfTerms; i++) {
                        int termId = (int) VByte.decode(in);
                        int tf = (int) VByte.decode(in);
                        termInDocFrequency.put(termId, new Double(tf));
                    }
                } else {
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    String line;
                    for (int i = 1; i <= numOfTerms; i++) {
                        if ((line = br.readLine()) != null) {
                            String[] lineArr = line.split(" ");
                            int termId = Integer.parseInt(lineArr[0]);
                            int tf = Integer.parseInt(lineArr[1]);
                            termInDocFrequency.put(termId, new Double(tf));
                        }
                    }
                    br.close();
                }
                documentVector.put(documentId,termInDocFrequency);
            }
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<DocumentScore> retrieve(String query) {
		ArrayList<DocumentScore> documentScores = new ArrayList<DocumentScore>(documents.size());
		ArrayList<ArrayList<Element>> elements = new ArrayList<ArrayList<Element>>();
		String[] words = query.split("\\s");
		HashMap<String, Integer> queryFrequency = new HashMap<String, Integer>();
		for(String s: words) {
			queryFrequency.put(s, queryFrequency.containsKey(s) ? queryFrequency.get(s)+1 : 1);
		}
		ArrayList<String> queryWords = new ArrayList<String>();
		for(String s: words) {
			ArrayList<Element> invertedList = retrieveTermFromFile(s);
			queryWords.add(s);
			elements.add(invertedList);
		}
		for(Integer docId: documentIdMap.keySet()) {
			DocumentScore ds = new DocumentScore();
			ds.documentId = docId;
			ds.score = 0;
			ds.valid = false;
            int counter = 0;
            for(ArrayList<Element> invertedList: elements) {
                String term = queryWords.get(counter++);
                for (Element element : invertedList) {
                    if (element.docId == docId) {
                        ds.score += scoreQLDIR(element.tf, sceneLength.get(documentIdMap.get(docId)), term);
                        ds.valid = true;
                        break;
                    } else if (element.docId > docId) {
                        break;
                    }
                }
            }
			if (ds.valid) {
                ds.score += priorProbability.get(docId);
				documentScores.add(ds);
			}
		}
		Collections.sort(documentScores);
		return documentScores;
    }

    public Double orderedWindowSearch(ArrayList<Element> selectedInvList, ArrayList<String> queryWords, int windowSize) {
        Double score = 0.0;
        List<List<Integer>> table = new ArrayList<List<Integer>>();
        table.add(selectedInvList.get(0).positions);
        for(int i = 1; i < selectedInvList.size(); i++) {
            List<Integer> validPos = new ArrayList<>();
            if (table.size() ==  i) {
                for (int k = 0; k < table.get(i - 1).size(); k++) {
                    for (int w = 1; w <= windowSize; w++) {
                        for (int j = 0; j < selectedInvList.get(i).positions.size(); j++) {
                            if (selectedInvList.get(i).positions.get(j) == table.get(i - 1).get(k) + w) {
                                validPos.add(selectedInvList.get(i).positions.get(j));
                            } else if (selectedInvList.get(i).positions.get(j) < table.get(i - 1).get(k)) {
                                continue;
                            } else {
                                break;
                            }
                        }
                    }
                }
                if (validPos.size() != 0) {
                    table.add(validPos);
                }
                else {
                    break;
                }
            }
            else {
                break;
            }
        }
        if (table.size() ==  selectedInvList.size()) {
            int tf = table.get(table.size()-1).size();
            int docId = selectedInvList.get(0).docId;
            for(int i=0; i< selectedInvList.size(); i++) {
                score += Math.log(scoreQLDIR(tf,sceneLength.get(documentIdMap.get(docId)),queryWords.get(i)));
            }
        }
        return score;
    }

    public Double unOrderedWindowSearch(ArrayList<Element> selectedInvList, ArrayList<String> queryWords, int windowSize) {
        Double score = 0.0;
		List<Integer> position = new ArrayList<>();
		for(Element e: selectedInvList) {
			position.add(e.positions.get(0));
		}
		int minPos = Collections.min(position);
		position.clear();
		for(Element e: selectedInvList) {
			position.add(e.positions.get(e.positions.size()-1));
		}
		int maxPos = Collections.max(position);
		int tf = 0;
		int lowerWindow = minPos;
		for(int upperWindow=minPos+windowSize-1; upperWindow<=maxPos; upperWindow++) {
			ArrayList<Boolean> flag = new ArrayList<>();
			for(int i=0; i<selectedInvList.size(); i++) {
				for(int pos=lowerWindow; pos<=upperWindow; pos++) {
					if (selectedInvList.get(i).positions.contains(pos)) {
						flag.add(true);
						break;
					}
				}
				if (flag.size() != i+1) {
					break;
				}
			}
			if (flag.size() == selectedInvList.size()) {
				tf++;
			}
			lowerWindow++;
		}
		if (tf != 0) {
			int docId = selectedInvList.get(0).docId;
			for (int i = 0; i < selectedInvList.size(); i++) {
				score += Math.log(scoreQLDIR(tf, sceneLength.get(documentIdMap.get(docId)), queryWords.get(i)));
			}
		}
        return score;
    }

	public Double filterRequire(ArrayList<Element> selectedInvList, ArrayList<String> queryWords) {
		Double score = 0.0;
		int docId = selectedInvList.get(0).docId;
		ArrayList<Double> p = new ArrayList<>();
		for(int i=0; i< selectedInvList.size(); i++) {
			score += Math.log(scoreQLDIR(selectedInvList.get(i).tf,sceneLength.get(documentIdMap.get(docId)),queryWords.get(i)));
		}
		return score;
	}

	public Double filterReject(HashMap<String,Element> selectedInvMap, ArrayList<String> queryWords, int docId) {
		Double score = 0.0;
		ArrayList<Double> p = new ArrayList<>();
		for(int i=0; i<queryWords.size(); i++) {
			if (selectedInvMap.containsKey(queryWords.get(i))) {
				score += Math.log(scoreQLDIR(selectedInvMap.get(queryWords.get(i)).tf, sceneLength.get(documentIdMap.get(docId)), queryWords.get(i)));
			}
			else {
				score += Math.log(scoreQLDIR(0,sceneLength.get(documentIdMap.get(docId)),queryWords.get(i)));
			}
		}
		return score;
	}


    public Integer getTermFrequency(String word) {

		return termFrequency.get(word);
    }
    
    public Integer getDocumentFrequency(String word) {

		return documentFrequency.get(word);
    }
    
    public Long getVocabularyCount() {

        return statistics.get("vocabulary").longValue();
    }
    
    public Long getCollectionCount() {

        return statistics.get("collection").longValue();
    }
    
    public Long getDocumentCount() {

        return statistics.get("documents").longValue();
    }
    
    public Double getAverageSceneLength() {

        return statistics.get("avgSceneLength");
    }
    
    public String getMinSceneId() {

        return getMinKey(sceneLength);
    }
    
    public String getMaxSceneId() {

        return getMaxKey(sceneLength);
    }
    
    public String getMinPlayId() {

        return getMinKey(playLength);
    }
    
    public String getMaxPlayId() {

        return getMaxKey(playLength);
    }

    public String getMinKey(Map<String, Integer> map) {
        int minVal = Integer.MAX_VALUE;
        String minKey = null;
        for(String s: map.keySet()) {
            if (map.get(s) < minVal) {
            minVal = map.get(s);
            minKey = s;
            }
        }
        return minKey;
    }
    
    public String getMaxKey(Map<String, Integer> map) {
        int maxVal = Integer.MIN_VALUE;
        String maxKey = null;
        for(String s: map.keySet()) {
            if (map.get(s) > maxVal) {
            maxVal = map.get(s);
            maxKey = s;
            }
        }
        return maxKey;
    }
    
    public String highScoringTwoWordPhrase(String query) {
        PriorityQueue<TwoWordPhrase> pqtw = new PriorityQueue<TwoWordPhrase>(terms.size(), new Comparator<TwoWordPhrase>() {

            @Override
            public int compare(TwoWordPhrase o1, TwoWordPhrase o2) {
            return Double.compare(o2.dcoef, o1.dcoef);
            }

        });
        ArrayList<Element> invertedListA = retrieveTermFromFile(query);
        for(String s: terms) {
            int n_ab=0, n_a, n_b;
            n_a = termFrequency.get(query);
            n_b = termFrequency.get(s);
            if (!s.equals(query)) {
            ArrayList<Element> invertedListB = retrieveTermFromFile(s);
            for(Element a: invertedListA) {
                for(Element b: invertedListB) {
                if (a.docId == b.docId) {
                    for(int p_a: a.positions) {
                    for(int p_b: b.positions) {
                        if (p_a == p_b+1 || p_a == p_b-1) {
                        n_ab++;
                        }
                    }
                    }
                }
                }
            }
            TwoWordPhrase twp = new TwoWordPhrase();
            twp.word = s;
            twp.dcoef = ((double)2*n_ab)/(n_a+n_b);
            pqtw.add(twp);
            }
        }
        return pqtw.peek().word;
    }
    
    public double scoreBM25(int tf, int qf, int dl, String term) {
        double avdl = getAverageSceneLength();
        double k, b = 0.75, idf, n_i, N, score;
        k = (k1*(1 - b)) + (b*dl/avdl);
        N = getDocumentCount();
        n_i = getDocumentFrequency(term);
        idf = Math.log((N + 0.5)/(n_i + 0.5));
        score = idf * (((k1+1)*tf)/(k+tf)) * (((k2+1)*qf)/(k2+qf));
        return score;
    }
    
    public double scoreQLJM(int tf, int dl, String term) {
        double c_qi, C, score;
        C = getCollectionCount();
        c_qi = getTermFrequency(term);
        score = Math.log(((1-lambda)*tf/dl)+(lambda*c_qi/C));
        return score;
    }
    
    public double scoreCosine(HashMap<Integer, Double> v1, HashMap<Integer, Double> v2) {
        double N, n_i, f_i, score = 0, doc_norm = 0, query_norm = 0;
        N = getDocumentCount();
        for (Map.Entry<Integer, Double> entry : v1.entrySet()) {
            n_i = getDocumentFrequency(termIdMap.get(entry.getKey()));
            f_i = entry.getValue();
            doc_norm += Math.pow((Math.log(f_i)+1)*Math.log(N/n_i), 2);
        }
        doc_norm = Math.sqrt(doc_norm);
        for (Map.Entry<Integer, Double> entry : v2.entrySet()) {
            n_i = getDocumentFrequency(termIdMap.get(entry.getKey()));
            f_i = entry.getValue();
            query_norm += Math.pow((Math.log(f_i)+1)*Math.log(N/n_i), 2);
        }
        query_norm = Math.sqrt(query_norm);
        for(Integer term: v2.keySet()) {
            if (v1.containsKey(term)) {
                n_i = getDocumentFrequency(termIdMap.get(term));
                double docWeight = (Math.log(v1.get(term))+1)*Math.log(N/n_i);
                double queryWeight = (Math.log(v2.get(term))+1)*Math.log(N/n_i);
                score += (docWeight*queryWeight)/(doc_norm*query_norm);
            }
        }
        return score;
    }

    public double scoreQLDIR(int tf, int dl, String term) {
        double c_qi, C, score;
        C = getCollectionCount();
        c_qi = getTermFrequency(term);
        score = Math.log((tf + (mu*c_qi/C))/(dl+mu));
        return score;
    }

    public double scoreAnd(ArrayList<Double> p) {
        double score = 0.0;
        for(Double p_i: p) {
            score+= Math.log(p_i);
        }
        return score;
    }

    public double scoreOr(ArrayList<Double> p) {
        double score = 1.0;
        for(Double p_i: p) {
            score*= (1-p_i);
        }
        return Math.log(1.0-score);
    }

    public double scoreNot(Double p1) {
        return Math.log(1.0-p1);
    }

    public double scoreWand(ArrayList<Double> p, ArrayList<Double> wt) {
        double score = 0.0;
        for(int i=0; i<p.size();i++) {
            score+= wt.get(i) * Math.log(p.get(i));
        }
        return score;
    }

    public double scoreMax(ArrayList<Double> p) {
        return Math.log(Collections.max(p));
    }

    public double scoreSum(ArrayList<Double> p) {
        double score = 0.0;
        for(Double p_i: p) {
            score+= p_i;
        }
        return Math.log(score/p.size());
    }

    public double scoreWsum(ArrayList<Double> p, ArrayList<Double> wt) {
        double score = 0.0;
        double wsum = 0.0;
        for(int i=0; i<p.size();i++) {
            score+= p.get(i)* wt.get(i);
            wsum = wt.get(i);
        }
        return Math.log(score/wsum);
    }

    public ArrayList<Cluster> cluster(LINKING linking, double threshold) {
        retrieveDocumentVectorFromFile();
		ArrayList<Cluster> clusters = new ArrayList<>();
        Cluster c1 = new Cluster(1, 1, documentVector.get(1));
        clusters.add(c1);
        int cId = 2;
        for(int doc=2; doc <= documents.size(); doc++) {
			HashMap<Cluster,Double> scores = new HashMap<Cluster,Double>();
            for(Cluster cj: clusters) {
                double score = 0;
                switch (linking) {
                    case min: score = minLinking(doc, cj); break;
                    case max: score = maxLinking(doc, cj); break;
                    case average: score = averageLinking(doc, cj); break;
                    default: score = meanLinking(doc, cj); break;
                }
                if (score > threshold) {
					scores.put(cj,score);
                }
            }
            if (scores.size() == 0) {
                Cluster c = new Cluster(cId++, doc, documentVector.get(doc));
                clusters.add(c);
            }
            else {
				Cluster c_max = maxSimilarityCluster(scores);
				c_max.addDocument(doc, documentVector.get(doc));
			}
        }
        return clusters;
	}

	public Cluster maxSimilarityCluster(HashMap<Cluster, Double> scores) {
		Double maxScore = Double.MIN_VALUE;
		Cluster maxCluster = null;
		for(Cluster c: scores.keySet()) {
			if (scores.get(c) > maxScore) {
				maxCluster = c;
				maxScore = scores.get(c);
			}
		}
		return maxCluster;
	}

	public double minLinking(Integer docId, Cluster c) {
        double min = Double.MAX_VALUE;
        HashMap<Integer, Double> docVector = documentVector.get(docId);
        for(int doc: c.documentIds) {
            double score = scoreCosine(docVector,documentVector.get(doc));
            if (score < min) {
                min = score;
            }
        }
        return min;
    }

    public double maxLinking(Integer docId, Cluster c) {
        double max = Double.MIN_VALUE;
        HashMap<Integer, Double> docVector = documentVector.get(docId);
        for(int doc: c.documentIds) {
            double score = scoreCosine(docVector,documentVector.get(doc));
            if (score > max) {
                max = score;
            }
        }
        return max;
    }

    public double averageLinking(Integer docId, Cluster c) {
        double sum = 0.0;
        HashMap<Integer, Double> docVector = documentVector.get(docId);
        for(int doc: c.documentIds) {
            sum+=scoreCosine(docVector,documentVector.get(doc));
        }
        return sum/c.documentIds.size();
    }

    public double meanLinking(Integer docId, Cluster c) {
        return scoreCosine(documentVector.get(docId), c.centroid);
    }
}
