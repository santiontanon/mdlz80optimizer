/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.pattopt;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceStatement;

/**
 *
 * @author santi
 */
public class EqualityConstraint {
    boolean inequality = false;
    Expression exp1, exp2;
    SourceStatement s1, s2;

    public EqualityConstraint(Expression a_exp1, SourceStatement a_s1,
                              Expression a_exp2, SourceStatement a_s2,
                              boolean a_in) {
        exp1 = a_exp1;
        exp2 = a_exp2;
        s1 = a_s1;
        s2 = a_s2;
        inequality = a_in;
    }

    boolean check(CodeBase code, MDLConfig config) {
        // first check if the statements have disappeared:
        if (s1 != null && s1.source.getStatements().indexOf(s1) == -1) {
            config.error("One of the statements that an EqualityContraint depended on was not updated!");
            return false;
        }
        if (s2 != null && s2.source.getStatements().indexOf(s2) == -1) {
            config.error("One of the statements that an EqualityContraint depended on was not updated!");
            return false;
        }
        Integer v1 = exp1.evaluateToInteger(s1, code, true);
        Integer v2 = exp2.evaluateToInteger(s2, code, true);
        if (inequality) {
            return !v1.equals(v2);
        } else {
            return v1.equals(v2);
        }
    }
}
