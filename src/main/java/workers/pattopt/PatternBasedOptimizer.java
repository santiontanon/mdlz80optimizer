/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import parser.Tokenizer;
import util.Resources;
import workers.MDLWorker;

/**
 *
 * @author santi
 */
public class PatternBasedOptimizer implements MDLWorker {

    public static class OptimizationResult {
        public int bytesSaved = 0;
        public int patternApplications = 0;

        public void aggregate(OptimizationResult r) {
            bytesSaved += r.bytesSaved;
            patternApplications += r.patternApplications;
        }
    }


    MDLConfig config;
    boolean activate = false;
    boolean silent = false;
    boolean logPatternsMatchedWithViolatedConstraints = false;
    String inputPatternsFileName = "data/pbo-patterns.txt";
    List<Pattern> patterns = new ArrayList<>();


    public PatternBasedOptimizer(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        return "  -po: Runs the pattern-based optimizer.\n" +
               "  -posilent: Supresses the pattern-based-optimizer output\n" +
               "  -popotential: Reports lines where a potential optimization was not applied for safety, but could maybe be done manually.\n" +
               "  -popatterns <file>: specifies the file to load optimization patterns from (default 'data/pbo-patterns.txt').\n";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-po")) {
            flags.remove(0);
            activate = true;
            return true;
        }
        if (flags.get(0).equals("-posilent")) {
            flags.remove(0);
            silent = true;
            return true;
        }
        if (flags.get(0).equals("-popotential")) {
            flags.remove(0);
            logPatternsMatchedWithViolatedConstraints = true;
            return true;
        }
        if (flags.get(0).equals("-popatterns") && flags.size()>=2) {
            flags.remove(0);
            inputPatternsFileName = flags.remove(0);
            return true;
        }
        return false;
    }


    void initPatterns()
    {
        try (BufferedReader br = Resources.asReader(inputPatternsFileName)) {

            String patternString = "";
            while(true) {
                String line = br.readLine();
                if (line == null) {
                    if (!patternString.equals("")) {
                        patterns.add(new Pattern(patternString, config));
                    }
                    break;
                }
                line = line.trim();
                // ignore comments:
                if (Tokenizer.isSingleLineComment(line)) continue;

                if (line.equals("")) {
                    if (!patternString.equals("")) {
                        patterns.add(new Pattern(patternString, config));
                        patternString = "";
                    }
                } else {
                    patternString += line + "\n";
                }
            }
        } catch (Exception e) {
            config.error("PatternBasedOptimizer: error initializing patterns! " + e);
        }
    }


    @Override
    public boolean work(CodeBase code) {
        if (activate) optimize(code);
        return true;
    }


    public OptimizationResult optimize(CodeBase code) {
        initPatterns();
        OptimizationResult r = new OptimizationResult();
        for(Pattern patt: patterns) {
            OptimizationResult r2 = optimizeWithPattern(patt, code);
            r.aggregate(r2);
        }

        if (r.patternApplications > 0) {
            code.resetAddresses();
        }

        config.info("PatternBasedOptimizer: "+r.patternApplications+" patterns applied, "+r.bytesSaved+" bytes saved");
        return r;
    }

    public OptimizationResult optimizeWithPattern(Pattern patt, CodeBase code) {
        OptimizationResult r = new OptimizationResult();
        for (SourceFile f : code.getSourceFiles()) {
            for (int i = 0; i < f.getStatements().size(); i++) {
                PatternMatch match = patt.match(i, f, code, config, logPatternsMatchedWithViolatedConstraints);
                if (match == null) {
                    continue;
                }

                int lineNumber = f.getStatements().get(i).lineNumber;
                int startIndex = i;
                int endIndex = startIndex;
                for(int id:match.opMap.keySet()) {
                    int idx = f.getStatements().indexOf(match.opMap.get(id));
                    if (idx > endIndex) endIndex = idx;
                }
                SourceStatement endStatement = null;
                if (f.getStatements().size()>endIndex+1) {
                    endStatement = f.getStatements().get(endIndex+1);
                }

                if (patt.apply(f.getStatements(), match)) {
                    if (config.isInfoEnabled()) {
                        config.info("Pattern-based optimization", f.fileName, lineNumber, 
                                patt.name+" ("+patt.getSpaceSaving(match)+" bytes saved)");

                        if (config.isDebugEnabled()) {
                            StringBuilder previousCode = new StringBuilder();
                            for(int line = startIndex;line<endIndex;line++) {
                                previousCode
                                        .append('\n')
                                        .append(f.getStatements().get(line).toString());
                            }

                            StringBuilder newCode = new StringBuilder();
                            endIndex = f.getStatements().size();
                            if (endStatement != null) endIndex = f.getStatements().indexOf(endStatement);
                            for(int line = startIndex;line<endIndex;line++) {
                                newCode
                                        .append('\n')
                                        .append(f.getStatements().get(line).toString());
                            }

                            config.debug(previousCode + "\nReplaced by:" + newCode);
                        }
                    }
                    r.patternApplications++;
                    r.bytesSaved += patt.getSpaceSaving(match);
                    
                    i--;    // re-check this statement, as more optimizations might chain
                }
            }
        }
        return r;
    }

}
