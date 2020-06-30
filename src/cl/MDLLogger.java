/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class MDLLogger {
    
    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARNING = 2;
    public static final int ERROR = 3;
    public static final int SILENT = 4;

    List<Integer> minLevelToLogStack = new ArrayList<>();
    int minLevelToLog = INFO;

    public MDLLogger(int a_minLevelToLog) {
        minLevelToLog = a_minLevelToLog;
    }
    
    public void setMinLevelToLog(int a_minLevelToLog)
    {
        minLevelToLog = a_minLevelToLog;
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
        return "Z80OptimizerLogger";
    }

    public boolean isLoggable(int level) {
        return true;
    }

    public void log(int level, ResourceBundle bundle, String msg, Throwable thrown) {
        log(level, msg);
    }

    public void log(int level, ResourceBundle bundle, String format, Object... params) {
        log(level, format);
    }

    public void log(int level, String msg) {
        if (level < minLevelToLog) {
            return;
        }
        switch (level) {
            case DEBUG:
                System.out.println("DEBUG: " + msg);
                break;
            case INFO:
                System.out.println(msg);
                break;
            case WARNING:
                System.out.println("WARNING: " + msg);
                break;
            case ERROR:
                System.err.println("ERROR: " + msg);
                break;
            default:
                System.out.println(msg);
                break;
        }
    }

}
