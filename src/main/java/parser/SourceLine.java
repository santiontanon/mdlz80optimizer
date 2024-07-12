/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser;

import code.SourceFile;
import code.CodeStatement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class SourceLine {
    public String line;
    public SourceFile source;
    public Integer lineNumber;
    public CodeStatement expandedFrom;
    
    public String labelPrefixToPush = null;
    public String labelPrefixToPop = null;
    
    public SourceLine(SourceLine sl)
    {
        line = sl.line;
        source = sl.source;
        lineNumber = sl.lineNumber;
        expandedFrom = sl.expandedFrom;
    }
    
    
    public SourceLine(String a_line, SourceFile a_f, Integer a_ln)
    {
        line = a_line;
        source = a_f;
        lineNumber = a_ln;
        expandedFrom = null;
    }


    public SourceLine(String a_line, SourceFile a_f, Integer a_ln, CodeStatement a_expandedFrom)
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
        String str = source.fileName + "#" + (lineNumber != null ? lineNumber:"-");
        if (expandedFrom != null) {
            str += " (expanded from " + expandedFrom.fileNameLineString() + ")";
        }
        return str;
    }    
    
    
    // Returns true is this occurs earlier than sl in the source code (or if they are the same line)
    public boolean precedesEq(SourceLine sl) 
    {
        if (this == sl) return true;

        List<SourceLine> thisParents = new ArrayList<>();
        List<SourceLine> slParents = new ArrayList<>();
        {
            SourceLine tmp = this;
            while (tmp != null) {
                thisParents.add(tmp);
                if (tmp.source.parentInclude != null) {
                    tmp = tmp.source.parentInclude.sl;
                } else {
                    tmp = null;
                }
            }
            tmp = sl;
            while (tmp != null) {
                slParents.add(tmp);
                if (tmp.source.parentInclude != null) {
                    tmp = tmp.source.parentInclude.sl;
                } else {
                    tmp = null;
                }
            }
        }
        
        // find the closest common file:
        for(SourceLine thisTmp : thisParents) {
            for(SourceLine slTmp: slParents) {
                if (thisTmp.source == slTmp.source) {
                    return thisTmp.lineNumber <= slTmp.lineNumber;
                }
            }
        }
        
        return false;
    }
}
