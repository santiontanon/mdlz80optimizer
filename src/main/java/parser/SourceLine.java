/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser;

import code.SourceFile;

/**
 *
 * @author santi
 */
public class SourceLine {
    public String line;
    public SourceFile source;
    public Integer lineNumber;
    
    
    public SourceLine(String a_line, SourceFile a_f, Integer a_ln)
    {
        line = a_line;
        source = a_f;
        lineNumber = a_ln;
    }
}