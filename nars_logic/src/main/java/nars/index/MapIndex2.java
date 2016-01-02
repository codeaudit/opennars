package nars.index;

import com.gs.collections.impl.map.mutable.primitive.IntObjectHashMap;
import nars.term.Term;
import nars.term.TermContainer;
import nars.term.Termed;
import nars.term.compound.Compound;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Created by me on 1/2/16.
 */
public class MapIndex2 extends AbstractMapIndex {

    private static final int SUBTERM_RELATION = Integer.MIN_VALUE;

    final Map<Object /* vector(t) */, IntObjectHashMap> data;
    int count = 0;

    public MapIndex2(Map<Object, IntObjectHashMap> data) {
        this.data = data;
    }

    static Object vector(Term t) {
        if (t.isCompound()) return ((Compound)t).subterms();
        return t;
    }

    /** returns previous value */
    public Object putItem(Object vv, int index, Object value) {
        return data.computeIfAbsent(vv,
                (k) -> new IntObjectHashMap(2)).put(index, value);
    }


    @Override
    public Termed getTermIfPresent(Termed t) {
        return (Termed) getItemIfPresent(
                vector(t.term()), t.opRel());
    }


    public Object getItemIfPresent(Object vv, int index) {
        IntObjectHashMap group = data.get(vv);
        if (group == null) return null;
        return group.get(index);
    }

    @Override
    protected TermContainer getSubtermsIfPresent(TermContainer subterms) {
        return (TermContainer) getItemIfPresent(
                subterms, SUBTERM_RELATION);
    }

    @Override
    public void putTerm(Termed t) {
        Object replaced = putItem(vector(t.term()), t.opRel(), t);
        if (replaced == null)
            count++;
    }

    @Override
    protected void putSubterms(TermContainer subterms) {
        putItem(subterms, SUBTERM_RELATION, subterms);
    }


    @Override
    public void clear() {
        count = 0;
        data.clear();
    }

    @Override
    public int subtermsCount() {
        return data.size();
    }

    @Override
    public int size() {
        return count;
    }



    @Override
    public void forEach(Consumer<? super Termed> c) {

    }
}
