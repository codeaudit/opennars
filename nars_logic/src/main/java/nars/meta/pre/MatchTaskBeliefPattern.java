package nars.meta.pre;

import nars.Global;
import nars.Op;
import nars.meta.PreCondition;
import nars.meta.RuleMatch;
import nars.meta.TaskRule;
import nars.term.Term;

/**
 * Created by me on 8/15/15.
 */
public class MatchTaskBeliefPattern extends PreCondition {

    public final PairMatchingProduct pattern;
    private final String id;


    public MatchTaskBeliefPattern(Term taskPattern, Term beliefPattern, TaskRule rule) {

        this.pattern = new PairMatchingProduct(taskPattern, beliefPattern);

        if (Global.DEBUG) {
            if (beliefPattern.structure() == 0) {

                // if nothing else in the rule involves this term
                // which will be a singular VAR_PATTERN variable
                // then allow null
                if (beliefPattern.op() != Op.VAR_PATTERN)
                    throw new RuntimeException("not what was expected");

            }
        }

        /*System.out.println( Long.toBinaryString(
                        pStructure) + " " + pattern
        );*/

        this.id = getClass().getSimpleName() + "[" + pattern.toStringCompact() + "]";
    }

    //TODO this caching is not thread-safe yet
    @Override
    public final boolean test(final RuleMatch m) {

//        if (!allowNullBelief && m.premise.getBelief() == null)
//            return false;

        final PairMatchingProduct tb = m.taskBelief;
        if (!tb.substitutesMayExist(pattern)) {
            return false;
        }

        return subst(m, tb);
    }

    final protected boolean subst(final RuleMatch m, final PairMatchingProduct t) {
        return m.next(pattern, t);
    }

    @Override
    public String toString() {
        return id;
    }


}