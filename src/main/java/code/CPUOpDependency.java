/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package code;

/**
 *
 * @author santi
 */
public class CPUOpDependency {
    public String register = null;
    public String flag = null;
    public String port = null;
    public String memoryStart = null;
    public String memoryEnd = null;
    
    public CPUOpDependency(String a_reg, String a_flag, String a_port, String a_mem1, String a_mem2)
    {
        register = a_reg;
        flag = a_flag;
        port = a_port;
        memoryStart = a_mem1;
        memoryEnd = a_mem2;
    }


    public CPUOpDependency(CPUOpDependency dep)
    {
        register = dep.register;
        flag = dep.flag;
        port = dep.flag;
        memoryStart = dep.memoryStart;
        memoryEnd = dep.memoryEnd;
    }    
    
    
    public boolean isEmpty()
    {
        return register == null && flag == null && port == null && 
               memoryStart == null && memoryEnd == null;
    }
    
    
    public boolean equals(Object o)
    {
        if (!(o instanceof CPUOpDependency)) return false;
        CPUOpDependency dep = (CPUOpDependency)o;
        if (register == null && dep.register != null) return false;
        if (register != null && !register.equals(dep.register)) return false;
        if (flag == null && dep.flag != null) return false;
        if (flag != null && !flag.equals(dep.flag)) return false;
        if (port == null && dep.port != null) return false;
        if (port != null && !port.equals(dep.port)) return false;
        if (memoryStart == null && dep.memoryStart != null) return false;
        if (memoryStart != null && !memoryStart.equals(dep.memoryStart)) return false;
        if (memoryEnd == null && dep.memoryEnd != null) return false;
        if (memoryEnd != null && !memoryEnd.equals(dep.memoryEnd)) return false;
        return true;
    }
        
    
    @Override
    public String toString() {
        if (register != null) return "reg:" + register;
        if (flag != null) return "flag:" + flag;
        if (port != null) return "port:" + port;
        return "mem:["+memoryStart+":"+memoryEnd+"]";
    }
    
    
    public boolean match(CPUOpDependency dep)
    {
        if (register != null && dep.register != null) {
            if (register.equals(dep.register)) return true;
            // match register pairs with their invididual parts:
            if (register.equals("A") && dep.register.equals("AF")) return true;
            if (register.equals("AF") && dep.register.equals("A")) return true;
            if ((register.equals("B") || register.equals("C")) && dep.register.equals("BC")) return true;
            if ((dep.register.equals("B") || dep.register.equals("C")) && register.equals("BC")) return true;
            if ((register.equals("D") || register.equals("E")) && dep.register.equals("DE")) return true;
            if ((dep.register.equals("D") || dep.register.equals("E")) && register.equals("DE")) return true;
            if ((register.equals("H") || register.equals("L")) && dep.register.equals("HL")) return true;
            if ((dep.register.equals("H") || dep.register.equals("L")) && register.equals("HL")) return true;
            if ((register.equals("IXL") || register.equals("IXH")) && dep.register.equals("IX")) return true;
            if ((dep.register.equals("IXL") || dep.register.equals("IXH")) && register.equals("IX")) return true;
            if ((register.equals("IYL") || register.equals("IYH")) && dep.register.equals("IY")) return true;
            if ((dep.register.equals("IYL") || dep.register.equals("IYH")) && register.equals("IY")) return true;
            return false;
        }
        if (flag != null) return flag.equals(dep.flag);
        if (port != null) return port.equals(dep.port);
        if (memoryStart != null) {
            // for now always match memory dependencies:
            // ...
            return dep.memoryStart != null;
        }
        if (flag != null && dep.register != null) {
            if (dep.register.equals("AF")) return true;
        }
        if (dep.flag != null && register != null) {
            if (register.equals("AF")) return true;
        }
        return false;
    }
    
    
    public void remove(CPUOpDependency dep)
    {
        if (register != null && dep.register != null) {
            if (register.equals(dep.register)) {
                register = null;
            }
        }
        if (register != null && dep.register != null) {
            // match register pairs with their invididual parts:
            if (register.equals("A") && dep.register.equals("AF")) register = null;
            if (register != null && register.equals("AF") && dep.register.equals("A")) {
                //only A is modified, but we still have a dependency going forwawrd on "F"
                register = "F";
            }
            if (register != null && (register.equals("B") || register.equals("C")) && dep.register.equals("BC")) {
                register = null;
            }
            if (register != null && (register.equals("D") || register.equals("E")) && dep.register.equals("DE")) {
                register = null;
            }
            if (register != null && (register.equals("H") || register.equals("L")) && dep.register.equals("HL")) {
                register = null;
            }
            if (register != null && (register.equals("IXL") || register.equals("IXH")) && dep.register.equals("IX")) {
                register = null;
            }
            if (register != null && (register.equals("IYL") || register.equals("IYH")) && dep.register.equals("IY")) {
                register = null;
            }

            if (register != null && dep.register.equals("B") && register.equals("BC")) register = "C";
            if (register != null && dep.register.equals("C") && register.equals("BC")) register = "B";
            if (register != null && dep.register.equals("D") && register.equals("DE")) register = "E";
            if (register != null && dep.register.equals("E") && register.equals("DE")) register = "D";
            if (register != null && dep.register.equals("H") && register.equals("HL")) register = "L";
            if (register != null && dep.register.equals("L") && register.equals("HL")) register = "H";
            if (register != null && dep.register.equals("IXL") && register.equals("IX")) register = "IXH";
            if (register != null && dep.register.equals("IXH") && register.equals("IX")) register = "IXL";
            if (register != null && dep.register.equals("IYL") && register.equals("IY")) register = "IYH";
            if (register != null && dep.register.equals("IYH") && register.equals("IY")) register = "IYL";
        }
        if (flag != null && flag.equals(dep.flag)) {
            flag = null;
        }
        if (port != null && port.equals(dep.port)) {
            port = null;
        }
        if (memoryStart != null && dep.memoryStart != null) {
            // for now always match memory dependencies:
            // ...
            memoryStart = null;
            memoryEnd = null;
        }
        if (flag != null && dep.register != null) {
            if (dep.register.equals("AF")) {
                dep.flag = null;
            }
        }
        if (dep.flag != null && register != null) {
            if (register.equals("F")) {
                register = null;
            }
        }
        
    }
    
}
