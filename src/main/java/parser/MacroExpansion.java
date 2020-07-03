/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser;

import code.SourceStatement;
import java.util.List;

/**
 *
 * @author santi
 */
public class MacroExpansion {
    public SourceMacro m;
    public SourceStatement macroCall;
    public List<SourceLine> lines;

    public MacroExpansion(SourceMacro a_m, SourceStatement a_mc, List<SourceLine> a_lines)
    {
        m = a_m;
        macroCall = a_mc;
        lines = a_lines;
    }
}
