/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser;

import code.CodeStatement;
import java.util.List;

/**
 *
 * @author santi
 */
public class MacroExpansion {
    public SourceMacro sm = null;
    public TextMacro tm = null;
    public CodeStatement macroCall;
    public List<SourceLine> lines;

    public MacroExpansion(SourceMacro a_m, CodeStatement a_mc, List<SourceLine> a_lines)
    {
        sm = a_m;
        macroCall = a_mc;
        lines = a_lines;
    }


    public MacroExpansion(TextMacro a_m, CodeStatement a_mc, List<SourceLine> a_lines)
    {
        tm = a_m;
        macroCall = a_mc;
        lines = a_lines;
    }
}
