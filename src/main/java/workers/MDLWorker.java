/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import code.CodeBase;
import java.util.List;

public interface MDLWorker {
    /*
    Text explaining how to use the flags to configure this generator
    */
    public String docString();

    
    /*
    Text explaining how to use the flags to configure this generator
    */
    public String simpleDocString();

    /*
    - Parses command-line flags.
    - It should just start parsing from the beginning of the list, and pop those flags it can recognize.
    - Returns true if it parsed any flag, and false if it didn't
    */
    public boolean parseFlag(List<String> flags);
    
    /*
    Returns true, after having parsed an argument that triggers the execution of this worker
    */
    public boolean triggered();
    
    public boolean work(CodeBase code);
}
