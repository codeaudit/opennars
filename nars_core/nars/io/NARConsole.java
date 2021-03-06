/*
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
package nars.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import nars.NAR;
import nars.io.CommandLineNARBuilder;

/**
 * Run Reasoner
 * <p>
 Runs a NAR with addInput. useful for command line or batch functionality; 
 TODO check duplicated code with {@link nars.main.NARS}
 * <p>
 * Manage the internal working thread. Communicate with Reasoner only.
 */
public class NARConsole {

    private final NAR nar;

    private boolean logging;
    private PrintStream out = System.out;
    private final boolean dumpLastState = true;
    int maxTime = 0;

    /**
     * The entry point of the standalone application.
     * <p>
     * @param args optional argument used : one addInput file
     */
    public static void main(String args[]) {
                
        NARConsole nars = new NARConsole(new NAR(new CommandLineNARBuilder(args)));
        nars.run(args);
        
        // TODO only if single finish ( no reset in between )
        if (nars.dumpLastState) {
            System.out.println("\n==== Dump Last State ====\n"
                    + nars.nar.toString());
        }
    }

    public NARConsole(NAR n) {
        this.nar = n;
    }

    private class InputThread extends Thread
    {
      private BufferedReader bufIn;
      NAR nar;
      InputThread(InputStream in, NAR nar)
      {
        this.bufIn = new BufferedReader(new InputStreamReader(in));
        this.nar=nar;
      }
      public void run()
      {
        while(true)
        {
          try
          {
            String line=bufIn.readLine();
            if(line!=null)
                nar.addInput(line);
          }catch(Exception ex){}
          try
          {
            Thread.sleep(1);
          }catch(Exception ex){}
        }
      }
    }
    
    /**
     * non-static equivalent to {@link #main(String[])} : finish to completion from
 an addInput file
     */
    public void run(String args[]) {
        TextOutput output = new TextOutput(nar, new PrintWriter(out, true));
        output.setErrors(true);
        output.setErrorStackTrace(true);
        InputThread it;
        
        if (args.length > 0) {
            try {
                nar.addInput(new TextInput(new File(args[0])));
            } catch (FileNotFoundException ex) {
                System.err.println("NARRun.init: " + ex);
            }
        }
        else {     
            it=new InputThread(System.in,nar);
            it.start();
            //nar.addInput(new TextInput(new BufferedReader(new InputStreamReader(System.in))));
        }
        
               while (true) {
            if (logging)
                log("NARSBatch.run():"
                        + " step " + nar.time()
                        + " " + nar.inputChannels.size());
            

            nar.step(1);
            
            
            if (logging)
                log("NARSBatch.run(): after tick"
                        + " step " + nar.time()
                        + " " + nar.inputChannels.size());
            
            if (maxTime > 0) {
                if ((nar.inputChannels.isEmpty()) || nar.time() == maxTime) {
                    break;
                }
            }
        }
               
        System.exit(0);
    }

    public void setPrintStream(PrintStream out) {
        this.out = out;
    }

    private void log(String mess) {
        if (logging) {
            System.out.println("/ " + mess);
        }
    }
}
