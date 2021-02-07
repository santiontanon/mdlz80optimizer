/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cl;

import java.util.HashMap;

/**
 *
 * @author santi
 */
public class OptimizationResult {
    public int bytesSaved = 0;
    public int timeSavings[] = {0, 0};
    public HashMap<String, Integer> optimizerSpecificStats = new HashMap<>();

    public void addSavings(int a_bytesSaved, int[] a_timeSavings) {
        bytesSaved += a_bytesSaved;
        timeSavings[0] += a_timeSavings[0];
        if (a_timeSavings.length == 2) {
            timeSavings[1] += a_timeSavings[1];
        } else {
            timeSavings[1] += a_timeSavings[0];
        }
    }


    public void addSavings(OptimizationResult savings) {
        bytesSaved += savings.bytesSaved;
        timeSavings[0] += savings.timeSavings[0];
        timeSavings[1] += savings.timeSavings[1];
        for(String name: savings.optimizerSpecificStats.keySet()) {
            addOptimizerSpecific(name, savings.optimizerSpecificStats.get(name));
        }
    }

    
    public void addOptimizerSpecific(String name, int amount)
    {
        if (!optimizerSpecificStats.containsKey(name)) {
            optimizerSpecificStats.put(name, 0);
        }
        optimizerSpecificStats.put(name, optimizerSpecificStats.get(name)+amount);
    }
    
    
    public boolean anyOptimization()
    {
        if (bytesSaved > 0 || timeSavings[0] > 0 || timeSavings[1] > 0 ||
            optimizerSpecificStats.size() > 0) return true;
        return false;
    }


    public String timeSavingsString()
    {
        if (timeSavings[0] == timeSavings[1]) {
            return "" + timeSavings[0];
        } else {
            return "" + timeSavings[0] + "/" + timeSavings[1];
        }
    }    
    
    
    public String summaryString(MDLConfig config)
    {
        String stats = bytesSaved + " bytes, " + 
                      timeSavingsString() + " " +config.timeUnit+"s saved.";
        for(String name:optimizerSpecificStats.keySet()) {
            stats += " " + name + ": " + optimizerSpecificStats.get(name) + ".";
        }               
        
        return stats;
    }
}
