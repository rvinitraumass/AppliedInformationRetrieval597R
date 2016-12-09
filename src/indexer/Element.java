package indexer;

import java.util.ArrayList;
import java.util.List;

public class Element{
    int docId;
    int tf;
    List<Integer> positions;
    
    public Element() {
	positions = new ArrayList<Integer>();
    }
}
