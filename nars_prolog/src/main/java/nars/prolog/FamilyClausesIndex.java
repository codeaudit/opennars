package nars.prolog;


import com.google.common.collect.*;
import nars.util.data.CuckooMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <code>FamilyClausesIndex</code> enables family clauses indexing
 * in {@link ClauseDatabase}.
 *
 * @author Paolo Contessi
 * @since 2.2
 */
class FamilyClausesIndex<K extends Comparable<? super K>> {
    private final ListMultimap<K, ClauseInfo> data;

    final List<ClauseInfo> preShared = new ArrayList();

    //extends RBTree<K, List<ClauseInfo>> {
        //extends HashMap<K, List<ClauseInfo>> {


        //TreeMultimap<K, ClauseInfo> = MultimapBuilder.

    //private List<ClauseInfo> varsClauses;

    public FamilyClausesIndex() {
        super();
        data = MultimapBuilder.treeKeys().arrayListValues().build();

        //varsClauses = new ArrayList<>();
    }

//    private Node<K, List<ClauseInfo>> createNewNode(K key, ClauseInfo clause, boolean first) {
//        List<ClauseInfo> list = new ArrayList<>(varsClauses);
//
//        if (first) {
//            list.add(0, clause);
//        } else {
//            list.add(clause);
//        }
//
//        return new Node<>(key, list, Color.RED, null, null);
//    }



    /*
     * Voglio memorizzare un riferimento alla clausola, rispettando l'ordine
     * delle clausole
     *
     * Se l'indice non ha nodi?
     * Se aggiungo un nuovo nodo
     */
    public void insertAsShared(ClauseInfo clause, boolean first) {

        if (data.isEmpty())
            preShared.add(clause);

        List<ClauseInfo> varsClauses;
        for (K k : data.keySet()) {
            varsClauses = data.get(k);
            if (first) {
                varsClauses.add(0, clause);
            } else {
                varsClauses.add(clause);
            }
        }

    }

    /**
     * Creates a new entry (<code>key</code>) in the index, relative to the
     * given <code>clause</code>. If other clauses is associated to <code>key</code>
     * <code>first</code> parameter is used to decide if it is the first or
     * the last clause to be retrieved.
     *
     * @param key    The key of the index
     * @param clause The value to be binded to the given key
     * @param first  If the clause must be binded as first or last element
     */
    public void insert(K key, ClauseInfo clause, boolean first) {
        if (data.containsKey(key)) {
            List<ClauseInfo> l = data.get(key);
            if (first) {
                l.add(0, clause);
            } else {
                l.add(clause);
            }
        }
        else {
            data.put(key, clause);

        }

        if (!preShared.isEmpty()) {
            for (ClauseInfo i : preShared)
                insertAsShared(i, true); //to add prior to the one just added above
            preShared.clear();
        }
    }

    /**
     * Removes all clauses related to the given key
     *
     * @param key The key
     */
    public void remove(K key, ClauseInfo clause) {
        data.remove(key, clause);
    }

    public void removeShared(ClauseInfo clause) {
        if (preShared.isEmpty()) {
            for (K k : data.keySet()) {
                data.get(k).remove(clause);
            }
        }
        else {
            preShared.remove(clause);
        }

    }

    /**
     * Retrieves all the clauses related to the key
     *
     * @param key The key
     * @return The related clauses
     */
    public List<ClauseInfo> get(K key) {
        List<ClauseInfo> n = data.get(key);
        if (n != null && !n.isEmpty())
            return n;


        return preShared;
    }
}