/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser;

import code.SourceFile;
import code.SourceStatement;

/**
 *
 * @author santi
 */
public class SourceLine {
    public String line;
    public SourceFile source;
    public Integer lineNumber;
    public SourceStatement expandedFrom;
    
    public SourceLine(String a_line, SourceFile a_f, Integer a_ln)
    {
        line = a_line;
        source = a_f;
        lineNumber = a_ln;
        expandedFrom = null;
    }


    public SourceLine(String a_line, SourceFile a_f, Integer a_ln, SourceStatement a_expandedFrom)
    {
        line = a_line;
        source = a_f;
        lineNumber = a_ln;
        expandedFrom = a_expandedFrom;
    }

    
    @Override
    public String toString()
    {
        return fileNameLineString()+": " + line;
    }
    
    
    public String fileNameLineString()
    {
        String str = source.fileName + "#" + lineNumber;
        if (expandedFrom != null) {
            str += " (expanded from " + expandedFrom.fileNameLineString() + ")";
        }
        return str;
    }    
}
