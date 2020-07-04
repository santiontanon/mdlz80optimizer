/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

public enum MDLLogger {

    /** Singleton instance */
    INSTANCE;

    /**
     * @return the actual SLF4J logger
     */
    public static Logger logger() {
        return INSTANCE.logger;
    }

    /** The SLF4J Logger */
    private Logger logger = LoggerFactory.getLogger(MDLLogger.class);

    private Stack<Level> minLevelToLogStack = new Stack<>();

    private List<String> annotations = new ArrayList<>();

    public void setMinLevelToLog(Level a_minLevelToLog)
    {
        ch.qos.logback.classic.Logger.class.cast(this.logger).setLevel(a_minLevelToLog);
    }

    public void silence()
    {
        Level currentLevel = ch.qos.logback.classic.Logger.class.cast(this.logger).getLevel();
        this.minLevelToLogStack.push(currentLevel);
        setMinLevelToLog(Level.OFF);
    }

    public void resume()
    {
        setMinLevelToLog(this.minLevelToLogStack.pop());
    }

    /**
     * Records a message that will be written to the annotations file (for later loading
     * into editors and provide, for example, in0editor optimization hints).
     * @param fileName the file where to show the annotation in editor
     * @param lineNumber the line number where to show the annotation in editor
     * @param tag the type of annotation (e.g.: "warning", "optimization", "possible optimization", etc.)
     * @param msg the content of the annotation
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
