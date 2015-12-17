package nars.term.match;

import nars.term.Term;
import nars.term.compound.Compound;
import nars.term.transform.Subst;

import java.util.Collection;
import java.util.Set;

/**
 * implementation which stores its series of subterms as a Term[]
 */
public class CollectionEllipsisMatch extends EllipsisMatch {

    public final Collection<Term> term;

    public CollectionEllipsisMatch(Collection<Term> term) {
        this.term = term;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + term.toString();
    }

    @Override
    public boolean applyTo(Subst f, Collection<Term> target, boolean fullMatch) {
        target.addAll(term);
        return true;
    }

    @Override
    public int size() {
        return term.size();
    }

    @Override
    public boolean addContained(Compound Y, Set<Term> ineligible) {

        for (Term e : term) {
            if (!Y.containsTerm(e)) return false;
            ineligible.add(e);
        }
        return true;

    }


}