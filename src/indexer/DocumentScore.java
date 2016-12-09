package indexer;

public class DocumentScore implements Comparable<DocumentScore> {
    int documentId;
    double score;
    boolean valid;
    
    @Override
    public int compareTo(DocumentScore o2) {
	if (o2.score > this.score)
	    return 1;
	else if (o2.score < this.score)
	    return -1;
	return 0;
    }
}
