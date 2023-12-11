/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;
import code.SourceConstant;
import java.util.ArrayList;
import java.util.HashSet;
import org.apache.commons.lang3.tuple.Pair;

public class DotGenerator implements MDLWorker {
    public static final String BINARY_COLOR = "gray";
    public static final int GRAPH_SOURCE_FILES = 0;
    public static final int GRAPH_SOURCE_FUNCTIONS = 1;

    MDLConfig config = null;
    int graphType = GRAPH_SOURCE_FILES;
    String outputFileName = null;
    boolean groupCallGraphByFiles = true;
    boolean useHTMLNodes = true;  // only affects call graphs

    public DotGenerator(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString()
    {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-dot <output file>```: generates a dot file with a graph " +
               "representing the whole source code with one vertex per source file. " + 
               "Convert it to a png using 'dot' like this: " +
               "```dot -Tpng <output file>.dot -o <output file>.png```\n" +
               "- ```-dot-cg <output file>```: generates a dot file with the call " +
               "graph of the whole code base. MDL will try to identify individual " +
               "functions in the code, but some might be missed.\n";
    }


    @Override
    public String simpleDocString() {
        return "";
    }
    
    
    @Override
    public boolean parseFlag(List<String> flags)
    {
        if (flags.get(0).equals("-dot") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            graphType = GRAPH_SOURCE_FILES;
            return true;
        }
        if (flags.get(0).equals("-dot-cg") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            graphType = GRAPH_SOURCE_FUNCTIONS;
            return true;
        }
        return false;
    }


    @Override
    public boolean work(CodeBase code)
    {
        if (outputFileName == null) return true;

        config.debug("Executing "+this.getClass().getSimpleName()+" worker...");

        StringBuilder sb = new StringBuilder();

        sb.append("digraph codeanalysis {\n");
        sb.append("graph[rankdir=LR];\n");
        sb.append("nodesep=0.5;\n");
        
        switch(graphType) {
            case GRAPH_SOURCE_FILES:
                sourceFileGraph(code, sb);
                break;
            case GRAPH_SOURCE_FUNCTIONS:
                callGraph(code, sb);
                break;
            default:
                config.error("DotGenerator: Unrecognized graph type: " + graphType);
        }
        sb.append("}");

        try (FileWriter fw = new FileWriter(outputFileName)) {
            fw.write(sb.toString());
            fw.flush();
        } catch (Exception e) {
            config.error("Cannot write to file " + outputFileName);
            return false;
        }
        return true;
    }
    
    
    public void sourceFileGraph(CodeBase code, StringBuilder sb)
    {
        HashMap<String, String> nodeNames = new HashMap<>();
        
        // vertices:
        for(SourceFile f : code.getSourceFiles()) {
            String sName = "" + (nodeNames.size()+1);
            nodeNames.put(f.fileName, sName);

            sb.append(sName);
            sb.append(" [shape=record label=\"");
            sb.append(sourceFileDotContent(f, code));
            sb.append("\"]\n");

            for(CodeStatement s : f.getStatements()) {
                if (config.includeBinariesInAnalysis) {
                    if (s.type == CodeStatement.STATEMENT_INCBIN) {
                        sName = "" + (nodeNames.size()+1);
                        nodeNames.put(s.incbin.getName(), sName);

                        sb.append(sName);
                        sb.append(" [style=filled fillcolor=");
                        sb.append(BINARY_COLOR);
                        sb.append(" shape=record label=\"{{name:|");
                        sb.append(s.incbin);
                        sb.append("}|{size:|");
                        sb.append(s.incbinSize);
                        sb.append("}}}");
                        sb.append("\"]\n");
                    }
                }
            }
        }

        // edges:
        for(SourceFile f: code.getSourceFiles()) {
            for(CodeStatement s : f.getStatements()) {
                if (s.type == CodeStatement.STATEMENT_INCLUDE) {
                    sb.append(nodeNames.get(f.fileName));
                    sb.append(" -> ");
                    sb.append(nodeNames.get(s.include.fileName));
                    sb.append("\n");
                } else if (s.type == CodeStatement.STATEMENT_INCBIN) {
                    if (config.includeBinariesInAnalysis) {
                        sb.append(nodeNames.get(f.fileName));
                        sb.append(" -> ");
                        sb.append(nodeNames.get(s.incbin.getName()));
                        sb.append("\n");
                    }
                }
            }
        }        
    }


    String sourceFileDotContent(SourceFile f, CodeBase code)
    {
        String str = "{{";
        str += "{name:|" + f.fileName + "}|";
        str += "{size(self):|" + f.sizeInBytes(code, false, false, false) + "}|";
        str += "{size(total):|" + f.sizeInBytes(code, true, true, false) + "}|";
        str += "{"+config.timeUnit+"s(self):|" + f.accumTimingString() + "}";
        str += "}}";

        return str;
    }
    
    
    public void callGraph(CodeBase code, StringBuilder sb)
    {
        HashMap<String, String> nodeNames = new HashMap<>();
        HashMap<CodeStatement, String> statementsToFunctions = new HashMap<>();
        HashMap<SourceConstant, String> labelsToFunctions = new HashMap<>();
        List<Pair<CodeStatement, CodeStatement>> allfunctions = new ArrayList<>();
        HashSet<String> existingEdges = new HashSet<>();
        
        SourceCodeTableGenerator sctg = new SourceCodeTableGenerator(config);
        int fileIndex = 0;
        for(SourceFile f: code.getSourceFiles()) {
            List<Pair<CodeStatement, CodeStatement>> functions = sctg.autodetectFunctions(f, code);
            if (functions.isEmpty()) continue;
            
            if (groupCallGraphByFiles) {
                sb.append("subgraph cluster");
                sb.append(fileIndex);
                fileIndex++;
                sb.append(" {\n");
                sb.append("label = \"");
                sb.append(f.fileName);
                sb.append("\"\n");
                sb.append("style = filled;\n");
                sb.append("fillcolor = \"#e0e0e0\";\n");
            }

            for(Pair<CodeStatement, CodeStatement> function:functions) {
                SourceConstant functionName = null;
                int size = 0;
                for(int i = f.getStatements().indexOf(function.getLeft());
                        i <= f.getStatements().indexOf(function.getRight()); 
                        i++) {
                    CodeStatement s = f.getStatements().get(i);
                    if (functionName == null) {
                        if (s.label == null) {
                            config.error("First statement of an autodetected function is not a label!");
                            break;
                        }
                        functionName = s.label;
                    }

                    if (s.op != null) size += s.op.spec.sizeInBytes;
                }
                if (functionName == null) continue;

                String sName = "" + (nodeNames.size()+1);
                nodeNames.put(functionName.name, sName);   
                allfunctions.add(function);
                
                // Information necessary for creating vertices:
                for(int i = f.getStatements().indexOf(function.getLeft());
                        i <= f.getStatements().indexOf(function.getRight()); 
                        i++) {
                    CodeStatement s = f.getStatements().get(i);
                    statementsToFunctions.put(s, sName);
                    if (s.label != null) {
                        labelsToFunctions.put(s.label, sName);
                    }
                }
                sb.append(sName);
                if (useHTMLNodes) {
                    sb.append("[label=<\n");
                    sb.append("<TABLE><TR>");
                    sb.append("<TD>name: ");
                    sb.append(functionName.name);
                    sb.append("</TD>\n");
                    if (!groupCallGraphByFiles) {
                        sb.append("<TD>file: ");
                        sb.append(f.fileName);
                        sb.append("</TD>");
                    }
                    sb.append("<TD>size: ");
                    sb.append(size);
                    sb.append("</TD>");
                    sb.append("</TR></TABLE>>]\n");                    
                } else {
                    sb.append(" [shape=record label=\"");
                    sb.append("{{");
                    sb.append("{name:|");
                    sb.append(functionName.name);
                    sb.append("}|");
                    if (!groupCallGraphByFiles) {
                        sb.append("{file:|");
                        sb.append(f.fileName);
                        sb.append("}|");
                    }
                    sb.append("{size:|");
                    sb.append(size);
                    sb.append("}}}\"]\n");
                }
            }
            
            if (groupCallGraphByFiles) {
                sb.append("}\n");
            }
        }
        
        // Edges:
        for(Pair<CodeStatement, CodeStatement> function:allfunctions) {
            SourceFile f = function.getLeft().source;
            for(int i = f.getStatements().indexOf(function.getLeft());
                    i <= f.getStatements().indexOf(function.getRight()); 
                    i++) {
                CodeStatement s = f.getStatements().get(i);
                if (s.op == null) continue;
                SourceConstant l = s.op.getTargetJumpLabel(code);
                if (l == null) continue;
                String sourceNode = statementsToFunctions.get(s);
                String targetNode = labelsToFunctions.get(l);
                if (targetNode != null && sourceNode != null) {
                    if (targetNode.equals(sourceNode)) continue;
                    String edgeName = sourceNode + "-" + targetNode;
                    if (existingEdges.contains(edgeName)) continue;
                    existingEdges.add(edgeName);
                    sb.append(sourceNode);
                    sb.append(" -> ");
                    sb.append(targetNode);
                    sb.append("\n");
                }
            }
        }
    }
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    }  
}
