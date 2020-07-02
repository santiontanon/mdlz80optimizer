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
    - Parses command-line flags.
    - It should just start parsing from the beginning of the list, and pop those flags it can recognize.
    - Returns true if it parsed any flag, and false if it didn't
    */
    public boolean parseFlag(List<String> flags);


    public boolean work(CodeBase code);

}
