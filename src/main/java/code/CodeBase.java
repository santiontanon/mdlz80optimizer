/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;

public class CodeBase {
    public static final String CURRENT_ADDRESS = "$";

    MDLConfig config;

    SourceFile main;
    LinkedHashMap<String, SourceFile> sources = new LinkedHashMap<>();
    LinkedHashMap<String, SourceConstant> symbols = new LinkedHashMap<>();


    public CodeBase(MDLConfig a_config)
    {
        config = a_config;
    }


    public boolean isRegister(String name)
    {
        String registers[] = {"a", "b", "c", "d", "e", "h","l",
                              "af", "bc", "de", "hl",
                              "sp", "ix", "iy", "pc",
                              "ixl", "ixh", "iyl", "iyh",
                              "af'",
                              "i", "r"};
        for(String reg:registers) {
            if (name.equalsIgnoreCase(reg)) return true;
        }

        return false;
    }


    public boolean isCondition(String name)
    {
        String conditions[] = {"c", "m", "nc", "nz", "p", "pe", "po", "z"};
        for(String c:conditions) {
            if (name.equalsIgnoreCase(c)) return true;
        }

        return false;
    }


    public SourceConstant getSymbol(String name)
    {
        if (symbols.containsKey(name)) {
            return symbols.get(name);
        }
        return null;
    }


    public Set<String> getSymbols()
    {
        return symbols.keySet();
    }


    public Integer getSymbolValue(String name, boolean silent)
    {
        if (symbols.containsKey(name)) {
            return symbols.get(name).getValue(this, silent);
        }
        return null;
    }


    public boolean addSymbol(String name, SourceConstant sc)
    {
        if (symbols.containsKey(name)) {
            if (symbols.get(name).exp != null) {
                config.error("Redefining symbol " + name);
                config.error("First defined in " + symbols.get(name).s.source.fileName + ", " + symbols.get(name).s.lineNumber + " as " + symbols.get(name).exp + ": " +  symbols.get(name).s);
                config.error("Redefined in " + sc.s.source.fileName + ", "+ sc.s.lineNumber + " as " + symbols.get(name).exp + ": " + sc.s);
                return false;
            }
        }
        symbols.put(name, sc);
        return true;
    }


    public Collection<SourceFile> getSourceFiles()
    {
        return sources.values();
    }


    public SourceFile getSourceFile(String fileName)
    {
        if (sources.containsKey(fileName)) return sources.get(fileName);
        return null;
    }


    public void addSourceFile(SourceFile s)
    {
        sources.put(s.fileName, s);
    }


    public void resetAddresses()
    {
        for(SourceFile f:sources.values()) {
            f.resetAddresses();
        }
        for(SourceConstant c:symbols.values()) {
            c.valueCache = null;
        }
    }


    public void setMain(SourceFile s)
    {
        main = s;
    }


    public SourceFile getMain()
    {
        return main;
    }

}
