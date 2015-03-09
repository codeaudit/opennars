package nars.logic.reason.concept;


import nars.io.Symbols;
import nars.logic.BudgetFunctions;
import nars.logic.TruthFunctions;
import nars.logic.entity.*;
import nars.logic.nal5.Conjunction;
import nars.logic.nal5.Implication;
import nars.logic.reason.ConceptProcess;

import java.util.HashSet;
import java.util.Set;

import static nars.logic.nal7.TemporalRules.ORDER_CONCURRENT;
import static nars.logic.nal7.TemporalRules.ORDER_FORWARD;

/*
 if the premise task is a =/>
    <(&/,<a --> b>,<b --> c>,<x --> y>,pick(a)) =/> <goal --> reached>>.
    (&/,<a --> b>,<b --> c>,<x --> y>). :|:
    |-
    <pick(a) =/> <goal --> reached>>. :|:
*/
public class ForwardImplicationProceed extends ConceptFireTaskTerm {

    /**
     * //new inference rule accelerating decision making: https://groups.google.com/forum/#!topic/open-nars/B8veE-WDd8Q
     */
    public static int PERCEPTION_DECISION_ACCEL_SAMPLES = 1;

    @Override
    public boolean apply(ConceptProcess f, TaskLink taskLink, TermLink termLink) {
        if (!f.nal(7)) return true;

        Concept concept = f.getCurrentBeliefConcept();
        if (concept == null) return true;

        Task taskLinkTask = f.getCurrentTask();// taskLink.getTask();


        if (!(taskLinkTask.sentence.isJudgment() || taskLinkTask.sentence.isGoal())) return true;

        Term taskTerm = taskLinkTask.sentence.term;
        if (!(taskTerm instanceof Implication)) return true;
        Implication imp = (Implication) taskLinkTask.sentence.term;

        if (!((imp.getTemporalOrder() == ORDER_FORWARD || (imp.getTemporalOrder() == ORDER_CONCURRENT))))
            return true;


        if (!(imp.getSubject() instanceof Conjunction)) return true;
        Conjunction conj = (Conjunction) imp.getSubject();


        //the conjunction must be of size > 2 in order for a smaller one to match its beginning subsequence
        if (conj.size() <= 2)
            return true;

        if (!((conj.getTemporalOrder() == ORDER_FORWARD) || (conj.getTemporalOrder() == ORDER_CONCURRENT)))
            return true;

        Set<Term> alreadyInducted = new HashSet();

        for (int i = 0; i < PERCEPTION_DECISION_ACCEL_SAMPLES; i++) {


            Concept next = f.memory.concepts.nextConcept();

            if ((next == null) || (next.equals(concept))) continue;

            final Term t = next.getTerm();
            if (!(t instanceof Conjunction)) continue;

            final Conjunction conj2 = (Conjunction) t;

            if (conj.getTemporalOrder() == conj2.getTemporalOrder() && alreadyInducted.add(t)) {

                Sentence s = null;
                if (taskLinkTask.sentence.punctuation == Symbols.JUDGMENT) {
                    s = next.getBestBelief();
                }
                else if (taskLinkTask.sentence.punctuation == Symbols.GOAL) {
                    s = next.getBestGoal(true, true);
                }
                if (s == null) continue;


                //conj2 conjunction has to be a minor of conj
                //the case where its equal is already handled by other inference rule
                if (conj2.term.length < conj.term.length) {

                    boolean equal = true;

                    //ok now check if it is really a minor (subsequence)
                    for (int j = 0; j < conj2.term.length; j++) {
                        if (!conj.term[j].equals(conj2.term[j])) {
                            equal = false;
                        }
                    }
                    if (!equal) continue;

                    //ok its a minor, we have to construct the residue implication now
                    Term[] residue = new Term[conj.term.length - conj2.term.length];
                    System.arraycopy(conj.term, conj2.term.length + 0, residue, 0, residue.length);

                    Term C = Conjunction.make(residue, conj.getTemporalOrder());
                    Implication resImp = Implication.make(C, imp.getPredicate(), imp.getTemporalOrder());

                    if (resImp == null)
                        continue;

                    //todo add
                    TruthValue truth = TruthFunctions.deduction(s.truth, taskLinkTask.sentence.truth);


                    Task newTask = new Task(
                            new Sentence(resImp, s.punctuation, truth,
                                    f.newStamp(taskLinkTask.sentence, f.memory.time())),
                            new BudgetValue(BudgetFunctions.forward(truth, f)),
                            taskLinkTask
                            );

                    f.deriveTask(newTask, false, false, s, taskLinkTask);

                }
            }
        }


        return true;
    }
}
