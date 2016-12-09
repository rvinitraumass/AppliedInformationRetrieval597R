package indexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by rvinitra on 11/13/16.
 */
public class Cluster {
    int clusterId;
    List<Integer> documentIds;
    HashMap<Integer, Double> centroid;

    public void addDocument(Integer docId, HashMap<Integer, Double> docVector) {
        documentIds.add(docId);
        for (Map.Entry<Integer, Double> entry : docVector.entrySet()) {
            if (centroid.containsKey(entry.getKey())) {
                double sum = centroid.get(entry.getKey()) * (documentIds.size()-1);
                sum+=entry.getValue();
                centroid.put(entry.getKey(),sum/documentIds.size());
            }
            else {
                centroid.put(entry.getKey(),entry.getValue()/documentIds.size());
            }
        }
    }

    public Cluster(int clusterId, Integer docId, HashMap<Integer, Double> docVector) {
        this.clusterId = clusterId;
        documentIds = new ArrayList<Integer>();
        documentIds.add(docId);
        this.centroid = new HashMap<>(docVector);
    }
}
