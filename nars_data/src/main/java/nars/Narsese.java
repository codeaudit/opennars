package nars;

import com.github.fge.grappa.Grappa;
import com.github.fge.grappa.annotations.Cached;
import com.github.fge.grappa.matchers.MatcherType;
import com.github.fge.grappa.matchers.base.AbstractMatcher;
import com.github.fge.grappa.parsers.BaseParser;
import com.github.fge.grappa.rules.Rule;
import com.github.fge.grappa.run.ListeningParseRunner3;
import com.github.fge.grappa.run.ParseRunner;
import com.github.fge.grappa.run.ParsingResult;
import com.github.fge.grappa.run.context.MatcherContext;
import com.github.fge.grappa.stack.DefaultValueStack;
import com.github.fge.grappa.stack.ValueStack;
import com.github.fge.grappa.support.Var;
import nars.term.Compounds;
import nars.term.Term;
import nars.term.atom.Atom;
import nars.term.compound.Compound;
import nars.term.match.*;
import nars.term.nal7.Tense;
import nars.term.op.Operator;
import nars.term.variable.Variable;
import nars.truth.DefaultTruth;
import nars.truth.Truth;
import nars.util.Texts;
import nars.util.data.list.FasterList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static nars.Op.*;
import static nars.Symbols.*;

/**
 * NARese, syntax and language for interacting with a NAR in NARS.
 * https://code.google.com/p/open-nars/wiki/InputOutputFormat
 */
public class Narsese extends BaseParser<Object>  {


    //These should be set to something like RecoveringParseRunner for performance
    public final ParseRunner inputParser = new ListeningParseRunner3(Input());
    private final ParseRunner singleTaskParser = new ListeningParseRunner3(Task());
    private final ParseRunner singleTermParser = new ListeningParseRunner3(Term());
    //private final ParseRunner singleTaskRuleParser = new ListeningParseRunner3(TaskRule());



    static final ThreadLocal<Narsese> parsers = ThreadLocal.withInitial(() -> Grappa.createParser(Narsese.class) );

    public static final Narsese the() {
        return parsers.get();
    }


    public Rule Input() {
        return sequence(
            zeroOrMore( //1 or more?
                    //sequence(
                            firstOf(
                                    LineComment(),
                                    Task()
                            ),
                            s()
                    //)
            ), eof() ) ;
    }

    /**  {Premise1,Premise2} |- Conclusion. */
    public Rule TaskRule() {

        //use a var to count how many rule conditions so that they can be pulled off the stack without reallocating an arraylist
        return sequence(
                STATEMENT_OPENER, s(),
                push(premiseRuleMarker),

                Term(), //cause

                zeroOrMore( sepArgSep(), Term() ),
                s(), TASK_RULE_FWD, s(),

                push(premiseRuleMarker), //stack marker

                Term(), //effect

                zeroOrMore( sepArgSep(), Term() ),
                s(), STATEMENT_CLOSER,

                push(popTaskRule())
        );
    }

    //TODO do this without stack markers
    @Deprecated private final Object premiseRuleMarker = new Object();


    /** the result of this is passed as the term parameter to PremiseRule instance */
    public Compound popTaskRule() {
        //(Term)pop(), (Term)pop()

        List<Term> r = Global.newArrayList(1);
        List<Term> l = Global.newArrayList(1);

        Object popped;
        while ( (popped = pop()) != premiseRuleMarker) { //lets go back till to the start now
            r.add((Term)popped);
        }

        while ( (popped = pop()) != premiseRuleMarker) {
            l.add((Term)popped);
        }

        Collections.reverse(l);
        Collections.reverse(r);

        Compound premise;
        if (l.size() >= 1) {
            premise = $.p(l);
        }
        else {
            //empty premise list is invalid
            return null;
        }

        Compound conclusion;
        if (r.size() >= 1) {
            conclusion = $.p(r);
        }
        else {
            //empty premise list is invalid
            return null;
        }

        return $.p /*PremiseRule*/(premise, conclusion);
    }

    public Rule LineComment() {
        return sequence(
                s(),
                //firstOf(
                        "//",
                        //"'",
                        //sequence("***", zeroOrMore('*')), //temporary
                        //"OUT:"
                //),
                //sNonNewLine(),
                LineCommentEchoed(),
                firstOf("\n",eof() /* may not have newline at end of file */)
        );
    }

    public Rule LineCommentEchoed() {
        return sequence(


                zeroOrMore(noneOf("\n")),

                push( $.oper(Operator.the("echo"), (Term)Atom.the(match())  ) ) );
    }

//    public Rule PauseInput() {
//        return sequence( s(), IntegerNonNegative(),
//                push( PauseInput.pause( (Integer) pop() ) ), sNonNewLine(),
//                "\n" );
//    }


//    public Rule TermEOF() {
//        return sequence( s(), Term(), s(), eof() );
//    }
//    public Rule TaskEOF() {
//        return sequence( s(), Task(), s(), eof() );
//    }

    public Rule Task() {

        Var<float[]> budget = new Var();
        Var<Character> punc = new Var();
        Var<Term> term = new Var();
        Var<Truth> truth = new Var();
        Var<Tense> tense = new Var(Tense.Eternal);

        return sequence(
                s(),

                optional( Budget(budget) ),


                Term(true,false),
                term.set((Term) pop()),

                SentencePunctuation(punc),

                optional(
                        s(), Tense(tense)
                ),

                optional(
                        s(), Truth(truth, tense)

                ),

                push(new Object[] { budget.get(), term.get(), punc.get(), truth.get(), tense.get() } )
                //push(getTask(budget, term, punc, truth, tense))

        );
    }


    Rule Budget(Var<float[]> budget) {
        return sequence(
                BUDGET_VALUE_MARK,

                ShortFloat(),

                firstOf(
                        BudgetPriorityDurabilityQuality(budget),
                        BudgetPriorityDurability(budget),
                        BudgetPriority(budget)
                ),

                optional(BUDGET_VALUE_MARK)
        );
    }

    boolean BudgetPriority(Var<float[]> budget) {
        return budget.set(new float[]{(float) pop()});
    }

    Rule BudgetPriorityDurability(Var<float[]> budget) {
        return sequence(
                VALUE_SEPARATOR, ShortFloat(),
                budget.set(new float[]{(float) pop(), (float) pop()}) //intermediate representation
        );
    }

    Rule BudgetPriorityDurabilityQuality(Var<float[]> budget) {
        return sequence(
                VALUE_SEPARATOR, ShortFloat(), VALUE_SEPARATOR, ShortFloat(),
                budget.set(new float[]{(float) pop(), (float) pop(), (float) pop()}) //intermediate representation
        );
    }

    Rule Tense(Var<Tense> tense) {
        return firstOf(
            sequence(TENSE_PRESENT, tense.set(Tense.Present)),
            sequence(TENSE_PAST, tense.set(Tense.Past)),
            sequence(TENSE_FUTURE, tense.set(Tense.Future))
        );
    }

    Rule Truth(Var<Truth> truth, Var<Tense> tense) {
        return sequence(

                TRUTH_VALUE_MARK,

                //Frequency
                ShortFloat(),

                firstOf(

                        sequence(

                                TruthTenseSeparator(VALUE_SEPARATOR, tense), // separating ;,|,/,\

                                ShortFloat(),

                                swap() && truth.set(new DefaultTruth((float) pop(), (float) pop())),

                                optional(TRUTH_VALUE_MARK) //tailing '%' is optional
                        ),

                        sequence(

                                truth.set(new DefaultTruth((float) pop(), Float.NaN /* will be set automatically by Memory */)),

                                optional(TruthTenseSeparator(TRUTH_VALUE_MARK, tense)) ////tailing %,|,/,\ is optional
                        )
                )

        );
    }

    Rule TruthTenseSeparator(char defaultChar, Var<Tense> tense) {
        return firstOf(
                defaultChar,
                sequence('|', tense.set(Tense.Present)),
                sequence('\\', tense.set(Tense.Past)),
                sequence('/', tense.set(Tense.Future))
        );
    }


    Rule ShortFloat() {
        return sequence(
                sequence(
                        optional(digit()),
                        optional('.', oneOrMore(digit()))
                ),
                push(Texts.f(matchOrDefault("NaN"), 0, 1.0f))
        );
    }


//    Rule IntegerNonNegative() {
//        return sequence(
//                oneOrMore(digit()),
//                push(Integer.parseInt(matchOrDefault("NaN")))
//        );
//    }

//    Rule Number() {
//
//        return sequence(
//                sequence(
//                        optional('-'),
//                        oneOrMore(digit()),
//                        optional('.', oneOrMore(digit()))
//                ),
//                push(Float.parseFloat(matchOrDefault("NaN")))
//        );
//    }

    Rule SentencePunctuation(Var<Character> punc) {
        return sequence(anyOf(".?!@;"), punc.set(matchedChar()));
    }



    public Rule Term() {
        return Term(true,true);
    }

//    Rule nothing() {
//        return new NothingMatcher();
//    }




    @Cached Rule Term(boolean oper, boolean meta) {
        /*
                 <term> ::= <word>                             // an atomic constant term
                        | <variable>                         // an atomic variable term
                        | <compound-term>                    // a term with internal structure
                        | <statement>                        // a statement can serve as a term
        */

        return seq(
                s(),
                firstOf(
                        QuotedMultilineLiteral(),
                        QuotedLiteral(),

                        Operator(),

                        Interval(),

                        seq(meta, Ellipsis() ),
                        seq(meta, EllipsisShim() ),

                        seq(meta, TaskRule() ),

                        seq(oper, ColonReverseInheritance() ),


                        //Functional form of an Operation, ex: operate(p1,p2), TODO move to FunctionalOperationTerm() rule
                        seq(oper,

                            Term(false,false),

                            COMPOUND_TERM_OPENER,

                            firstOf(

                                //empty operator parens
                                sequence( s(), COMPOUND_TERM_CLOSER,  push(popTerm(Op.OPERATOR, false)) ),

                                MultiArgTerm(Op.OPERATOR, COMPOUND_TERM_CLOSER, false, false, false, true)
                            )
                        ),

                        seq( STATEMENT_OPENER,
                            MultiArgTerm(null, STATEMENT_CLOSER, false, true, true, false)
                        ),

                        Variable(),

//                        //negation shorthand
                        seq(Op.NEGATE.str, s(), Term(), push(
                            //Negation.make(popTerm(null, true)))),
                            $.neg(Atom.the(pop())))),

                        seq(
                            Op.SET_EXT_OPENER.str,
                            MultiArgTerm(Op.SET_EXT_OPENER, SET_EXT_CLOSER)
                        ),

                        seq(
                            Op.SET_INT_OPENER.str,
                            MultiArgTerm(Op.SET_INT_OPENER, SET_INT_CLOSER)
                        ),

                        seq( COMPOUND_TERM_OPENER,
                                firstOf(
                                        //empty product
                                        seq( s(), COMPOUND_TERM_CLOSER, push(Compounds.Empty) ),

                                        MultiArgTerm(null, COMPOUND_TERM_CLOSER, true, false, false, false),

                                        //default to product if no operator specified in ( )
                                        MultiArgTerm(Op.PRODUCT, COMPOUND_TERM_CLOSER, false, false, false, false),

                                        MultiArgTerm(null, COMPOUND_TERM_CLOSER, false, true, true, false)
                                )
                        ),

                        NumberAtom(),
                        Atom()

                ),

                push(the(pop())),

                s()
        );
    }

    public Rule seq(Object rule, Object rule2,
                    Object... moreRules) {
        return sequence(rule, rule2, moreRules);
    }


//    Rule EmptyProduct() {
//        return sequence(
//            COMPOUND_TERM_OPENER, s(), COMPOUND_TERM_CLOSER, push(Compounds.Empty)
//        );
//    }


    Rule Operator() {
        return sequence(OPERATOR.ch, Term(false,false),
                push(new Operator((Term)pop())));
    }

    //final static String invalidAtomCharacters = " ,.!?" + INTERVAL_PREFIX_OLD + "<>-=*|&()<>[]{}%#$@\'\"\t\n";

    /**
     * an atomic term, returns a String because the result may be used as a Variable name
     */
    Rule Atom() {
        return sequence(
                ValidAtomCharMatcher.the,
                push(match())
        );
    }

    Rule NumberAtom() {
        return sequence(
            sequence(
                    optional('-'),
                    oneOrMore(digit()),
                    optional('.', oneOrMore(digit()))
            ),
            push(Atom.the(Float.parseFloat(matchOrDefault("NaN"))))
        );
    }




    public static final class ValidAtomCharMatcher extends AbstractMatcher
    {

        public static final ValidAtomCharMatcher the = new ValidAtomCharMatcher();

        protected ValidAtomCharMatcher() {
            super("'ValidAtomChar'");
        }

        @Override
        public MatcherType getType()
        {
            return MatcherType.TERMINAL;
        }

        @Override
        public <V> boolean match(MatcherContext<V> context) {
            int count = 0;
            int max= context.getInputBuffer().length() - context.getCurrentIndex();

            while (count < max && isValidAtomChar(context.getCurrentChar())) {
                context.advanceIndex(1);
                count++;
            }

            return count > 0;
        }
    }

    public static boolean isValidAtomChar(char c) {
        int x = (int)c;

        //TODO replace these with Symbols. constants
        switch(x) {
            case ' ':
            case Symbols.ARGUMENT_SEPARATOR:
            case Symbols.JUDGMENT:
            case Symbols.GOAL:
            case Symbols.QUESTION:
            case Symbols.QUEST:
            case '\"':
            case '^':
            case '<':
            case '>':

            case '~':
            case '=':

            case '+':
            case '-':
            case '*':

            case '|':
            case '&':
            case '(':
            case ')':
            case '[':
            case ']':
            case '{':
            case '}':
            case '%':
            case '#':
            case '$':
            case ':':
            case '`':
            case '\'':
            case '\t':
            case '\n':
                return false;
        }
        return true;
    }


    /**
     * MACRO: y:x    becomes    <x --> y>
     */
    Rule ColonReverseInheritance() {
        return sequence(
            Term(false,true), s(), ':', s(), Term(),
            push($.inh( (Term)pop(), (Term)pop() ) )
        );
    }

//    /**
//     * MACRO: y`x    becomes    <{x} --> y>
//     */
//    Rule BacktickReverseInstance() {
//        return sequence(
//                Atom(), s(), '`', s(), Term(false),
//                push(Instance.make((Term)(pop()), Atom.the(pop())))
//        );
//    }
//

//    /** creates a parser that is not associated with a memory; it will not parse any operator terms (which are registered with a Memory instance) */
//    public static NarseseParser newParser() {
//        return newParser((Memory)null);
//    }
//
//    public static NarseseParser newMetaParser() {
//        return newParser((Memory)null);
//    }


    Rule QuotedLiteral() {
        return sequence(dquote(), AnyString(), push('\"' + match() + '\"'), dquote());
    }

    Rule QuotedMultilineLiteral() {
        return sequence(
                TripleQuote(), //dquote(), dquote(), dquote()),
                AnyString(), push('\"' + match() + '\"'),
                TripleQuote() //dquote(), dquote(), dquote()
        );
    }

    Rule TripleQuote() {
        return string("\"\"\"");
    }

    Rule Ellipsis() {
        return sequence(
                Variable(), "..",
                firstOf(

                    seq( Term(false,false), "=", Term(false,false), "..+",
                        swap(3),
                            push( new EllipsisTransform(
                            (Variable)pop(), (Term)pop(), (Term)pop()  ))
                        ),
                    seq( "+",
                            push( new EllipsisOneOrMore( (Variable)pop() ) )
                    ),
                    seq( "*",
                            push( new EllipsisZeroOrMore( (Variable)pop() ) )
                    )
                )
        );
    }

    Rule EllipsisShim() {
        return sequence(
                "..",
                push( Ellipsis.Shim)
        );
    }

    Rule AnyString() {
        //TODO handle \" escape
        return oneOrMore(noneOf("\""));
    }

//    Rule AnyAlphas() {
//        //TODO handle \" escape
//        return sequence( alpha(), push(matchedChar()), zeroOrMore( alphanumeric() ), push(match()),
//                swap(),
//                push( pop().toString() + pop().toString()));
//    }

    //Rule alphanumeric() { return firstOf(alpha(), digit()); }



//    @Deprecated Rule IntervalLog() {
//        return sequence(INTERVAL_PREFIX_OLD, sequence(oneOrMore(digit()), push(match()),
//                //push(Interval.interval(-1 + Texts.i((String) pop())))
//                push(CyclesInterval.intervalLog(-1 + Texts.i((String) pop())))
//        ));
//    }

    Rule Interval() {
        return sequence(INTERVAL_PREFIX, sequence(oneOrMore(digit()), push(match()),
                push($.cycles(Texts.i((String) pop())))
        ));
    }



    Rule Variable() {
        /*
           <variable> ::= "$"<word>                          // independent variable
                        | "#"[<word>]                        // dependent variable
                        | "?"[<word>]                        // query variable in question
                        | "%"[<word>]                        // pattern variable in rule
        */
        return firstOf(
            sequence( Symbols.VAR_INDEPENDENT, Atom(), push( new Variable.VarIndep((String)pop()))) ,
            sequence( Symbols.VAR_DEPENDENT, Atom(), push( new Variable.VarDep((String)pop()))) ,
            sequence( Symbols.VAR_QUERY, Atom(), push( new Variable.VarQuery((String)pop()))) ,
            sequence( Symbols.VAR_PATTERN, Atom(), push( new VarPattern((String)pop())))
//                anyOf(variables),
//                push(match().charAt(0)), Atom(), swap(),
//                    push($.v((char)pop(), (String) pop())
//                )
        );
    }

    //Rule CompoundTerm() {
        /*
         <compound-term> ::= "{" <term> {","<term>} "}"         // extensional set
                        | "[" <term> {","<term>} "]"         // intensional set
                        | "(&," <term> {","<term>} ")"       // extensional intersection
                        | "(|," <term> {","<term>} ")"       // intensional intersection
                        | "(*," <term> {","<term>} ")"       // product
                        | "(/," <term> {","<term>} ")"       // extensional image
                        | "(\," <term> {","<term>} ")"       // intensional image
                        | "(||," <term> {","<term>} ")"      // disjunction
                        | "(&&," <term> {","<term>} ")"      // conjunction
                        | "(&/," <term> {","<term>} ")"      // (sequential events)
                        | "(&|," <term> {","<term>} ")"      // (parallel events)
                        | "(--," <term> ")"                  // negation
                        | "(-," <term> "," <term> ")"        // extensional difference
                        | "(~," <term> "," <term> ")"        // intensional difference
        
        */

    //}

    Rule Op() {
        return sequence(
                trie(
                        INTERSECT_EXT.str, INTERSECT_INT.str,
                        DIFF_EXT.str, DIFF_INT.str,
                        PRODUCT.str,
                        IMAGE_EXT.str, IMAGE_INT.str,

                        INHERIT.str,

                        SIMILAR.str,

                        PROPERTY.str,
                        INSTANCE.str,
                        INSTANCE_PROPERTY.str,

                        NEGATE.str,

                        IMPLICATION.str,
                        EQUIV.str,
                        IMPLICATION_AFTER.str, IMPLICATION_BEFORE.str, IMPLICATION_WHEN.str,
                        EQUIV_AFTER.str, EQUIV_WHEN.str,
                        DISJUNCTION.str,
                        CONJUNCTION.str,
                        SEQUENCE.str,
                        PARALLEL.str
                ),

                push(getOperator(match()))
        );
    }

    Rule sepArgSep() {
        return sequence(s(), ARGUMENT_SEPARATOR, s());
    }

    Rule sepArg() {
        return sequence(s(), ARGUMENT_SEPARATOR);

        /*
        return firstOf(
                //check the ' , ' comma separated first, it is more complex
                sequence(s(), String.valueOf(Symbols.ARGUMENT_SEPARATOR), s()),


                //then allow plain whitespace to function as a term separator?
                s()
        );*/
    }

    @Cached
    Rule MultiArgTerm(Op open, char close) {
        return MultiArgTerm(open, /*open, */close, false, false, false, false);
    }

    boolean OperationPrefixTerm() {
        return push( new Object[] { termable(pop()), (Operator.class) } );
    }

    /**
     * list of terms prefixed by a particular compound term operate
     */
    @Cached
    Rule MultiArgTerm(Op defaultOp, char close, boolean initialOp, boolean allowInternalOp, @Deprecated boolean spaceSeparates, boolean operatorPrecedes) {


        return sequence(

                operatorPrecedes ?  OperationPrefixTerm() : push(Compound.class),

                initialOp ? Op() : Term(),

                spaceSeparates ?

                        sequence( s(), Op(), s(), Term() )

                        :

                        zeroOrMore(sequence(
                                sepArg(),
                            allowInternalOp ? AnyOperatorOrTerm() : Term()
                        )),

                sequence(s(), close),

                push(popTerm(defaultOp, allowInternalOp))
        );
    }

    /**
     * operation()
     */
    Rule EmptyOperationParens() {
        return sequence(

                OperationPrefixTerm(),

                /*s(),*/ COMPOUND_TERM_OPENER, s(), COMPOUND_TERM_CLOSER,

                push(popTerm(Op.OPERATOR, false))
        );
    }

    Rule AnyOperatorOrTerm() {
        return firstOf(Op(), Term());
    }


    /** pass-through; the object is potentially a term but don't create it yet */
    static Object termable(Object o) {
        return o;
    }

    static Object the(Object o) {
        if (o == null) return null; //pass through
        if (o instanceof Term) return o;
        if (o instanceof String) {
            String s= (String)o;
            return Atom.the(s);
        }
        throw new RuntimeException(o + " is not a term");
    }

    /**
     * produce a term from the terms (& <=1 NALOperator's) on the value stack
     */
    final Term popTerm(Op op /*default */, @Deprecated boolean allowInternalOp) {



        //System.err.println(getContext().getValueStack());

        ValueStack<Object> stack = getContext().getValueStack();


        List<Term> vectorterms = Global.newArrayList(2); //stack.size() + 1);

        while (!stack.isEmpty()) {
            Object p = pop();

            if (p instanceof Object[]) {
                //it's an array so unpack by pushing everything back onto the stack except the last item which will be used as normal below
                Object[] pp = (Object[])p;
                if (pp.length > 1) {
                    for (int i = pp.length-1; i >= 1; i--) {
                        stack.push(pp[i]);
                    }
                }

                p = pp[0];
            }

            if (p == Operator.class) {
                op = OPERATOR;
                break;
            }

            if (p == Compound.class) break; //beginning of stack frame for this term




            if (p instanceof String) {
                throw new RuntimeException("string not expected here");
//                Term t = Atom.the((String) p);
//                vectorterms.add(t);
            } else if (p instanceof Term) {
                Term t = (Term) p;
                vectorterms.add(t);
            } else if (p instanceof Op) {

                if (op != null) {
                    if ((!allowInternalOp) && (!p.equals(op)))
                        throw new RuntimeException("Internal operator " + p + " not allowed here; default op=" + op);

                    throw new NarseseException("Too many operators involved: " + op + ',' + p + " in " + stack + ':' + vectorterms);
                }

                op = (Op)p;
            }
        }


        if (vectorterms.isEmpty()) return null;

        //int v = vectorterms.size();

        Collections.reverse(vectorterms);

//        if ((op == null || op == PRODUCT) && (vectorterms.get(0) instanceof Operator)) {
//            op = NALOperator.OPERATION;
//        }

        if (op == null) op = Op.PRODUCT;



        if (op == OPERATOR) {
            //temporary
            //final Term self = Atom.the("SELF");//memory.self();

            //automatically add SELF term to operations if in NAL8+
            /*if (!vectorterms.isEmpty() && !vectorterms.get(vectorterms.size()-1).equals(self))
                vectorterms.add(self);*/ //SELF in final argument

            return $.oper(
                new Operator(vectorterms.get(0)),
                $.p(vectorterms, 1, vectorterms.size())
            );
        }
        else {
            Term[] va = vectorterms.toArray(new Term[vectorterms.size()]);
            return Compounds.the(op, va);
        }
    }



    /**
     * whitespace, optional
     */
    Rule s() {
        return zeroOrMore(anyOf(" \t\f\n\r"));
    }

//    Rule sNonNewLine() {
//        return zeroOrMore(anyOf(" \t\f"));
//    }

//    public static NarseseParser newParser(NAR n) {
//        return newParser(n.memory);
//    }
//
//    public static NarseseParser newParser(Memory m) {
//        NarseseParser np = ;
//        return np;
//    }


    //r.getValueStack().clear();

//        r.getValueStack().iterator().forEachRemaining(x -> {
//            if (x instanceof Task)
//                c.accept((Task) x);
//            else {
//                throw new RuntimeException("Unknown parse result: " + x + " (" + x.getClass() + ')');
//            }
//        });


    /** parse one term and normalize it if successful */
    public <T extends Term> T term(String s) {
        Term x = termRaw(s);
        if (x==null) return null;

        return x.normalized();
    }


//    public TaskRule taskRule(String input) {
//        Term x = termRaw(input, singleTaskRuleParser);
//        if (x==null) return null;
//
//        return x.normalizeDestructively();
//    }


    public <T extends Term> T termRaw(String input) throws NarseseException {

        ParsingResult r = singleTermParser.run(input);

        DefaultValueStack stack = (DefaultValueStack) r.getValueStack();
        FasterList sstack = stack.stack;

        switch (sstack.size()) {
            case 1:


                Object x = sstack.get(0);

                if (x instanceof String)
                    x = $.$((String) x);

                if (x != null) {

                    try {
                        return (T) x;
                    } catch (ClassCastException cce) {
                        throw new NarseseException("Term mismatch: " + x.getClass(), cce);
                    }
                }
                break;
            case 0:
                return null;
            default:
                throw new RuntimeException("Invalid parse stack: " + sstack);
        }

        return null;
    }


    public static NarseseException newParseException(String input, ParsingResult r, Exception e) {

        //CharSequenceInputBuffer ib = (CharSequenceInputBuffer) r.getInputBuffer();


        //if (!r.isSuccess()) {
            return new NarseseException("input: " + input + " (" + r.toString() + ")  " +
                    (e!=null ? e.toString() + ' ' + Arrays.toString(e.getStackTrace()) : ""));

        //}
//        if (r.parseErrors.isEmpty())
//            return new InvalidInputException("No parse result for: " + input);
//
//        String all = "\n";
//        for (Object o : r.getParseErrors()) {
//            ParseError pe = (ParseError)o;
//            all += pe.getClass().getSimpleName() + ": " + pe.getErrorMessage() + " @ " + pe.getStartIndex() + "\n";
//        }
//        return new InvalidInputException(all + " for input: " + input);
    }


//    /**
//     * interactive parse test
//     */
//    public static void main(String[] args) {
//        NAR n = new NAR(new Default());
//        NarseseParser p = NarseseParser.newParser(n);
//
//        Scanner sc = new Scanner(System.in);
//
//        String input = null; //"<a ==> b>. %0.00;0.9%";
//
//        while (true) {
//            if (input == null)
//                input = sc.nextLine();
//
//            ParseRunner rpr = new ListeningParseRunner<>(p.Input());
//            //TracingParseRunner rpr = new TracingParseRunner(p.Input());
//
//            ParsingResult r = rpr.run(input);
//
//            //p.printDebugResultInfo(r);
//            input = null;
//        }
//
//    }

//    public void printDebugResultInfo(ParsingResult r) {
//
//        System.out.println("valid? " + (r.isSuccess() && (r.getParseErrors().isEmpty())));
//        r.getValueStack().iterator().forEachRemaining(x -> System.out.println("  " + x.getClass() + ' ' + x));
//
//        for (Object e : r.getParseErrors()) {
//            if (e instanceof InvalidInputError) {
//                InvalidInputError iie = (InvalidInputError) e;
//                System.err.println(e);
//                if (iie.getErrorMessage() != null)
//                    System.err.println(iie.getErrorMessage());
//                for (MatcherPath m : iie.getFailedMatchers()) {
//                    System.err.println("  ?-> " + m);
//                }
//                System.err.println(" at: " + iie.getStartIndex() + " to " + iie.getEndIndex());
//            } else {
//                System.err.println(e);
//            }
//
//        }
//
//        System.out.println(printNodeTree(r));
//
//    }


    /**
     * Describes an error that occurred while parsing Narsese
     */
    public static class NarseseException extends RuntimeException {

        /**
         * An invalid addInput line.
         * @param s type of error
         */
        public NarseseException(String s) {
            super(s);
        }

        public NarseseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}