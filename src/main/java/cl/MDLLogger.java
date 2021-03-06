/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class MDLLogger {
    
    public static final int DEBUG = 0;
    public static final int TRACE = 1;
    public static final int INFO = 2;
    public static final int WARNING = 3;
    public static final int ERROR = 4;
    public static final int SILENT = 5;
    
    // colors:
    public static String ANSI_RESET = "\u001B[0m";
    public static String ANSI_RED = "\u001B[31m";
    public static String ANSI_YELLOW = "\u001B[33m";
    public static String ANSI_WHITE = "\u001b[37;1m";

    List<Integer> minLevelToLogStack = new ArrayList<>();
    int minLevelToLog = INFO;
    PrintStream out = System.out;
    PrintStream err = System.err;
        

    public MDLLogger(int a_minLevelToLog) {
        minLevelToLog = a_minLevelToLog;
    }

    public MDLLogger(int a_minLevelToLog, PrintStream a_out, PrintStream a_err) {
        minLevelToLog = a_minLevelToLog;
        out = a_out;
        err = a_err;
    }
    
    public void setMinLevelToLog(int a_minLevelToLog)
    {
        minLevelToLog = a_minLevelToLog;
    }
    
    
    public void useColors(boolean val) 
    {
        if (val) {
            ANSI_RESET = "\u001B[0m";
            ANSI_RED = "\u001B[31m";
            ANSI_YELLOW = "\u001B[33m";
            ANSI_WHITE = "\u001b[37;1m";
        } else {
            ANSI_RESET = "";
            ANSI_RED = "";
            ANSI_YELLOW = "";
            ANSI_WHITE = "";
        }
    }
    
        
    public void silence()
    {
        minLevelToLogStack.add(0, minLevelToLog);
        minLevelToLog = SILENT;
    }

    
    public void resume()
    {
        minLevelToLog = minLevelToLogStack.remove(0);
    }
    
    
    public String getName() {
        return "MDLLogger";
    }

    
    public void log(int level, String msg) {
        if (level < minLevelToLog) {
            return;
        }
        switch (level) {
            case DEBUG:
                out.println("DEBUG: " + msg);
                break;
            case INFO:
                out.println("INFO: " + msg);
                break;
            case WARNING:
                out.println(ANSI_YELLOW + "WARNING: " + msg + ANSI_RESET);
                break;
            case ERROR:
                err.println(ANSI_RED + "ERROR: "+ msg + ANSI_RESET);
                break;
            default:
                out.println(msg);
                break;
        }
    }
}