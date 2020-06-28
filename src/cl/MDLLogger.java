/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import java.util.ResourceBundle;

public class MDLLogger implements System.Logger {

    Level minLevelToLog = Level.INFO;

    public MDLLogger(Level a_minLevelToLog) {
        minLevelToLog = a_minLevelToLog;
    }
    
    public void setMinLevelToLog(Level a_minLevelToLog)
    {
        minLevelToLog = a_minLevelToLog;
    }

    @Override
    public String getName() {
        return "Z80OptimizerLogger";
    }

    @Override
    public boolean isLoggable(Level level) {
        return true;
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        log(level, msg);
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        log(level, format);
    }

    @Override
    public void log(Level level, String msg) {
        if (level.getSeverity() < minLevelToLog.getSeverity()) {
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
