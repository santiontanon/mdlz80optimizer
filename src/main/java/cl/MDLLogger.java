/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import java.util.ArrayList;
import java.util.List;

public class MDLLogger {
    
    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARNING = 2;
    public static final int ERROR = 3;
    public static final int SILENT = 4;

    List<Integer> minLevelToLogStack = new ArrayList<>();
    int minLevelToLog = INFO;
    
    List<String> annotations = new ArrayList<>();
    

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
        return "MDLLogger";
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
    
    
    /*
    Records a message that will be written to the annotations file (for later loading
    into editors and provide, for example, in0editor optimization hints).
    - fileName/lineNumber should be the file and line where to show the annotation in editor
    - tag is the type of annotation (e.g.: "warning", "optimization", "possible optimization", etc.)
    - msg is the content of the annotation
    */
    public void annotation(String fileName, int lineNumber, String tag, String msg)
    {
        annotations.add(fileName + "\t" + lineNumber + "\t" + tag + "\t" + msg);
    }
    
    
    public List<String> getAnnotations()
    {
        return annotations;
    }

}
