/*
 * Memory.java
 *
 * Copyright (C) 2008  Pei Wang
 *
 * This file is part of Open-NARS.
 *
 * Open-NARS is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Open-NARS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open-NARS.  If not, see <http://www.gnu.org/licenses/>.
 */
package nars.storage;

import com.google.common.util.concurrent.AtomicDouble;
import nars.util.Events;
import nars.util.EventEmitter;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import nars.NAR;
import nars.config.RuntimeParameters;
import nars.config.Parameters;
import nars.util.Events.ResetEnd;
import nars.util.Events.ResetStart;
import nars.util.Events.TaskRemove;
import nars.control.WorkingCycle;
import nars.control.DerivationContext;
import nars.control.ImmediateProcess;
import nars.plugin.mental.Emotions;
import nars.entity.BudgetValue;
import nars.entity.Concept;
import nars.entity.Item;
import nars.entity.Sentence;
import nars.entity.Stamp;
import nars.entity.Task;
import nars.entity.TaskLink;
import nars.entity.TruthValue;
import nars.inference.BudgetFunctions;
import static nars.inference.BudgetFunctions.truthToQuality;
import nars.inference.TemporalRules;
import nars.io.Output.IN;
import nars.io.Output.OUT;
import nars.io.Symbols;
import nars.language.Conjunction;
import nars.language.Tense;
import nars.language.Term;
import static nars.language.Terms.equalSubTermsInRespectToImageAndProduct;
import nars.operator.Operation;
import nars.operator.Operator;
import nars.io.Echo;
import nars.io.PauseInput;
import nars.io.Reset;
import nars.io.SetDecisionThreshold;
import nars.io.SetVolume;
import nars.language.CompoundTerm;
import nars.language.Interval;


/**
 * Memory consists of the run-time state of a NAR, including:
 *   * term and concept memory
 *   * clock
 *   * reasoner state
 *   * etc.
 * 
 * Excluding input/output channels which are managed by a NAR.  
 * 
 * A memory is controlled by zero or one NAR's at a given time.
 * 
 * Memory is serializable so it can be persisted and transported.
 */
public class Memory implements Serializable {
    
    //emotion meter keeping track of global emotion
    public final Emotions emotion = new Emotions();   
    
    public boolean allowExecution = true;
    public Task lastDecision = null;
    private long timeRealStart;
    private long timeRealNow;
    private long timePreviousCycle;
    private long timeSimulation;
    
    public static long randomSeed = 1;
    public static Random randomNumber = new Random(randomSeed);
    public static void resetStatic() {
        randomNumber.setSeed(randomSeed);    
    }
    
    //todo make sense of this class and de-obfuscate
    public final WorkingCycle concepts;
    public final EventEmitter event;
    
    /* InnateOperator registry. Containing all registered operators of the system */
    public final HashMap<CharSequence, Operator> operators;
    
    /* New tasks with novel composed terms, for delayed and selective processing*/
    public final Bag<Task<Term>,Sentence<Term>> novelTasks;
    
    /* Input event tasks that were either input events or derived sequences*/
    public Bag<Task<Term>,Sentence<Term>> sequenceTasks;

    /* List of new tasks accumulated in one cycle, to be processed in the next cycle */
    public final Deque<Task> newTasks;
    
    /* The remaining number of steps to be carried out (stepLater mode)*/
    private int inputPausedUntil;
    
    /* System clock, relatively defined to guarantee the repeatability of behaviors */
    private long cycle;
    
    /* System parameters that can be changed at runtime */
    public final RuntimeParameters param;
    
    //index of Conjunction questions
    transient private Set<Task> questionsConjunction = new HashSet();

    
    /* ---------- Constructor ---------- */
    /**
     * Create a new memory
     *
     * @param initialOperators - initial set of available operators; more may be added during runtime
     */
    public Memory(RuntimeParameters param, WorkingCycle concepts, Bag<Task<Term>,Sentence<Term>> novelTasks,
            Bag<Task<Term>,Sentence<Term>> sequenceTasks) {                

        this.param = param;
        this.event = new EventEmitter();
        this.concepts = concepts;
        this.concepts.init(this);
        this.novelTasks = novelTasks;                
        this.newTasks = new ArrayDeque<>();
        this.sequenceTasks = sequenceTasks;
        this.operators = new HashMap<>();
        reset();
    }
    
    public void reset() {
        event.emit(ResetStart.class);
        concepts.reset();
        novelTasks.clear();
        newTasks.clear();    
        cycle = 0;
        timeRealStart = timeRealNow = System.currentTimeMillis();
        timePreviousCycle = time();
        inputPausedUntil = 0;
        emotion.set(0.5f, 0.5f);
        resetStatic();
        event.emit(ResetEnd.class);
    }

    public long time() {
        return getCycleTime();
    }
    
    public int getDuration() {
        return param.duration.get();
    }
    
    /** internal, subjective time (inference steps) */
    public long getCycleTime() {
        return cycle;
    }
    
    /** difference in time since last cycle */
    public long getTimeDelta() {
        return time() - timePreviousCycle;
    }

    public Deque<Task> getNewTasks() {
        return newTasks;
    }
    

    /* ---------- conversion utilities ---------- */
    /**
     * Get an existing Concept for a given name
     * <p>
     * called from Term and ConceptWindow.
     *
     * @param t the name of a concept
     * @return a Concept or null
     */
    public Concept concept(final Term t) {
        return concepts.concept(t);
    }

    /**
     * Get the Concept associated to a Term, or create it.
     * 
     *   Existing concept: apply tasklink activation (remove from bag, adjust budget, reinsert)
     *   New concept: set initial activation, insert
     *   Subconcept: extract from cache, apply activation, insert
     * 
     * If failed to insert as a result of null bag, returns null
     *
     * A displaced Concept resulting from insert is forgotten (but may be stored in optional  subconcept memory
     * 
     * @param term indicating the concept
     * @return an existing Concept, or a new one, or null 
     */
    public Concept conceptualize(final BudgetValue budget, final Term term) {
        boolean createIfMissing = true;
        
        /*Concept c = concept(term);
        if (c!=null)
            System.out.print(c.budget + "   ");
        System.out.println(term + " conceptualize: " + budget);*/
                
        return concepts.conceptualize(budget, term, createIfMissing);
    }

    /**
     * Get the current activation level of a concept.
     *
     * @param t The Term naming a concept
     * @return the priority value of the concept
     */
    public float conceptActivation(final Term t) {
        final Concept c = concept(t);
        return (c == null) ? 0f : c.getPriority();
    }
    
    /** 
     * this will not remove a concept.  it is not good to use directly because it can disrupt 
     * the bag's priority order. it should only be used after it has been removed then before inserted */
    public void forget(final Item x, final float forgetCycles, final float relativeThreshold) {
        BudgetFunctions.forgetIterative(x.budget, forgetCycles, relativeThreshold);
        /*switch (param.forgetting) {
            case Iterative:                
                BudgetFunctions.forgetIterative(x.budget, forgetCycles, relativeThreshold);
                break;
            case Periodic:
                BudgetFunctions.forgetPeriodic(x.budget, forgetCycles, relativeThreshold, time());
                break;
        }*/
    }    
    
    /* ---------- new task entries ---------- */
    /**
     * add new task that waits to be processed in the next cycleMemory
     */
    public void addNewTask(final Task t, final String reason) {
        newTasks.add(t);
      //  logic.TASK_ADD_NEW.commit(t.getPriority());
        emit(Events.TaskAdd.class, t, reason);
        output(t);
    }
    
    /* There are several types of new tasks, all added into the
     newTasks list, to be processed in the next cycleMemory.
     Some of them are reported and/or logged. */
    /**
     * Input task processing. Invoked by the outside or inside environment.
 Outside: StringParser (addInput); Inside: InnateOperator (feedback). Input
 tasks with low priority are ignored, and the others are put into task
 buffer.
     *
     * @param t The addInput task
     */
    
    boolean checked=false;
    boolean isjUnit=false;
    public static boolean isJUnitTest() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        List<StackTraceElement> list = Arrays.asList(stackTrace);
        for (StackTraceElement element : list) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }           
        }
        return false;
    }
    
    public void inputTask(final Item t) {
        if(!checked) {
            checked=true;
            isjUnit=isJUnitTest();
        }
        if (t instanceof Task) {
            Task task = (Task)t;
            Stamp s = task.sentence.stamp;                        
            if (s.getCreationTime()==-1)
                s.setCreationTime(time(), param.duration.get());

            emit(IN.class, task);

            if (task.budget.aboveThreshold()) {
                
                addNewTask(task, "Perceived");
                
            } else {
                removeTask(task, "Neglected");
            }
        }
        else if (t instanceof PauseInput) {            
            stepLater(((PauseInput)t).cycles);            
            emit(IN.class, t);
        }
        else if (t instanceof Reset) {
            reset();
            emit(OUT.class,((Reset) t).input);
            emit(IN.class, t);
        }
        else if (t instanceof Echo) {
            Echo e = (Echo)t;
            if(!isjUnit) {
                emit(OUT.class,((Echo) t).signal);
            }
            emit(e.channel, e.signal);
        }
        else if (t instanceof SetVolume) {            
            param.noiseLevel.set(((SetVolume)t).volume);
            emit(IN.class, t);
        } 
        else if (t instanceof SetDecisionThreshold) {
            param.decisionThreshold.set(((SetDecisionThreshold)t).volume);
            emit(IN.class, t);
        } 
        else {
            emit(IN.class, "Unrecognized Input Task: " + t);
        }
    }



    public void removeTask(final Task task, final String reason) {        
        emit(TaskRemove.class, task, reason);
        task.end();        
    }
    
    /**
     * ExecutedTask called in Operator.call
     *
     * @param operation The operation just executed
     */
    public void executedTask(final Operation operation, TruthValue truth) {
        Task opTask = operation.getTask();
       // logic.TASK_EXECUTED.commit(opTask.budget.getPriority());
                
        Stamp stamp = new Stamp(this, Tense.Present); 
        Sentence sentence = new Sentence(operation, Symbols.JUDGMENT_MARK, truth, stamp);
        
        Task task = new Task(sentence, new BudgetValue(Parameters.DEFAULT_FEEDBACK_PRIORITY, Parameters.DEFAULT_FEEDBACK_DURABILITY,
                                        truthToQuality(sentence.getTruth())), operation.getTask());
        task.setElemOfSequenceBuffer(true);
        addNewTask(task, "Executed");
    }

    public void output(final Task t) {
        
        final float budget = t.budget.summary();
        final float noiseLevel = 1.0f - (param.noiseLevel.get() / 100.0f);
        
        if (budget >= noiseLevel) {  // only report significant derived Tasks
            emit(OUT.class, t);
        }        
    }
    
    final public void emit(final Class c, final Object... signal) {        
        event.emit(c, signal);
    }

    final public boolean emitting(final Class channel) {
        return event.isActive(channel);
    }

    public void cycle(final NAR inputs) {
    
        event.emit(Events.CycleStart.class);                
        
        /** adds input tasks to newTasks */
        for(int i=0; i<1 && isProcessingInput(); i++) {
            Item t = inputs.nextTask();                    
            if (t!=null) 
                inputTask(t);            
        }
      
        concepts.cycle();         
        
        event.emit(Events.CycleEnd.class);
        event.synch();
        
        timePreviousCycle = time();
        cycle++;
        timeRealNow = System.currentTimeMillis();
    }
   
    /**
     * Process the newTasks accumulated in the previous workCycle, accept input
     * ones and those that corresponding to existing concepts, plus one from the
     * buffer.
     */
    public void processNewTasks() {
        Task task;
        int counter = newTasks.size();  // don't include new tasks produced in the current workCycle
        while (counter-- > 0) {
            task = newTasks.removeFirst();
            boolean enterDirect = true;
            if (/*task.isElemOfSequenceBuffer() || task.isObservablePrediction() || */ enterDirect ||  task.isInput() || task.sentence.isQuest() || task.sentence.isQuestion() || concept(task.sentence.term)!=null) { // new input or existing concept
                new ImmediateProcess(this, task).run(); 
            } else {
                Sentence s = task.sentence;
                if (s.isJudgment() || s.isGoal()) {
                    double d = s.getTruth().getExpectation();
                    if (s.isJudgment() && d > Parameters.DEFAULT_CREATION_EXPECTATION) {
                        novelTasks.putIn(task);    // new concept formation
                    } else 
                    if(s.isGoal() && d > Parameters.DEFAULT_CREATION_EXPECTATION_GOAL) {
                        novelTasks.putIn(task);    // new concept formation
                    }
                    else
                    {
                        removeTask(task, "Neglected");
                    }
                }
            }
        }
    }
    

    /**
     * Select a novel task to process.
     * @return whether a task was processed
     */
    public void processNovelTask() {
        final Task task = novelTasks.takeNext();
        if (task != null) {            
                new ImmediateProcess(this, task).run();
        }
    }

     public Operator getOperator(final String op) {
        return operators.get(op);
     }
     
     public Operator addOperator(final Operator op) {
         operators.put(op.name(), op);
         return op;
     }
     
     public Operator removeOperator(final Operator op) {
         return operators.remove(op.name());
     }

    private long currentStampSerial = 0;
    public long newStampSerial() {
        return currentStampSerial++;
    }

    public boolean isProcessingInput() {
        return time() >= inputPausedUntil;
    }
    
    /**
     * Queue additional cycle()'s to the inference process.
     *
     * @param cycles The number of inference steps
     */
    public void stepLater(final int cycles) {
        inputPausedUntil = (int) (time() + cycles);
    }    
    
    /** get all tasks in the system by iterating all newTasks, novelTasks, Concept TaskLinks */
    public Set<Task> getTasks(boolean includeTaskLinks, boolean includeNewTasks, boolean includeNovelTasks) {
        
        Set<Task> t = new HashSet();
        
        if (includeTaskLinks) {
            for (Concept c : concepts) {
                for (TaskLink tl : c.taskLinks) {
                    t.add(tl.targetTask);
                }
            }
        }
        
        if (includeNewTasks)
            t.addAll(newTasks);
        
        if (includeNovelTasks)
            for (Task n : novelTasks)
                t.add(n);
            
        return t;        
    }

    public Task newTask(Term content, char sentenceType, float freq, float conf, float priority, float durability) {
        return newTask(content, sentenceType, freq, conf, priority, durability, (Task)null);
    }
            
            
    public Task newTask(Term content, char sentenceType, float freq, float conf, float priority, float durability, final Task parentTask) {
        return newTask(content, sentenceType, freq, conf, priority, durability, parentTask, Tense.Present);
    }
    
    /** convenience method for forming a new Task from a term */
    public Task newTask(Term content, char sentenceType, float freq, float conf, float priority, float durability, Tense tense) {
        return newTask(content, sentenceType, freq, conf, priority, durability, null, tense);
    }
    
    /** convenience method for forming a new Task from a term */
    public Task newTask(Term content, char sentenceType, float freq, float conf, float priority, float durability, Task parentTask, Tense tense) {
        
        TruthValue truth = new TruthValue(freq, conf);
        Sentence sentence = new Sentence(
                content, 
                sentenceType, 
                truth, 
                new Stamp(this, tense));
        BudgetValue budget = new BudgetValue(Parameters.DEFAULT_JUDGMENT_PRIORITY, Parameters.DEFAULT_JUDGMENT_DURABILITY, truth);
        Task task = new Task(sentence, budget, parentTask);
        return task;
    }
    
    public Collection<Task> conceptQuestions(Class c) {
        if (c == Conjunction.class) {
            return questionsConjunction;
        }
        throw new RuntimeException("Questions index for " + c + " does not exist");
    }
    
    //TODO put probably in extra class involved for event chaining?
    //public final ArrayDeque<Task> stm = new ArrayDeque();
    //is input or by the system triggered operation
    public static boolean isInputOrOperation(final Task newEvent) {
        return newEvent.isInput() || (newEvent.sentence.term instanceof Operation);
    }
    
    public boolean proceedWithTemporalInduction(final Sentence newEvent, final Sentence stmLast, Task controllerTask, DerivationContext nal, boolean SucceedingEventsInduction) {
        
        if(SucceedingEventsInduction && !controllerTask.isElemOfSequenceBuffer()) { //todo refine, add directbool in task
            return false;
        }
        if (newEvent.isEternal() || !isInputOrOperation(controllerTask)) {
            return false;
        }
        if (equalSubTermsInRespectToImageAndProduct(newEvent.term, stmLast.term)) {
            return false;
        }
        
        if(newEvent.punctuation!=Symbols.JUDGMENT_MARK || stmLast.punctuation!=Symbols.JUDGMENT_MARK)
            return false; //temporal inductions for judgements only
        
        nal.setTheNewStamp(newEvent.stamp, stmLast.stamp, time());
        nal.setCurrentTask(controllerTask);

        Sentence previousBelief = stmLast;
        nal.setCurrentBelief(previousBelief);

        Sentence currentBelief = newEvent;

        //if(newEvent.getPriority()>Parameters.TEMPORAL_INDUCTION_MIN_PRIORITY)
        TemporalRules.temporalInduction(currentBelief, previousBelief, nal, SucceedingEventsInduction);
        return false;
    }

    public boolean eventInference(final Task newEvent, DerivationContext nal) {

        if(newEvent.getTerm() == null || newEvent.budget==null || !newEvent.isElemOfSequenceBuffer()) { //todo refine, add directbool in task
            return false;
       }

        nal.emit(Events.InduceSucceedingEvent.class, newEvent, nal);

        if (!newEvent.sentence.isJudgment() || newEvent.sentence.isEternal() || !isInputOrOperation(newEvent)) {
            return false;
       }

        if(Parameters.TEMPORAL_INDUCTION_ON_SUCCEEDING_EVENTS) {
            /*for (Task stmLast : stm) {
                Concept OldConc = this.concept(stmLast.getTerm());
                if(OldConc != null)
                {
                    TermLink template = new TermLink(newEvent.getTerm(), TermLink.TEMPORAL);
                    if(OldConc.termLinkTemplates == null)
                        OldConc.termLinkTemplates = new ArrayList<>();
                    OldConc.termLinkTemplates.add(template);
                    OldConc.buildTermLinks(newEvent.getBudget()); //will be built bidirectionally anyway
                }
            }*/
            
            //also attempt direct
            for(int i =0 ;i<Math.min(this.sequenceTasks.size(), Parameters.SEQUENCE_BAG_ATTEMPTS);i++) {
                Task takeout = this.sequenceTasks.takeNext();
                proceedWithTemporalInduction(newEvent.sentence, takeout.sentence, newEvent, nal, true);
                this.sequenceTasks.putBack(takeout, cycles(this.param.sequenceForgetDurations), this);
            }
            //for (Task stmLast : stm) {
               // proceedWithTemporalInduction(newEvent.sentence, stmLast.sentence, newEvent, nal, true);
            //}
        }
        
        addToSequenceTasks(newEvent);
        
        /*System.out.println("----------");
        for(Task t : this.sequenceTasks) {
            System.out.println(t.sentence.getTerm().toString()+ " " +String.valueOf(t.getPriority()));
        }
        System.out.println("^^^^^^^^^");*/
        return true;
    }

    public void addToSequenceTasks(final Task newEvent) {

        float periority_penalty = 1.0f;
        if(newEvent.getTerm() instanceof Conjunction) {
            Conjunction term = ((Conjunction)newEvent.getTerm());
            if(!(term.term[term.term.length-1] instanceof Operation)) {
                periority_penalty *= 0.5f;
            }
            if(term.term[0] instanceof Operation) { //also try to start with a condition
                periority_penalty *= 0.5f;
            }
        }

        //multiple versions are necessary, but we do not allow duplicates
        Task removal = null;
        do
        {
            removal = null;
            for(Task s : this.sequenceTasks) {
                if(CompoundTerm.cloneDeepReplaceIntervals(s.getTerm()).equals(
                        CompoundTerm.cloneDeepReplaceIntervals(newEvent.getTerm()))) {
                        // && //-- new outcommented
                        //s.sentence.stamp.equals(newEvent.sentence.stamp,false,true,true,false) ) {
                    //&& newEvent.sentence.getOccurenceTime()>s.sentence.getOccurenceTime() ) { 
                    removal = s;
                    break;
                }
            }
            if(removal != null) {
                this.sequenceTasks.take(removal);
            }
        }
        while(removal != null);
        //ok now add the new one:
        //making sure we do not mess with budget of the task:
        Task t2 = new Task(newEvent.sentence, new BudgetValue(0.9f*periority_penalty,0.1f,0.1f), newEvent.getParentTask(), newEvent.getParentBelief(), newEvent.getBestSolution());
        //we use a event default budget here so the time it appeared and whether it was selected is key criteria currently divided by complexity
        this.sequenceTasks.putIn(t2);

        //debug:
        /*System.out.println("---------");
        for(Task t : this.sequenceTasks) {
            System.out.println(t.getTerm().toString());
        }
        System.out.println("^^^^^^");*/
    }
    
    /** converts durations to cycles */
    public final float cycles(AtomicDouble durations) {
        return param.duration.floatValue() * durations.floatValue();
    }
    
   
}
