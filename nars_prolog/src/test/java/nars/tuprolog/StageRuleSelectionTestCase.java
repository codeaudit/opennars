package nars.tuprolog;

import junit.framework.TestCase;

public class StageRuleSelectionTestCase extends TestCase {
	
	public void testUnknownPredicateInQuery() throws MalformedGoalException, InvalidLibraryException {
		Prolog engine = new DefaultProlog();
		TestWarningListener warningListener = new TestWarningListener();
		engine.addWarningListener(warningListener);
		String query = "p(X).";
		engine.solve(query);
		assertTrue(warningListener.warning.indexOf("p/1") > 0);
		assertTrue(warningListener.warning.indexOf("Unknown") > 0);
	}
	
	public void testUnknownPredicateInTheory() throws InvalidTheoryException, MalformedGoalException, InvalidLibraryException {
		Prolog engine = new DefaultProlog();
		TestWarningListener warningListener = new TestWarningListener();
		engine.addWarningListener(warningListener);
		String theory = "p(X) :- a, b. \nb.";
		engine.setTheory(new Theory(theory));
		String query = "p(X).";
		engine.solve(query);
		assertTrue(warningListener.warning.indexOf("a/0") > 0);
		assertTrue(warningListener.warning.indexOf("Unknown") > 0);
	}

}