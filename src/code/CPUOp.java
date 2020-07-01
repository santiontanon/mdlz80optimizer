/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.ArrayList;
import java.util.List;

public class CPUOp {
    public CPUOpSpec spec;
    public List<Expression> args;
    
    List<CPUOpDependency> inputDeps = null;
    List<CPUOpDependency> outputDeps = null;
    
    
    public CPUOp(CPUOpSpec a_spec, List<Expression> a_args)
    {
        spec = a_spec;
        args = a_args;
    }
    
    
    public CPUOp(CPUOp op)
    {
        spec = op.spec;
        args = new ArrayList<>();
        args.addAll(op.args);
    }
    

    public int sizeInBytes()
    {
        return spec.sizeInBytes;
    }


    public String timeString()
    {
        return spec.timeString();
    }
    
    
    @Override
    public String toString()
    {
        String str = spec.opName;
        
        for(int i = 0;i<args.size();i++) {
            if (i==0) {
                str += " ";
            } else {
                str += ", ";
            }
            str += args.get(i).toString();
        }
        
        return str;
    }
    
    
    public boolean checkInputDependency(CPUOpDependency dep)
    {
        getInputDependencies();
        
//        System.out.println("        inputDeps: " + inputDeps);
        for(CPUOpDependency inDep:inputDeps) {
            if (dep.match(inDep)) {
                return true;
            }
        }
        
        return false;
    }


    /*
    Given a dependency "dep" that comes from an earlier instruction,
    this function removes all the info from "dep" that the current instruction
    overwrites. Returning "null" if "dep" becomes empty after removing info.
    */
    public CPUOpDependency checkOutputDependency(CPUOpDependency dep)
    {
        getOutputDependencies();
        
        // remove from "dep" all the dependencies that are overwritten:
        CPUOpDependency dep2 = new CPUOpDependency(dep);
        for(CPUOpDependency outDep:outputDeps) {
            dep2.remove(outDep);
        }
        
        if (dep2.isEmpty()) return null;
        
        return dep2;
    }
    
    
    public List<CPUOpDependency> getInputDependencies()
    {
        if (inputDeps == null) inputDeps = spec.getInputDependencies(args);
        return inputDeps;
    }

    
    public List<CPUOpDependency> getOutputDependencies()
    {
        if (outputDeps == null) outputDeps = spec.getOutputDependencies(args);
        return outputDeps;
    }


    public Expression getTargetJumpExpression() throws Exception
    {
        int labelArg = spec.jumpLabelArgument();
        if (labelArg == -1) return null;
        Expression targetLabel = args.get(labelArg);
        return targetLabel;
    }

    
    public SourceConstant getTargetJumpLabel(CodeBase code) throws Exception
    {
        int labelArg = spec.jumpLabelArgument();
        if (labelArg == -1) return null;
        Expression targetLabel = args.get(labelArg);
        if (targetLabel.type == Expression.EXPRESSION_SYMBOL) {
            SourceConstant label = code.getSymbol(targetLabel.symbolName);
            return label;
        }
        return null;
    }
    
    
    public boolean isConditional()
    {
        return spec.isConditional();
    }
    
    
    public boolean isRet()
    {
        return spec.isRet();
    }
}
