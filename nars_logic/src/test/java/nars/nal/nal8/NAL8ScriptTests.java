package nars.nal.nal8;


import nars.NARSeed;
import nars.model.impl.Classic;
import nars.model.impl.Default;
import nars.nal.ScriptNALTest;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static nars.io.in.LibraryInput.getParams;

public class NAL8ScriptTests extends ScriptNALTest {

    public NAL8ScriptTests(NARSeed b, String input) {
        super(b, input);
    }

    @Parameterized.Parameters(name= "{1} {0}")
    public static Collection configurations() {
        return getParams(new String[]{"test8"},
                new Default(),
                new Default().setInternalExperience(null),
                new Classic().setInternalExperience(null)
        );
    }

    public int getMaxCycles() { return 1500; }


}
