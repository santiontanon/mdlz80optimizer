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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
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
        public int timeSaved[] = {0,0}; // some instructions have two times (e.g., if a condition is met or not)
        public int patternApplications = 0;

        public void aggregate(OptimizationResult r) {
            bytesSaved += r.bytesSaved;
            patternApplications += r.patternApplications;
            timeSaved[0] += r.timeSaved[0];
            timeSaved[1] += r.timeSaved[1];
        }
        
        
        public String timeString() {
            if (timeSaved[0] == timeSaved[1]) {
                return ""+timeSaved[0];
            } else {
                return timeSaved[0] + "/" + timeSaved[1];
            }        
        }
    }
    

    MDLConfig config;
    boolean activate = false;
    boolean silent = false;
    boolean logPatternsMatchedWithViolatedConstraints = false;
    String inputPatternsFileName = "data/pbo-patterns.txt";
    List<Pattern> patterns = new ArrayList<>();
    
    // Some optimizations depend on certain labels to have specific values. After applying them,
    // we need to ensure that other optimizations to not change those values and make the code incorrect.
    // These lists accumulate conditions that previous optimizations assume, to make sure subsequent 
    // optimizations are still safe:
    List<EqualityConstraint> equalitiesToMaintain = new ArrayList<>();


    public PatternBasedOptimizer(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        return "  -po: Runs the pattern-based optimizer. You can pass an optimal parameter, like '-po size' or '-po speed', which are shortcuts for '-po -popatterns data/pbo-patterns-size.txt' and '-po -popatterns data/pbo-patterns-speed.txt'\n" +
               "  -posilent: Supresses the pattern-based-optimizer output\n" +
               "  -popotential: Reports lines where a potential optimization was not applied for safety, but could maybe be done manually.\n" +
               "  -popatterns <file>: specifies the file to load optimization patterns from (default 'data/pbo-patterns.txt', " +
                                     "which contains patterns that optimize both size and speed). For targetting size optimizations, use " +
                                     "'data/pbo-patterns-size.txt'.\n";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-po")) {
            flags.remove(0);
            if (!flags.isEmpty()) {
                if (flags.get(0).equals("size")) {
                    inputPatternsFileName = "data/pbo-patterns-size.txt";
                    flags.remove(0);
                } else if (flags.get(0).equals("speed")) {
                    inputPatternsFileName = "data/pbo-patterns-speed.txt";
                    flags.remove(0);
                }
            }
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
        loadPatterns(inputPatternsFileName);
    }
    
    
    void loadPatterns(String fileName) 
    {
        config.debug("Loading patterns from " + fileName);
        try (BufferedReader br = Resources.asReader(fileName)) {
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
                    if (line.startsWith("include")) {
                        List<String> tokens = Tokenizer.tokenize(line);
                        if (tokens.size()>=2) {
                            String name = tokens.get(1);
                            if (Tokenizer.isString(name)) {
                                // include another pattern file:
                                name = name.substring(1, name.length()-1);
                                String path = config.lineParser.pathConcat(FilenameUtils.getFullPath(fileName), name);
                                loadPatterns(path);
                            } else {
                                config.error("Problem loading patterns in line: " + line);
                            }
                        } else {
                            config.error("Problem loading patterns in line: " + line);
                        }
                    } else {
                        patternString += line + "\n";
                    }
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
        List<Pair<Pattern,PatternMatch>> matches = new ArrayList<>();
        
        for (SourceFile f : code.getSourceFiles()) {
            for (int i = 0; i < f.getStatements().size(); i++) {
                matches.clear();
                for(Pattern patt: patterns) {
                    PatternMatch match = patt.match(i, f, code, config, logPatternsMatchedWithViolatedConstraints);
                    if (match != null) matches.add(Pair.of(patt,match));
                }

                if (!matches.isEmpty()) {
                    // there was at least a match, pick the best!
                    Pattern bestPatt = null;
                    PatternMatch bestMatch = null;
                    int bestSavings = 0;    // selection is based on bytes saved
                    for(Pair<Pattern,PatternMatch> p:matches) {
                        int savings = p.getLeft().getSpaceSaving(p.getRight(), code);
                        if (bestPatt == null || savings > bestSavings) {
                            bestPatt = p.getLeft();
                            bestMatch = p.getRight();
                            bestSavings = savings;
                        }
                    }
                    
                    SourceStatement statementToDisplayMessageOn = null;
                    int startIndex = i;
                    int endIndex = startIndex;
                    for(int id:bestMatch.map.keySet()) {
                        if (id == 0) statementToDisplayMessageOn = bestMatch.map.get(id).get(0);
                        for(SourceStatement s:bestMatch.map.get(id)) {
                            int idx = f.getStatements().indexOf(s);
                            if (idx > endIndex) endIndex = idx;
                        }
                    }
                    if (statementToDisplayMessageOn == null) {
                        config.warn("Could not identify the statement to display the optimization message on...");
                        statementToDisplayMessageOn = f.getStatements().get(i);
                    }
                    SourceStatement endStatement = null;
                    if (f.getStatements().size()>endIndex+1) {
                        endStatement = f.getStatements().get(endIndex+1);
                    }

                    if (bestPatt.apply(f.getStatements(), bestMatch, code, equalitiesToMaintain)) {
                        if (config.isInfoEnabled()) {
                            int bytesSaved = bestPatt.getSpaceSaving(bestMatch, code);
                            String timeSavedString = bestPatt.getTimeSavingString(bestMatch, code);
                            config.info("Pattern-based optimization", statementToDisplayMessageOn.fileNameLineString(), 
                                    bestPatt.getInstantiatedName(bestMatch)+" ("+bytesSaved+" bytes, " +
                                    timeSavedString + " " +config.timeUnit+"s saved)");

                            if (config.isDebugEnabled()) {
                                StringBuilder previousCode = new StringBuilder();
                                for(int line = startIndex;line<endIndex;line++) {
                                    previousCode.append('\n')
                                                .append(f.getStatements().get(line).toString());
                                }

                                StringBuilder newCode = new StringBuilder();
                                endIndex = f.getStatements().size();
                                if (endStatement != null) endIndex = f.getStatements().indexOf(endStatement);
                                for(int line = startIndex;line<endIndex;line++) {
                                    newCode.append('\n')
                                           .append(f.getStatements().get(line).toString());
                                }

                                config.debug(previousCode + "\nReplaced by:" + newCode);
                            }
                        }
                        r.patternApplications++;
                        r.bytesSaved += bestPatt.getSpaceSaving(bestMatch, code);
                        r.timeSaved[0] += bestPatt.getTimeSaving(bestMatch, code)[0];
                        r.timeSaved[1] += bestPatt.getTimeSaving(bestMatch, code)[1];
                        i--;    // re-check this statement, as more optimizations might chain
                    }
                }
            }
        }        

        config.info("PatternBasedOptimizer: "+r.patternApplications+" patterns applied, " +
                    r.bytesSaved+" bytes, " + 
                    r.timeString() + " " +config.timeUnit+"s saved.");
        return r;
    }
}
