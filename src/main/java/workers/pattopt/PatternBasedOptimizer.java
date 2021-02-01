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
import java.io.FileReader;
import java.io.FileWriter;
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

    public static String defaultInputPatternsFileName = "data/pbo-patterns.txt";
    public static String defaultInputPatternsSizeFileName = "data/pbo-patterns-size.txt";
    public static String defaultInputPatternsSpeedFileName = "data/pbo-patterns-speed.txt";
    
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
    

    public boolean logPotentialOptimizations = false;    
    public boolean generateFilesWithAppliedOptimizations = false;
    public boolean onlyOnePotentialOptimizationPerLine = true;
    
    MDLConfig config;
    boolean trigger = false;
    boolean silent = false;
    String inputPatternsFileName = null;
    List<Pattern> patterns = new ArrayList<>();
    
    // Some optimizations depend on certain labels to have specific values. After applying them,
    // we need to ensure that other optimizations to not change those values and make the code incorrect.
    // These lists accumulate conditions that previous optimizations assume, to make sure subsequent 
    // optimizations are still safe:
    List<EqualityConstraint> equalitiesToMaintain = new ArrayList<>();
    public boolean alreadyShownAPotentialOptimization = false;
    
    List<PatternMatch> appliedOptimizations = new ArrayList<>();


    public PatternBasedOptimizer(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        return "  -po: (task) Runs the pattern-based optimizer using the latest settings. Notice that the -posilent, -poapply, etc. flags need to be passed *before* the call to -po that they which to affect and which is the one that triggers the optimizer. You can pass an optional parameter, like '-po size' or '-po speed', which are shortcuts for '-po -popatterns data/pbo-patterns-size.txt' and '-po -popatterns data/pbo-patterns-speed.txt' (some dialects might change the defaults of these two)\n" +
               "  -posilent: Supresses the pattern-based-optimizer output\n" +
               "  -poapply: For each assembler <file> parsed by MDL, a corresponding <file>.mdl.asm is generated with the optimizations applied to it.\n" + 
               "  -popotential: Reports lines where a potential optimization was not applied for safety, but could maybe be done manually (at most one potential optimization per line is shown).\n" +
               "  -popotential-all: Same as above, but without the one-per-line constraint.\n" +
               "  -popatterns <file>: specifies the file to load optimization patterns from (default 'data/pbo-patterns.txt', " +
                                     "which contains patterns that optimize both size and speed). For targetting size optimizations, use " +
                                     "'data/pbo-patterns-size.txt'. Notice that some dialects might change the default, for example, the " +
                                     "sdcc dialect sets the default to 'data/pbo-patterns-sdcc-speed.txt'\n";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-po")) {
            flags.remove(0);
            if (!flags.isEmpty()) {
                if (flags.get(0).equals("size")) {
                    inputPatternsFileName = defaultInputPatternsSizeFileName;
                    flags.remove(0);
                } else if (flags.get(0).equals("speed")) {
                    inputPatternsFileName = defaultInputPatternsSpeedFileName;
                    flags.remove(0);
                }
            }
            trigger = true;
            return true;
        }
        if (flags.get(0).equals("-posilent")) {
            flags.remove(0);
            silent = true;
            return true;
        }
        if (flags.get(0).equals("-poapply")) {
            flags.remove(0);
            generateFilesWithAppliedOptimizations = true;
            return true;
        }
        if (flags.get(0).equals("-popotential")) {
            flags.remove(0);
            logPotentialOptimizations = true;
            return true;
        }
        if (flags.get(0).equals("-popotential-all")) {
            flags.remove(0);
            logPotentialOptimizations = true;
            onlyOnePotentialOptimizationPerLine = false;
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
        if (inputPatternsFileName == null) {
            inputPatternsFileName = defaultInputPatternsFileName;
        }
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
        if (trigger) {
            optimize(code);
            if (generateFilesWithAppliedOptimizations) {
                applyOptimizationsToOriginalFiles(code);
            }
        }
        return true;
    }


    public OptimizationResult optimize(CodeBase code) {
        initPatterns();
        OptimizationResult r = new OptimizationResult();
        List<Pair<Pattern,PatternMatch>> matches = new ArrayList<>();
        
        for (SourceFile f : code.getSourceFiles()) {
            for (int i = 0; i < f.getStatements().size(); i++) {
                alreadyShownAPotentialOptimization = false;
                matches.clear();
                for(Pattern patt: patterns) {
                    PatternMatch match = patt.match(i, f, code, this);
                    if (match != null) {
                        if (generateFilesWithAppliedOptimizations) {
                            // prevent matches to lines on which there is already an optimization:
                            boolean overlapsWithPreviousOptimization = false;
                            for(PatternMatch previous:appliedOptimizations) {
                                if (machesLinesGeneratedByPreviousPattern(match, previous)) {
                                    overlapsWithPreviousOptimization = true;
                                    break;
                                }
                            }
                            if (!overlapsWithPreviousOptimization) {
                                matches.add(Pair.of(patt,match));
                            } else {
                                config.debug("Prevented an optimization!");
                            }
                        } else {
                            matches.add(Pair.of(patt,match));
                        }
                    }
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
//                    SourceStatement endStatement = null;
//                    if (f.getStatements().size()>endIndex+1) {
//                        endStatement = f.getStatements().get(endIndex+1);
//                    }

                    if (bestPatt.apply(f, bestMatch, code, equalitiesToMaintain)) {
                        if (config.isInfoEnabled()) {
                            int bytesSaved = bestPatt.getSpaceSaving(bestMatch, code);
                            String timeSavedString = bestPatt.getTimeSavingString(bestMatch, code);
                            config.info("Pattern-based optimization", statementToDisplayMessageOn.fileNameLineString(), 
                                    bestPatt.getInstantiatedName(bestMatch)+" ("+bytesSaved+" bytes, " +
                                    timeSavedString + " " +config.timeUnit+"s saved)");

//                            if (config.isDebugEnabled()) {
//                                StringBuilder previousCode = new StringBuilder();
//                                for(int line = startIndex;line<endIndex;line++) {
//                                    previousCode.append('\n')
//                                                .append(f.getStatements().get(line).toString());
//                                }
//
//                                StringBuilder newCode = new StringBuilder();
//                                endIndex = f.getStatements().size();
//                                if (endStatement != null) endIndex = f.getStatements().indexOf(endStatement);
//                                for(int line = startIndex;line<endIndex;line++) {
//                                    newCode.append('\n')
//                                           .append(f.getStatements().get(line).toString());
//                                }
//
//                                config.debug(previousCode + "\nReplaced by:" + newCode);
//                            }
                        }
                        r.patternApplications++;
                        r.bytesSaved += bestPatt.getSpaceSaving(bestMatch, code);
                        r.timeSaved[0] += bestPatt.getTimeSaving(bestMatch, code)[0];
                        r.timeSaved[1] += bestPatt.getTimeSaving(bestMatch, code)[1];
                        i = Math.max(0, i-2);   // go back a couple of statements, as more optimizations might chain
                        
                        appliedOptimizations.add(bestMatch);
                    }
                }
            }
        }        

        config.info("PatternBasedOptimizer: "+r.patternApplications+" patterns applied, " +
                    r.bytesSaved+" bytes, " + 
                    r.timeString() + " " +config.timeUnit+"s saved.");
        return r;
    }
    
    
    public boolean machesLinesGeneratedByPreviousPattern(PatternMatch match, PatternMatch previous)
    {
        if (previous.f == match.f) {
            for(int j = 0;j<match.pattern.pattern.size();j++) {
                if (!match.pattern.pattern.get(j).isWildcard()) {
                    CPUOpPattern pattern = match.pattern.pattern.get(j);
                    int ID = pattern.ID;
                    CPUOpPattern replacement = null;
                    for(CPUOpPattern replacement2:match.pattern.replacement) {
                        if (replacement2.ID == ID) {
                            replacement = replacement2;
                            break;
                        }
                    }
                    if (replacement == null ||
                        !replacement.toString().equals(pattern.toString())) {
                        List<SourceStatement> toRemoveL = match.map.get(ID);
                        for(SourceStatement toRemove:toRemoveL) {
                            for(SourceStatement added:previous.added) {
                                if (toRemove == added) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
    
    
    public boolean applyOptimizationsToOriginalFiles(CodeBase code)
    {
        for(SourceFile f:code.getSourceFiles()) {
            String newFileName = f.fileName + ".mdl.asm";
            config.info("Generating optimized file " + newFileName);
            
            // 1) Read lines:
            // - indexed by original line number, and contains as many lines as that
            //   original turned into
            List<List<String>> lines = new ArrayList<>();
            try {
                BufferedReader br = new BufferedReader(new FileReader(f.fileName));
                while(true) {
                    String line = br.readLine();
                    if (line == null) break;
                    List<String> l = new ArrayList<>();
                    l.add(line);
                    lines.add(l);
                }
            } catch (Exception e) {
                config.error("Error loading " + f.fileName + " when trying to generate a file with optimizations applied to it.");
                return false;
            }
            
            // 2) Apply optimizations:
            for(PatternMatch match: appliedOptimizations) {
                if (match.f == f) {
                    // check that the optimization is not on a macro (do not apply optimizations to macros!):
                    boolean fromMacro = false;
                    for(SourceStatement s: match.removed) {
                        if (s.sl.expandedFrom != null) {
                            // this was expanded from a macro:
                            fromMacro = true;
                            break;
                        }
                    }
                    for(SourceStatement s: match.added) {
                        if (s.sl.expandedFrom != null) {
                            // this was expanded from a macro:
                            fromMacro = true;
                            break;
                        }
                    }

                    if (fromMacro) {
                        config.warn("Optimization '" + match.pattern.getInstantiatedName(match) + "' cannot be applied to the source code (for safety reasons) as it is associated with a macro (MDL cannot guarantee that the resulting optimized file is sound after this point).");
                        continue;
                    }
                    
                    for(SourceStatement s: match.removed) {
                        List<String> updatedLines = lines.get(s.sl.lineNumber-1);
                        if (updatedLines.size() == 1) {
                            updatedLines.add("; " + updatedLines.remove(0) + "  ; -mdl");
                        } else {
                            boolean onlyLastNotCommentedOut = true;
                            for(int i = 0;i<updatedLines.size();i++) {
                                if (i == updatedLines.size()-1) {
                                    if (updatedLines.get(i).startsWith(";")) {
                                        onlyLastNotCommentedOut = false;
                                        break;
                                    }
                                } else {
                                    if (!updatedLines.get(i).startsWith(";")) {
                                        onlyLastNotCommentedOut = false;
                                        break;
                                    }
                                }
                            }
                            if (onlyLastNotCommentedOut) {
                                updatedLines.add("; " + updatedLines.remove(updatedLines.size()-1) + "  ; -mdl");                                
                            } else {
                                // We need to look for which one in particular to remove:
                                config.error("More than one optimization applied to the same line, not yet supported at: " + s.sl);
                                config.error("Lines that had to be removed were:");
                                for(String line:updatedLines) {
                                    config.error(line);
                                }
                                return false;
                            }
                        }
                    }
                    for(SourceStatement s: match.added) {
                        List<String> updatedLines = lines.get(s.sl.lineNumber-1);
                        if (config.dialectParser != null) {
                            updatedLines.add(config.dialectParser.statementToString(s, code, true, null) + "  ; +mdl");
                        } else {
                            updatedLines.add(s + "  ; +mdl");
                        }
                    }
                }
            }
            
            // 3) Update includes:
            for(SourceStatement s : f.getStatements()) {
                if (s.type == SourceStatement.STATEMENT_INCLUDE) {
                    SourceStatement s2 = new SourceStatement(s.type, s.sl, s.source, config);
                    s2.rawInclude = s.rawInclude + ".mdl.asm";
                    List<String> updatedLines = lines.get(s.sl.lineNumber-1);
                    if (updatedLines.size() == 1) {
                        updatedLines.add("; " + updatedLines.remove(0) + "  ; -mdl");
                        if (config.dialectParser != null) {
                            updatedLines.add(config.dialectParser.statementToString(s2, code, true, null) + "  ; +mdl");
                        } else {
                            updatedLines.add(s2 + "  ; +mdl");
                        }
                    } else {
                        config.error("Optimization applied to an include statement in applyOptimizationsToOriginalFiles");
                        return false;                        
                    }
                }
            }
            
            // 4) Save lines:
            try {
                FileWriter fw = new FileWriter(newFileName);
                for(List<String> l:lines) {
                    for(String line:l) {
                        fw.write(line + "\n");
                    }
                }
                fw.flush();
                fw.close();
            } catch (Exception e) {
                config.error("Error writing to " + newFileName + " when trying to generate a file with optimizations applied to it.");
                return false;
            }
        }
        return true;
    }
    
    
    @Override
    public boolean triggered() {
        return trigger;
    }

    
    @Override
    public MDLWorker cloneForExecutionQueue() {
        PatternBasedOptimizer w = new PatternBasedOptimizer(config);

        w.logPotentialOptimizations = logPotentialOptimizations;
        w.generateFilesWithAppliedOptimizations = generateFilesWithAppliedOptimizations;
        w.onlyOnePotentialOptimizationPerLine = onlyOnePotentialOptimizationPerLine;
        w.trigger = trigger;
        w.silent = silent;
        w.inputPatternsFileName = inputPatternsFileName;
        
        // reset state:
        trigger = false;
        
        return w;
    }      
}
