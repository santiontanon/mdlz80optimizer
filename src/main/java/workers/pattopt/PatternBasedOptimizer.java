/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import cl.OptimizationResult;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
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
    
    public boolean logPotentialOptimizations = false;    
    public boolean onlyOnePotentialOptimizationPerLine = true;
    public boolean preventLabelDependentOptimizations = false;
    
    MDLConfig config;
    MDLConfig patternsConfig;   // we have a separate configuration, as the assembler used to define the patterns
                                // might be different form the current dialect.
    boolean trigger = false;
    boolean silent = false;
    int nPasses = 2;
    int stopAfter = -1;
    String inputPatternsFileName = null;
    List<Pattern> patterns = new ArrayList<>();
    
    // Some optimizations depend on certain labels to have specific values. After applying them,
    // we need to ensure that other optimizations to not change those values and make the code incorrect.
    // These lists accumulate conditions that previous optimizations assume, to make sure subsequent 
    // optimizations are still safe:
    List<EqualityConstraint> equalitiesToMaintain = new ArrayList<>();
    public boolean alreadyShownAPotentialOptimization = false;
    
    HashSet<CodeStatement> statementsWhereIXIsSP = null;
    HashSet<CodeStatement> statementsWhereIYIsSP = null;
    
    List<PatternMatch> appliedOptimizations = new ArrayList<>();


    public PatternBasedOptimizer(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-po```: Runs the pattern-based optimizer. You can pass an optional parameter, like ````-po size``` or ```-po speed```, which are shortcuts for '-po -popatterns data/pbo-patterns-size.txt' and '-po -popatterns data/pbo-patterns-speed.txt' (some dialects might change the defaults of these two)\n" +
               "- ```-po1```/```-po2```/```-po3```: The same as ```-po```, but specify whether to do 1, 2 or 3 passes of optimization (```-po``` is equivalent to ```-po2```). The more passes, the slower the optimization. Usually 1 pass is enough, but often 2 passes finds a few additional optimizations. 3 passes rarely finds any additional optimization.\n"+
               "- ```-posilent```: Supresses the pattern-based-optimizer output\n" +
               "- ```-popotential```: Reports lines where a potential optimization was not applied for safety, but could maybe be done manually (at most one potential optimization per line is shown).\n" +
               "- ```-popotential-all```: Same as above, but without the one-per-line constraint.\n" +
               "- ```-popatterns <file>```: specifies the file to load optimization patterns from (default 'data/pbo-patterns.txt', " +
                                     "which contains patterns that optimize both size and speed). For targetting size optimizations, use " +
                                     "'data/pbo-patterns-size.txt'. Notice that some dialects might change the default, for example, the " +
                                     "sdcc dialect sets the default to 'data/pbo-patterns-sdcc-speed.txt'\n" +
               "- ```-po-ldo```: some pattern-based optimizations depend on the specific value that some labels take ('label-dependent optimizations', ldo). These might be dangerous for code that is still in development.\n" +
               "- ```-po-stop-after <n>```: Stops optimizing after n optimizations. This is useful for debugging, if there is any optimization that breaks the code, to help locate it.\n";
    }

    @Override
    public String simpleDocString() {
        return "- ```-po```: Runs the pattern-based optimizer.\n";
    }
    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-po") ||
            flags.get(0).equals("-po1") ||
            flags.get(0).equals("-po2") ||
            flags.get(0).equals("-po3")) {
            String flag = flags.remove(0);
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
            switch(flag) {
                case "-po1":
                    nPasses = 1;
                    break;
                case "-po3":
                    nPasses = 3;
                    break;
                default:
                    nPasses = 2;
            }
            return true;
        }
        if (flags.get(0).equals("-posilent")) {
            flags.remove(0);
            silent = true;
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
        if (flags.get(0).equals("-po-ldo")) {
            flags.remove(00);
            preventLabelDependentOptimizations = true;
            return true;
        }
        if (flags.get(0).equals("-po-stop-after") && flags.size()>=2) {
            flags.remove(0);
            stopAfter = Integer.parseInt(flags.get(0));
            flags.remove(0);
            if (stopAfter <= 0) {
                config.error("The parameter to -po-stop-after must be an integer larger than 0.");
                return false;
            }
            return true;
        }
        return false;
    }


    public void initPatterns()
    {
        if (inputPatternsFileName == null) {
            inputPatternsFileName = defaultInputPatternsFileName;
        }
        
        // set the internal configuration, so we can parse the patterns with standard z80 syntax:
        patternsConfig = new MDLConfig();
        patternsConfig.labelsHaveSafeValues = config.labelsHaveSafeValues;

        try {
            patternsConfig.parseArgs("dummy", "-dialect", "mdl", "-cpu", config.cpu);
        } catch(IOException e) {
            config.error("Problem initializing the PatternBasedOptimizer!");
        }
        patternsConfig.logger = config.logger;
        patternsConfig.tokenizer.allowDashPlusLabels = config.tokenizer.allowDashPlusLabels;
        
        loadPatterns(inputPatternsFileName);
    }
    
    
    public List<Pattern> getPatterns()
    {
        return patterns;
    }
    
    
    public Pattern getPattern(String name)
    {
        for(Pattern p: patterns) {
            if (p.name != null && p.name.equals(name)) return p;
        }
        return null;
    }
    
    
    public boolean checkPatternIntegrity()
    {
        config.debug("Running checkPatternIntegrity with " + patterns.size() + " patterns...");
        for(Pattern p: patterns) {
            if (!p.checkIntegrity(config)) return false;
        }
        return true;
    }
    
    
    boolean tagCheck(Pattern p)
    {
        for(String tag:p.tags) {
            if (config.ignorePatternsWithTags.contains(tag)) return false;
        }
        
        return true;
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
                        Pattern p = new Pattern(patternString, patternsConfig);
                        if ((p.name == null || getPattern(p.name) == null) && tagCheck(p)) {
                            // do not load the same pattern twice (some are repeated in some files for convenience):
                            patterns.add(p);
                        }
                    }
                    break;
                }
                line = line.trim();
                // ignore comments:
                if (config.tokenizer.isSingleLineComment(line)) continue;

                if (line.equals("")) {
                    if (!patternString.equals("")) {
                        Pattern p = new Pattern(patternString, patternsConfig);
                        if ((p.name == null || getPattern(p.name) == null) && tagCheck(p)) {
                            // do not load the same pattern twice (some are repeated in some files for convenience):
                            patterns.add(p);
                        }
                        patternString = "";
                    }
                } else {
                    if (line.startsWith("include")) {
                        List<String> tokens = config.tokenizer.tokenize(line);
                        if (tokens.size()>=2) {
                            String name = tokens.get(1);
                            if (config.tokenizer.isString(name)) {
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
            config.error("PatternBasedOptimizer: error initializing patterns! " + e.getMessage());
        }
    }


    @Override
    public boolean work(CodeBase code) {
        if (trigger) {            
            OptimizationResult r = optimize(code);
            config.optimizerStats.addSavings(r);
            if (config.dialectParser != null) return config.dialectParser.postCodeModificationActions(code);
        }
        return true;
    }


    public OptimizationResult optimize(CodeBase code) {        
        initPatterns();
        OptimizationResult r = new OptimizationResult();
        
        // Finding the optimal set of optimizations would require systematic search,
        // which would be unfeasible computationally. So, just use a simple heuristic to
        // obtain at least a decent set of optimizations (even if there is no guarantee
        // of being close to the optimal). We do two passes, in the first pass, we only
        // perform optimizations that would NOT set any equality constraints. And in the second
        // pass, we allow any kind of optimizations:
        
        boolean done = false;
        for(int pass = 0;pass<nPasses;pass++) {
            for (SourceFile f : code.getSourceFiles()) {
                for (int i = 0; i < f.getStatements().size(); i++) {
                    if (optimizeStartingFromLine(f, i, code, r, pass==nPasses-1)) {
                        i = Math.max(0, i-2);   // go back a couple of statements, as more optimizations might chain
                        if (stopAfter >= 0 && appliedOptimizations.size() >= stopAfter) {
                            done = true;
                            break;
                        }
                    }
                }
                if (done) break;
            }
            if (done) break;
        }
        
        code.resetAddresses();

        Integer npatterns = r.optimizerSpecificStats.get("Pattern-based optimizer pattern applications");
        if (npatterns == null) npatterns = 0;
        config.diggest("PatternBasedOptimizer: "+npatterns+" patterns applied, " +
                       r.bytesSaved+" bytes, " + 
                       r.timeSavingsString() + " " +config.timeUnit+"s saved.");
        return r;
    }
    
    
    public void getMatchesStartingFromLine(SourceFile f, int i, List<PatternMatch> matches, boolean lastPass, CodeBase code)
    {
        for(Pattern patt: patterns) {
            PatternMatch match = patt.match(i, f, code, this);
            if (match != null &&
                preventLabelDependentOptimizations &&
                match.dependsOnLabelValues(code)) {
                config.debug("-po-ldo prevented an an optimization match.");
                match = null;
            }
            // We stop matches that add new equalities in the first pass:
            if (!lastPass && match != null && !match.newEqualities.isEmpty()) {
                match = null;
            }
            if (match != null) matches.add(match);
        }        
    }
    
    
    // Returns whether any optimization was done:
    public boolean optimizeStartingFromLine(SourceFile f, int i, CodeBase code, OptimizationResult r, boolean lastPass)
    {
        int maximumLookAhead = 16;  // maximum number of lines to look ahead for a better pattern
        
        alreadyShownAPotentialOptimization = false;
        // we only print potential optimizations in the last pass:
        if (!lastPass) alreadyShownAPotentialOptimization = true;

        List<PatternMatch> matches = new ArrayList<>();
        getMatchesStartingFromLine(f, i, matches, lastPass, code);
        
        if (!matches.isEmpty()) {
            // Look for patterns that match in the lines that will be modified beyon line "i",
            // to reduce the probability of applying a pattern that prevents a better one
            // to be applied later on:
            int lastMatchedLine = i;
            for(PatternMatch match: matches) {
                for(CPUOpPattern opp:match.pattern.pattern) {
                    List<CodeStatement> l = match.map.get(opp.ID);
                    if (l == null) break;
                    for(CodeStatement s:l) {
                        int idx = f.getStatements().indexOf(s);
                        if (idx > lastMatchedLine) lastMatchedLine = idx;
                    }
                }
            }
            lastMatchedLine = Math.min(i+maximumLookAhead, lastMatchedLine);
            for(int ii = i+1; ii<=lastMatchedLine;ii++) {
                getMatchesStartingFromLine(f, ii, matches, lastPass, code);
            }
        }
        

        if (!matches.isEmpty()) {
            // there was at least a match, pick the best!
            Pattern bestPatt = null;
            PatternMatch bestMatch = null;
            // selection is based on bytes saved, if there are ties, resolve by time saved,
            // and if there are ties, resolve based on equality constraints
            int bestSizeSavings = 0;    
            int bestTimeSavings = 0;
            int bestNumConstraints = 0;
            for(PatternMatch match:matches) {
                int sizeSavings = match.pattern.getSpaceSaving(match, code);
                int timeSavings = match.pattern.getTimeSaving(match, code)[0];
                int numConstraints = match.newEqualities.size();
                int numLinesInvolved = match.map.size();
                config.debug("optimization option: " + match.pattern.getInstantiatedName(match) + " --> " + sizeSavings + ", " + timeSavings + ", " + numConstraints + ", " + match.map.size());
                // Prefer patterns that save the most bytes, otherwise, the ones that save the most time, 
                // otherwise those with less constraints, and otherwise, those that involve less lines of code.
                if (bestPatt == null ||
                    sizeSavings > bestSizeSavings ||
                    (sizeSavings == bestSizeSavings && timeSavings > bestTimeSavings) ||
                    (sizeSavings == bestSizeSavings && timeSavings == bestTimeSavings && numConstraints < bestNumConstraints) ||
                    (sizeSavings == bestSizeSavings && timeSavings == bestTimeSavings && numConstraints == bestNumConstraints && numLinesInvolved < bestMatch.map.size())) {
                    bestPatt = match.pattern;
                    bestMatch = match;
                    bestSizeSavings = sizeSavings;
                    bestTimeSavings = timeSavings;
                    bestNumConstraints = numConstraints;
                }
            }

            CodeStatement statementToDisplayMessageOn = null;
            int startIndex = i;
            int endIndex = startIndex;
            for(int id:bestMatch.map.keySet()) {
                if (id == 0) statementToDisplayMessageOn = bestMatch.map.get(id).get(0);
                for(CodeStatement s:bestMatch.map.get(id)) {
                    int idx = f.getStatements().indexOf(s);
                    if (idx > endIndex) endIndex = idx;
                }
            }
            if (statementToDisplayMessageOn == null) {
                config.warn("Could not identify the statement to display the optimization message on...");
                statementToDisplayMessageOn = f.getStatements().get(i);
            }

            if (bestPatt != null && 
                bestPatt.apply(f, bestMatch, code, equalitiesToMaintain)) {
                statementsWhereIXIsSP = null;
                statementsWhereIYIsSP = null;
                if (config.isInfoEnabled()) {
                    int bytesSaved = bestPatt.getSpaceSaving(bestMatch, code);
                    String timeSavedString = bestPatt.getTimeSavingString(bestMatch, code);
                    config.info("Pattern-based optimization", statementToDisplayMessageOn.fileNameLineString(), 
                            bestPatt.getInstantiatedName(bestMatch)+" ("+bytesSaved+" bytes, " +
                            timeSavedString + " " +config.timeUnit+"s saved)");
                    for(EqualityConstraint ec:bestMatch.newEqualities) {
                        config.debug("new equality constraint: " + ec.exp1 + " == " + ec.exp2);
                    }

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
                r.addOptimizerSpecific("Pattern-based optimizer pattern applications", 1);
                r.addSavings(bestPatt.getSpaceSaving(bestMatch, code), bestPatt.getTimeSaving(bestMatch, code));
                bestMatch.setConfig(config);    // we set the dialect configuration to the global one, in order to generate code properly if needed
                appliedOptimizations.add(bestMatch);
                return true;
            } else {
                config.debug("Optimization pattern application failed");
            }
        }
        return false;
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
                        List<CodeStatement> toRemoveL = match.map.get(ID);
                        for(CodeStatement toRemove:toRemoveL) {
                            for(CodeStatement added:previous.added) {
                                if (toRemove == added) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
    
    /*
    - Search for occurrences of "ld ix, 0", "add ix, sp" (and the same for "iy")
    - Then records the set of instructions for which ix retains the value of sp
    */
    public void searchStatementsWhereIXIYAreSP(CodeBase code)
    {
        statementsWhereIXIsSP = new LinkedHashSet<>();
        statementsWhereIYIsSP = new LinkedHashSet<>();
        
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.op != null) {
                    CodeStatement s2 = SPAssignedToRegister(s, "ix", code);
                    if (s2 != null) {
                        config.debug("searchStatementsWhereIXIYAreSP ix match at " + s.sl);
                        markSPAssignedToRegisterStartingFrom(s2, "ix", statementsWhereIXIsSP, code);
                    } else {
                        s2 = SPAssignedToRegister(s, "iy", code);
                        if (s2 != null) {
                            config.debug("searchStatementsWhereIXIYAreSP iy match at " + s.sl);
                            markSPAssignedToRegisterStartingFrom(s2, "iy", statementsWhereIYIsSP, code);
                        }
                    }
                }
            }
        }
//        for(CodeStatement s:statementsWhereIXIsSP) {
//            config.info("ix: " + s.sl);
//        }
//        for(CodeStatement s:statementsWhereIYIsSP) {
//            config.info("iy: " + s.sl);
//        }
    }
    
    
    public CodeStatement SPAssignedToRegister(CodeStatement s, String register, CodeBase code)
    {
        if (!s.op.spec.getName().equalsIgnoreCase("ld") ||
            !s.op.args.get(0).isRegister(register) ||
            !s.op.args.get(1).evaluatesToNumericConstant()) {
            return null;
        }
        Integer v = s.op.args.get(1).evaluateToInteger(s, code, true);
        if (v == null) {
            config.debug("Cannot evaluate expression: " + s.op.args.get(1));
            config.debug("MDL will assume that it does not evaluate to 0.");
            return null;
        }
        if (v != 0) return null;
        
        while(true) {
            s = s.source.getNextStatementTo(s, code);
            if (s == null) return null;
            if (s.label != null) return null;
            if (s.op != null) {
                if (s.op.spec.getName().equalsIgnoreCase("add") &&
                    s.op.args.get(0).isRegister(register) &&
                    s.op.args.get(1).isRegister("sp")) {
                    return s.source.getNextStatementTo(s, code);
                }
            } else {
                if (!s.isEmptyAllowingComments()) return null;
            }
        }
    }
    
    
    public void markSPAssignedToRegisterStartingFrom(CodeStatement s, String register, HashSet<CodeStatement> set, CodeBase code)
    {
        List<CodeStatement> open = new ArrayList<>();
        HashSet<CodeStatement> closed = new HashSet<>();
        open.add(s);
        
        while(!open.isEmpty()) {
            s = open.remove(0);
            closed.add(s);
            if (s.op != null) {
                
                // Check for "pop register" or "ld register, ???"
                if (s.op.spec.getName().equalsIgnoreCase("pop") &&
                    s.op.args.get(0).isRegister(register)) {
                    continue;
                }
                if (s.op.spec.getName().equalsIgnoreCase("ld") &&
                    s.op.args.get(0).isRegister(register)) {
                    continue;
                }
                
                set.add(s);
                List<Pair<CodeStatement, List<CodeStatement>>> next_l = s.source.nextExecutionStatements(s, true, null, code);
                if (next_l != null) {
                    for(Pair<CodeStatement, List<CodeStatement>> next:next_l) {
                        if (!set.contains(next.getLeft()) &&
                            !closed.contains(next.getLeft())) {
                            open.add(next.getLeft());
                        }
                    }
                }
            } else {
                if (s.type == CodeStatement.STATEMENT_NONE) {
                    s = s.source.getNextStatementTo(s, code);
                    if (!set.contains(s) && !closed.contains(s)) {
                        open.add(s);
                    }
                }
            }
        }
    }
    
    
    @Override
    public boolean triggered() {
        return trigger;
    }
}
