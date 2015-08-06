package nars.process;

import nars.Events;
import nars.bag.Bag;
import nars.concept.Concept;
import nars.link.TaskLink;
import nars.link.TermLink;
import nars.link.TermLinkKey;

/**
 * Created by me on 8/5/15.
 */
public class ConceptProcessTaskTermLink extends ConceptProcess {

    protected final TermLink termLink;

    public ConceptProcessTaskTermLink(Concept concept, TaskLink taskLink, TermLink termLink) {
        super(concept, taskLink);

        if (taskLink.type == TermLink.TRANSFORM) {
            throw new RuntimeException("ConceptProcessTaskTermLink must involve a TaskLink non-Transform type");
        }

        this.termLink = termLink;
    }

    /**
     * @return the current termLink aka BeliefLink
     */
    @Override
    public TermLink getTermLink() {
        return termLink;
    }


    @Override
    protected void derive() {

        final Bag<TermLinkKey, TermLink> tl = concept.getTermLinks();
        if (tl != null)
            tl.setForgetNext(memory.param.termLinkForgetDurations, memory);


        getMemory().rules.fire(this);

        emit(Events.BeliefReason.class, this);
    }


    @Override
    protected void beforeFinish(long now) {
        super.beforeFinish(now);

        termLink.setUsed(now);
    }

    /**
     * the current termlink / belieflink's concept
     */
    public Concept getTermLinkConcept() {
        TermLink tl = getTermLink();
        if (tl != null) {
            return concept(getTermLink().getTarget());
        }
        return null;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(getClass().getSimpleName())
                .append("[").append(concept.toString()).append(':').append(taskLink).append(',')
                .append(termLink).append(']')
                .toString();
    }

}