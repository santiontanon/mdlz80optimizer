/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.CodeStatement;
import code.OutputBinary;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import util.TextUtils;

/**
 *
 * @author santi
 */
public class BinaryGenerator implements MDLWorker {

    public static String AUTO_FILENAME = "auto";
    
    MDLConfig config = null;
    String outputFileName = null;

    public BinaryGenerator(MDLConfig a_config)
    {
        config = a_config;
    }

    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-bin <output file>```: (task) generates an assembled binary. Use ```"+AUTO_FILENAME+"``` as the output file name to respect the filenames specified in the sourcefiles of some dialects, or to auto generate an output name.\n";
    }


    @Override
    public String simpleDocString() {
        return "- ```-bin <output file>```: (task) generates an assembled binary.\n";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-bin") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        }
        return false;
    }

    
    @Override
    public boolean work(CodeBase code) {
        if (outputFileName != null) {
            config.debug("Executing "+this.getClass().getSimpleName()+" worker...");
            
            for(OutputBinary output:code.outputs) {
                String finalOutputFileName = outputFileName;
                boolean addSufix = true;
                if (outputFileName.equals(AUTO_FILENAME)) {
                    // autogenerate filenames:
                    if (output.fileName != null) {
                        String path = FilenameUtils.getFullPath(output.main.fileName);
                        finalOutputFileName = path + output.fileName;
                        addSufix = false;
                    } else {
                        finalOutputFileName = output.main.fileName + ".mdl.bin";
                    }
                }
                int idx = code.outputs.indexOf(output);
                if (addSufix && idx > 0) {
                    Pair<String, String> tmp = TextUtils.splitFileNameExtension(finalOutputFileName);
                    finalOutputFileName = tmp.getLeft() + "-output" + (idx+1) + tmp.getRight();
                }            
            
                try (FileOutputStream os = new FileOutputStream(finalOutputFileName)) {
                    if (!writeBytes(output.main, code, os)) return false;
                    os.flush();
                } catch (Exception e) {
                    config.error("Cannot write to file " + finalOutputFileName + ": " + e);
                    config.error(Arrays.toString(e.getStackTrace()));
                    return false;
                }
            }
        }
        return true;
    }
    

    public boolean writeBytes(SourceFile sf, CodeBase code, OutputStream os) throws Exception
    {
        List<Pair<CodeStatement, byte[]>> statementBytes = new ArrayList<>();
        if (!generateStatementBytes(sf, code, statementBytes)) return false;
        for(Pair<CodeStatement, byte[]> pair:statementBytes) {
            os.write(pair.getRight());
        }
        return true;
    }

    
    public boolean generateStatementBytes(SourceFile sf, CodeBase code, List<Pair<CodeStatement, byte[]>> statementBytes)
    {
        for (CodeStatement ss:sf.getStatements()) {
            switch(ss.type) {
                case CodeStatement.STATEMENT_NONE:
                case CodeStatement.STATEMENT_ORG:
                case CodeStatement.STATEMENT_CONSTANT:
                case CodeStatement.STATEMENT_MACRO:
                case CodeStatement.STATEMENT_MACROCALL:
                    break;

                case CodeStatement.STATEMENT_INCLUDE:
                    if (!generateStatementBytes(ss.include, code, statementBytes)) return false;
                    break;

                case CodeStatement.STATEMENT_INCBIN:
                {
                    int skip = 0;
                    int size = 0;
                    if (ss.incbinSkip != null) skip = ss.incbinSkip.evaluateToInteger(ss, code, false);
                    if (ss.incbinSize != null) size = ss.incbinSize.evaluateToInteger(ss, code, false);
                    long flength = ss.incbin.length();
                    int datalength = Math.max((int)flength - skip, size);
                    byte data[] = new byte[datalength];
                    try (InputStream is = new FileInputStream(ss.incbin)) {
                        if (skip > 0) is.skip(skip);
                        is.read(data, 0, datalength);
                    } catch(Exception e) {
                        config.error("Cannot expand incbin: " + ss.incbin + " (skip: " + skip + ", size: " + size + ", expectedLength: " + datalength + ")");
                        return false;
                    }
                    statementBytes.add(Pair.of(ss, data));
                    break;
                }

                case CodeStatement.STATEMENT_DATA_BYTES:
                {
                    List<Integer> data = new ArrayList<>();
                    for(Expression exp: ss.data) {        
                        if (!expressionToBytes(exp, ss, code, data)) return false;
                    }
                    byte datab[] = new byte[data.size()];
                    for(int i = 0;i<data.size();i++) {
                        datab[i] = (byte)(int)data.get(i);
                    }
                    statementBytes.add(Pair.of(ss, datab));
                    break;
                }

                case CodeStatement.STATEMENT_DATA_WORDS:
                {
                    byte data[] = new byte[ss.data.size()*2];
                    for(int i = 0;i<ss.data.size();i++) {
                        Expression exp = ss.data.get(i);
                        if (exp.evaluatesToNumericConstant()) {
                            Integer v = exp.evaluateToInteger(ss, code, true);
                            if (v == null) {
                                config.error("Cannot evaluate expression " + exp + " when generating a binary.");
                                return false;
                            }
                            data[i*2] = (byte)(int)(v&0x00ff);
                            data[i*2+1] = (byte)(int)((v>>8)&0x00ff);
                        } else {
                            config.error("Cannot evaluate expression " + exp + " when generating a binary.");
                            return false;
                        }
                    }
                    statementBytes.add(Pair.of(ss, data));
                    break;
                }

                case CodeStatement.STATEMENT_DATA_DOUBLE_WORDS:
                {
                    byte data[] = new byte[ss.data.size()*4];
                    for(int i = 0;i<ss.data.size();i++) {
                        Expression exp = ss.data.get(i);
                        if (exp.evaluatesToNumericConstant()) {
                            int v = exp.evaluateToInteger(ss, code, true);
                            data[i*4] = (byte)(int)(v&0x00ff);
                            data[i*4+1] = (byte)(int)((v>>8)&0x00ff);
                            data[i*4+2] = (byte)(int)((v>>16)&0x00ff);
                            data[i*4+3] = (byte)(int)((v>>24)&0x00ff);
                        } else {
                            config.error("Cannot evaluate expression " + exp + "when generating a binary.");
                            return false;
                        }
                    }
                    statementBytes.add(Pair.of(ss, data));
                    break;
                }

                case CodeStatement.STATEMENT_DEFINE_SPACE:
                    if (ss.space_value != null) {
                        Integer value = ss.space_value.evaluateToInteger(ss, code, true);
                        Integer amount = ss.space.evaluateToInteger(ss, code, true);
                        if (value == null) {
                            config.error("Cannot evaluate " + ss.space_value + " in " + ss.sl);
                            return false;
                        }
                        if (amount == null) {
                            config.error("Cannot evaluate " + ss.space + " in " + ss.sl);
                            return false;
                        }
                        byte data[] = new byte[amount];
                        for(int i = 0;i<amount;i++) {
                            data[i] = (byte)(int)value;
                        }
                        statementBytes.add(Pair.of(ss, data));
                    }
                    break;

                case CodeStatement.STATEMENT_CPUOP:
                {
                    List<Integer> data = ss.op.assembleToBytes(ss, code, config);
                    if (data == null) {
                        config.error("Cannot convert op " + ss.op + " to bytes!");
                        return false;
                    }
                    byte datab[] = new byte[data.size()];
                    for(int i = 0;i<data.size();i++) {
                        datab[i] = (byte)(int)data.get(i);
                    }
                    statementBytes.add(Pair.of(ss, datab));
                    break;
                }
            }
        }
        
        return true;
    }
    
    
    public boolean expressionToBytes(Expression exp, CodeStatement ss, CodeBase code, List<Integer> data)
    {
        Object val = exp.evaluate(ss, code, true);
        if (val == null) {
            config.error("Cannot evaluate expression " + exp + "when generating a binary.");
            return false;
        }
        return valueToBytes(val, ss, code, data);
    }
    
    
    public boolean valueToBytes(Object val, CodeStatement ss, CodeBase code, List<Integer> data)
    {
        if (val instanceof Integer) {
            int v = (Integer)val;
            data.add(v&0x00ff);
        } else if (val instanceof String) {
            String v = (String)val;
            for(int i = 0;i<v.length();i++) {
                data.add((int)v.charAt(i));
            }
        } else if (val instanceof List) {
            for(Object val2:(List)val) {
                if (!valueToBytes(val2, ss, code, data)) return false;
            }
        } else if (val instanceof Expression) {
            return expressionToBytes((Expression)val, ss, code, data);
        } else {
            config.error("Unsupported value " + val + "when generating a binary.");
            return false;
        }
        
        return true;
    }    
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    }

    
    @Override
    public MDLWorker cloneForExecutionQueue() {
        BinaryGenerator w = new BinaryGenerator(config);
        w.outputFileName = outputFileName;
        
        // reset state:
        outputFileName = null;
        
        return w;
    }    
}
