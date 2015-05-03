/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package nars.gui.output;

import automenta.vivisect.Video;
import com.google.common.collect.Iterators;
import javolution.util.FastMap;
import nars.Events;
import nars.Events.FrameEnd;
import nars.NAR;
import nars.budget.Bag;
import nars.budget.Budget.Budgetable;
import nars.event.AbstractReaction;
import nars.gui.VerticalLayout;
import nars.gui.output.graph.nengo.TermGraphNode;
import nars.gui.output.graph.nengo.TermGraphPanelNengo;
import nars.nal.Item;
import nars.nal.Named;
import nars.nal.Sentence;
import nars.nal.TruthValue.Truthable;
import nars.nal.concept.Concept;
import nars.nal.term.Term;
import nars.nal.tlink.TaskLink;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.WEST;

/**
 * Manages a set of ConceptPanels by receiving events and dispatching update commands
 * TODO use a minimum framerate for updating
 * TODO use ConceptMapSet and remove the MultiMap
 */
public class ConceptPanelBuilder extends AbstractReaction {


    private final NAR nar;
    private final Map<Concept, ConceptPanel> concept = new FastMap().atomic();
    static float conceptGraphFPS = 4; //default update frames per second

    final Set<Concept> changed = new LinkedHashSet();

    public ConceptPanelBuilder(NAR n) {
        super(n, Events.FrameEnd.class,
                Events.ConceptBeliefAdd.class,
                Events.ConceptBeliefRemove.class,
                Events.ConceptQuestionAdd.class,
                Events.ConceptQuestionRemove.class,
                Events.ConceptGoalAdd.class,
                Events.ConceptGoalRemove.class);

        this.nar = n;

    }


    public static Color getBeliefColor(float freq, float conf, float factor) {
        //float ii = 0.45f + (factor*conf) * 0.55f;
//        float green = freq > 0.5f ? (freq / 2f) : 0f;
//        float red = freq <= 0.5f ? ((1.0f - freq) / 2f) : 0;
        return new Color(freq, 1.0f - freq, 1.0f, factor);
    }

    public static Color getGoalColor(float freq, float conf, float factor) {
        //float ii = 0.45f + (factor*conf) * 0.55f;
//        float green = freq > 0.5f ? (freq / 2f) : 0f;
//        float red = freq <= 0.5f ? ((1.0f - freq) / 2f) : 0;
        return new Color(1.0f - freq, 1.0f, freq, factor);
    }

    public ConceptPanel newPanel(Concept c, boolean label, boolean full, int chartSize) {
        ConceptPanel cp = new ConceptPanel(c, label, full, chartSize).update(nar.time());
//        {

//            @Override
//            protected void visibility(final boolean appearedOrDisappeared) {
//
//                    if (appearedOrDisappeared) {
//                        //concept.put(c, this);
//                    } else {
//                        this.closed = true;
//                    }
//
//            }
        //    }

        concept.put(c, cp);
        return cp;
    }

    @Override
    public void event(Class event, Object[] args) {

        if (event == FrameEnd.class) {
            //SwingUtilities.invokeLater(this);
            if (isAutoRemove())
                updateAll();
            else
                updateChanged();
        } else {
            if (args[0] instanceof Concept) {
                Concept c = (Concept) args[0];
                changed.add(c);
            } else {
                throw new RuntimeException(this + " unable to process unknown event format: " + event + " with " + Arrays.toString(args));
            }
        }
    }

    @Deprecated
    public boolean isAutoRemove() {
        return true;
    }

    public synchronized void updateChanged() {

        if (changed.isEmpty()) return;

        final long now = nar.time();


        for (Concept c : changed) {
            ConceptPanel cp = concept.get(c);
            if (cp == null) {
                continue;
            }
            if (cp.isShowing())
                cp.update(now);
            else {
                concept.remove(c);
            }
        }


        changed.clear();


    }

    @Deprecated public void updateAll() {
//        throw new RuntimeException("should not need to update all");
//        //TODO only update the necessary concepts
//        long t = nar.time();
//        synchronized (concept) {
//            final Iterator<Map.Entry<Concept, ConceptPanel>> ee = concept.entries().iterator();
//            while (ee.hasNext()) {
//                final Map.Entry<Concept, ConceptPanel> e = ee.next();
//                final ConceptPanel cp = e.getValue();
//                if (isAutoRemove() && (cp.closed) || (!cp.isShowing())) {
//                    ee.remove();
//                } else if (cp.isVisible()) {
//                    cp.update(t);
//                }
//            }
//        }
    }

    public void remove(Collection<ConceptPanel> cp) {
        if (cp.isEmpty()) return;

        for (ConceptPanel c : cp)
            concept.remove(c.concept, c);
    }

    public boolean remove(ConceptPanel cp) {
        return concept.remove(cp.concept, cp);
    }

    public void off() {
        super.off();
        changed.clear();
        concept.clear();
    }

    public ConceptPanel getPanel(Concept c) {
        return concept.get(c);
    }

    public ConceptPanel getFirstPanelOrCreateNew(Concept c, boolean label, boolean full, int chartSize) {
        ConceptPanel existing = getPanel(c);
        if (existing == null) {
            return newPanel(c, label, full, chartSize);
        }
        return existing;
    }


    final static float titleSize = 18f;
    final static Font titleFont = Video.monofont.deriveFont(titleSize);

    public static class ConceptPanel extends JPanel implements Named<Concept> {


        public final Concept concept;
        private final TruthChart beliefGoalChart;
        private final PriorityColumn questionChart;
        private final boolean full;
        private RadialBagChart taskLinkChart;
        private ScatterPlotBagChart termLinkChart;
        //private final BagChart termLinkChart;
        //private final BagChart taskLinkChart;
        int chartWidth = 64;
        int chartHeight = 64;

        private JLabel subtitle;
        //final float subfontSize = 16f;
        private BeliefTimeline beliefGoalTime;
        public boolean closed;
        // private final PCanvas syntaxPanel;


        public ConceptPanel(final Concept c, boolean label, boolean full, int chartSize) {
            super();
            this.concept = c;
            this.closed = false;

            this.chartWidth = this.chartHeight = chartSize;
            this.full = full;

            setOpaque(false);

            this.subtitle = new JLabel();

            this.beliefGoalTime = new BeliefTimeline(chartHeight * 2, chartHeight, false);
            this.beliefGoalChart = new TruthChart(chartWidth, chartHeight);
            this.questionChart = new PriorityColumn((int) Math.ceil(Math.sqrt(chartWidth)), chartHeight);

            if (full) {
                setLayout(new BorderLayout());

                TermGraphPanelNengo nengo;


                add(nengo = new TermGraphPanelNengo(new TermGraphNode(c.memory) {

                    Set<Term> neighbors = new LinkedHashSet();

                    @Override
                    public boolean includeTerm(Term t) {

                        if (t.equals(concept.getTerm())) return true;

                        if (neighbors.contains(t)) return true;

                        return false;

                    }

                    @Override
                    public boolean filter() {
                        return true;
                    }


                    @Override
                    public void refresh(Object x) {
                        if (x == concept) {
                            neighbors.clear();
                            Iterator<Term> neighborTerms = concept.adjacentTerms(true, true);
                            Iterators.addAll(neighbors, neighborTerms);
                        }

                        super.refresh(x);
                    }

                    @Override
                    public String name() {
                        return concept.term.toString();
                    }

                }, conceptGraphFPS), BorderLayout.CENTER);


                FlowLayout fl = new FlowLayout(FlowLayout.RIGHT);

                fl.setVgap(30);

                nengo.getUniverse().setLayout(fl);

                nengo.getUniverse().add(beliefGoalTime);
                nengo.getUniverse().add(beliefGoalChart);
                nengo.getUniverse().add(questionChart);

                JPanel details = new JPanel(new GridLayout(0, 2));
                details.add(termLinkChart = new ScatterPlotBagChart(c, c.termLinks));
                details.add(taskLinkChart = new RadialBagChart(c, c.taskLinks));
                add(details, BorderLayout.SOUTH);

            } else {
                setLayout(new BorderLayout());


                JPanel details = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
                details.setOpaque(false);

                details.add(beliefGoalTime);
                details.add(beliefGoalChart);
                details.add(questionChart);

                //details.add(this.questChart = new PriorityColumn((int)Math.ceil(Math.sqrt(chartWidth)), chartHeight)));

                //details.add(this.termLinkChart = new ScatterPlotBagChart(c, c.termLinks));
                //details.add(this.taskLinkChart = new RadialBagChart(c, c.taskLinks));

                add(details, WEST);

                if (label) {
//                    JTextArea title = new JTextArea(concept.term.toString());
//
//                    title.setWrapStyleWord(true);
//                    title.setLineWrap(true);
//                    title.setEditable(false);
//                    title.setOpaque(false);
                    JLabel title = new JLabel(concept.term.toString());
                    title.setFont(titleFont);

                    JPanel titlePanel = new JPanel(new VerticalLayout());
                    titlePanel.setOpaque(false);
                    titlePanel.add(title);
                    titlePanel.add(subtitle);


                    add(titlePanel, CENTER);
                }


            }

            doLayout();

           /* TermSyntaxVis tt = new TermSyntaxVis(c.term);
            syntaxPanel = new PCanvas(tt);
            syntaxPanel.setZoom(10f);

            syntaxPanel.noLoop();
            syntaxPanel.redraw();

            add(syntaxPanel);*/
            //setComponentZOrder(overlay, 1);
            //syntaxPanel.setBounds(0,0,400,400);


        }

        @Override
        public Concept name() {
            return concept;
        }

        public ConceptPanel update(long time) {

            String st = "";

            beliefGoalChart.setVisible(true);
            if (!concept.beliefs.isEmpty() || !concept.goals.isEmpty()) {

                beliefGoalChart.update(time, concept.beliefs, concept.goals);

                if (!concept.beliefs.isEmpty())
                    st += (concept.beliefs.get(0).truth.toString()) + ' ';
                if (!concept.goals.isEmpty()) {
                    st += " desire: " + concept.goals.get(0).truth.toString();
                }

                beliefGoalTime.setVisible(
                        beliefGoalTime.update(time, concept.beliefs, concept.goals));
            } else {
                beliefGoalChart.setVisible(false);
                beliefGoalTime.setVisible(false);
            }

            if (subtitle != null)
                subtitle.setText(st.trim());

            if (!concept.questions.isEmpty()) {
                questionChart.setVisible(true);
                questionChart.update(concept.questions);
            } else {
                questionChart.setVisible(false);
            }


            if (termLinkChart != null) {
                termLinkChart.update(time);
            }
            if (taskLinkChart != null) {
                taskLinkChart.update(time);
            }


            //validate();

            return this;
        }

//        @Override
//        protected void visibility(boolean appearedOrDisappeared) {
//            validate();
//        }


    }

    public static class PriorityColumn extends ImagePanel {

        public PriorityColumn(int width, int height) {
            super(width, height);
            update(Collections.EMPTY_LIST);
        }

        public void update(Iterable<? extends Budgetable> i) {
            Graphics g = g();
            if (g == null) return;

            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());
            for (Budgetable s : i) {
                float pri = s.getBudget().getPriority();
                float dur = s.getBudget().getDurability();

                float ii = 0.1f + pri * 0.9f;
                g.setColor(new Color(ii, ii, ii, 0.5f + 0.5f * dur));

                int h = 8;
                int y = (int) ((1f - pri) * (getHeight() - h));

                g.fillRect(0, y - h / 2, getWidth(), h);

            }
            g.dispose();
        }
    }

    /**
     * normalized to entire history of non-eternal beliefs;
     * displayed horizontally
     */
    public static class BeliefTimeline extends ImagePanel {

        float minTime, maxTime;
        private float timeFactor;
        boolean vertical;
        int thick = 4;
        int timeMargin = 3;
        private float length;

        public BeliefTimeline(int width, int height, boolean vertical) {
            super(width, height);
            this.vertical = vertical;
        }

        public float getT(long when) {
            return ((when - minTime) / timeFactor);
        }

        public boolean update(long time, Collection<Sentence> belief, Collection<Sentence> goal) {

            minTime = maxTime = time;
            for (Sentence s : belief) {
                if (s == null || s.isEternal()) continue;
                long when = s.getOccurrenceTime();
                if (minTime > when) minTime = when;
                if (maxTime < when) maxTime = when;
            }
            for (Sentence s : goal) {
                if (s == null || s.isEternal()) continue;
                long when = s.getOccurrenceTime();
                if (minTime > when) minTime = when;
                if (maxTime < when) maxTime = when;
            }

            if (minTime == maxTime) {
                //no time-distinct beliefs
                return false;
            }

            Graphics2D g = g();
            if (g == null) return false;


            timeFactor = 1.0f * (maxTime - minTime);

            if (vertical)
                length = ((float) h - timeMargin * 2);
            else
                length = ((float) w - timeMargin * 2);


            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());
            for (Sentence s : belief)
                draw(g, s, true);
            for (Sentence s : goal)
                draw(g, s, false);


            // "now" axis
            g.setColor(Color.GRAY);


            int tt = Math.round(getT(time) * length);

            if (vertical)
                g.fillRect(0, timeMargin + tt - 1, getWidth(), 3);
            else
                g.fillRect(timeMargin + tt - 1, 0, 3, getHeight());

            g.dispose();
            return true;
        }

        private void draw(Graphics2D g, Sentence s, boolean belief) {
            if (s == null) return;
            if (s.isEternal()) return;
            float freq = s.getTruth().getFrequency();
            float conf = s.getTruth().getConfidence();

            long when = s.getOccurrenceTime();

            int yy = (int) (getT(when) * length);


            int xx = (int) ((1.0f - freq) * (this.w - thick));


            g.setColor(belief ? getBeliefColor(freq, conf, 0.75f) : getGoalColor(freq, conf, 0.75f));

            if (vertical)
                g.fillRect(xx, yy, thick, thick);
            else
                g.fillRect(yy, xx, thick, thick);

        }
    }

    public static class TruthChart extends ImagePanel {

        public TruthChart(int width, int height) {
            super(width, height);
        }

        public void update(long now, Collection<? extends Truthable> beliefs, Collection<? extends Truthable> goals) {
            Graphics g = g();
            if (g == null) return;

            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());
            for (Truthable s : beliefs) {
                draw(now, g, s, true);
            }
            for (Truthable s : goals) {
                draw(now, g, s, false);
            }
            g.dispose();

        }

        private void draw(final long now, final Graphics g, final Truthable s, final boolean belief) {
            final float freq = s.getTruth().getFrequency();
            final float conf = s.getTruth().getConfidence();

            float factor = 0.9f;
            if (s instanceof Sentence) {
                Sentence ss = (Sentence) s;
                if (!ss.isEternal()) {
                    //float factor = TruthFunctions.temporalProjection(now, ss.getOccurenceTime(), now);
                    //factor = 1.0f / (1f + Math.abs(ss.getOccurrenceTime() - now));
                    factor = 0.75f;
                }
            }
            g.setColor(belief ? getBeliefColor(freq, conf, factor) :
                    getGoalColor(freq, conf, factor));

            int w, h;
            if (belief) {
                w = 10;
                h = 4;
            } else {
                h = 10;
                w = 4;
            }
            float dw = getWidth() - w;
            float dh = getHeight() - h;

            /* x-axis: confidence, y-axis: freq */
            g.fillRect((int) ((conf) * dh), (int) ((1.0f - freq) * dw), w, h);
        }
    }

    abstract public static class BagChart extends ImagePanel {

        final Map<String, Float> priority = new TreeMap(); //this might be limiting if items have the same .name().toString()

        private final Concept concept;
        private final Bag bag;
        private final String conceptString;

        float momentum = 0.9f;
        int maxItems = 32;
        private float max = 0, min = 0;

        public BagChart(Concept c, Bag b) {
            super(400, 200);

            this.concept = c;
            this.bag = b;
            this.conceptString = concept.getTerm().toString();
        }

        boolean updateData() {

            final AtomicBoolean changed = new AtomicBoolean(false);

            synchronized (priority) {
                min = Float.POSITIVE_INFINITY;
                max = Float.NEGATIVE_INFINITY;
                //priority.clear();

                bag.forEach(new Consumer<Item>() {
                    boolean finished = false;

                    @Override
                    public void accept(final Item t) {

                        if (finished || priority.size() >= maxItems) {
                            finished = true;
                            //this isnt the most efficient way to do it
                            //instead what would be best is: Bag.forEach(consumer, max) method ,maybe with an iteration direction (high to low, low to high)
                            return;
                        }

                        String n = t.name().toString();

                        if (t instanceof TaskLink) {

                            if (((TaskLink) t).getTerm().equals(concept.getTerm())) {
                                n = n.replace(conceptString, "");
                            }
                        }

                        float existing = priority.getOrDefault(n, -0.01f);

                        float p = t.getPriority();
                        float v = p;

                        if (existing != v) {
                            priority.put(n, v);
                            changed.set(true);
                        }

                        if (p > max) max = p;
                        if (p < min) min = p;
                    }
                });
                setToolTipText("Min=" + min + ", Max=" + max);
            }

            return (changed.get());
        }

        abstract protected void render(Graphics2D g);

        public void update(long now) {
            if (!updateData())
                return;

            Graphics2D g = (Graphics2D) g();
            render(g);
            g.dispose();

            repaint();
        }


    }

    public static class ScatterPlotBagChart extends BagChart {

        public ScatterPlotBagChart(Concept c, Bag b) {
            super(c, b);
        }

        @Override
        protected void render(Graphics2D g) {

            g.setColor(new Color(0.1f, 0.1f, 0.1f, 0.75f));
            g.fillRect(0, 0, getWidth(), getHeight());

            int height = getHeight();
            float dx = getWidth() / (1 + priority.size());
            float x = dx / 2;
            g.setFont(Video.monofont.deriveFont(9f));
            for (Map.Entry<String, Float> e : priority.entrySet()) {

                String s = e.getKey();
                float v = e.getValue();
                //int textLength = g.getFontMetrics().stringWidth(s);
                float y = (v) * (height * 0.85f) + (height * 0.1f);
                y = height - y;

                g.setColor(Color.getHSBColor(e.getValue() * 0.5f + 0.25f, 0.75f, 0.8f));


                g.translate((int) x, (int) y /*+ textLength*/);

                float angle = -(float) ((1.0f - v) * Math.PI / 2f);
                if (angle != 0)
                    g.rotate(angle);


                g.drawString(s, 0, 0);

                if (angle != 0)
                    g.rotate(-angle);

                g.translate(-(int) x, -(int) y /*+ textLength*/);

                x += dx;
            }
        }
    }

    public static class RadialBagChart extends BagChart implements MouseMotionListener {

        float phase = 0;

        public RadialBagChart(Concept c, Bag b) {
            super(c, b);

            addMouseMotionListener(this);
        }

        @Override
        protected void render(Graphics2D g) {

            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());

            int count = priority.size();
            final int width = getWidth();
            final int height = getHeight();
            float dx = (float) (Math.PI * 2) / (count);
            float theta = phase;
            g.setFont(Video.monofont.deriveFont(9f));

            int i = 0;
            g.translate(width / 2, height / 2);
            for (Map.Entry<String, Float> e : priority.entrySet()) {

                String s = e.getKey();


                float rad = e.getValue() * (width / 2 * 0.45f) + (width / 2 * 0.12f);

                g.setColor(Color.getHSBColor(e.getValue() * 0.5f + 0.25f, 0.75f, 0.8f));
                //g.translate((int) theta, (int) y /*+ textLength*/);

                int x, y;
                if (count == 1) {
                    int textLength = g.getFontMetrics().stringWidth(s);
                    x = 0;
                    y = 0;
                    x -= textLength / 2;
                    g.drawString(s, x, y);
                } else {
                    if (i == 0) g.rotate(phase); //initial angle offset

                    //x = (int) (rad * Math.cos(theta)) + width / 2;
                    //y = (int) (rad * Math.sin(theta)) + height / 2;
                    g.drawString(s, rad, 0);
                    g.rotate(dx);
                    //g.rotate(-theta);
                }

                theta += dx;
                i++;
            }
        }

        int prevx;

        @Override
        public void mouseDragged(MouseEvent e) {
            int ex = e.getPoint().x;
            phase += (ex - prevx) / 32f;
            prevx = ex;
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            prevx = e.getPoint().x;
        }
    }


}