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
import util.Pair;
import util.TextUtils;

/**
 *
 * @author santi
 */
public class BinaryGenerator implements MDLWorker {
    
    /*
    Regular statements just produce a set of bytes, but some ("fpos"), change
    the next position we should write in the output binary. These positions are
    specified with fposAbsolute and fposOffset.
    */
    public static class StatementBinaryEffect {
        CodeStatement s;
        byte bytes[];
        Integer fposAbsolute = null, fposOffset = null;
        
        public StatementBinaryEffect(CodeStatement a_s, byte a_bytes[], Integer a_fposAbsolute, Integer a_fposOffset) {
            s = a_s;
            bytes = a_bytes;
            fposAbsolute = a_fposAbsolute;
            fposOffset = a_fposOffset;
        }
    }

    
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
        return "- ```-bin <output file>```: generates an assembled binary. Use ```"+AUTO_FILENAME+"``` as the output file name to respect the filenames specified in the sourcefiles of some dialects, or to autogenerate an output name.\n";
    }


    @Override
    public String simpleDocString() {
        return "- ```-bin <output file>```: generates an assembled binary.\n";
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
                    if (!writeBytes(output.main, code, os, output.minimumSize, true)) return false;
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
    

    public boolean writeBytes(SourceFile sf, CodeBase code, OutputStream os, int minimumSize, boolean considerFPosEffects) throws Exception
    {
        int size = 0;
        List<StatementBinaryEffect> statementBytes = new ArrayList<>();
        if (!generateStatementBytes(sf, code, statementBytes)) return false;
        List<Integer> data = new ArrayList<>();        
        int position = 0;
        for(StatementBinaryEffect effect:statementBytes) {
            if (effect.bytes != null) {
                while(data.size() < position) data.add(0);
                for(byte v:effect.bytes) {
                    if (data.size() > position) {
                        data.set(position, (int)v);
                    } else {
                        data.add((int)v);
                    }
                    position++;
                }
            }
            if (considerFPosEffects) {
                if (effect.fposAbsolute != null) {
                    position = effect.fposAbsolute;
                } else if (effect.fposOffset != null) {
                    position += effect.fposOffset;
                }
            }
        }
        if (!data.isEmpty()) {
            byte dataArray[] = new byte[data.size()];
            for(int i = 0;i<data.size();i++) {
                dataArray[i] = (byte)(int)data.get(i);
            }
            os.write(dataArray);
            size += dataArray.length;
        }
        while(size < minimumSize) {
            os.write(0);
            size ++;
        }
        return true;
    }

    
    public boolean generateStatementBytes(SourceFile sf, CodeBase code, List<StatementBinaryEffect> statementBytes)
    {
        for (CodeStatement s:sf.getStatements()) {
            switch(s.type) {
                case CodeStatement.STATEMENT_INCLUDE:
                    if (!generateStatementBytes(s.include, code, statementBytes)) return false;
                    break;

                case CodeStatement.STATEMENT_FPOS:
                {
                    Integer fposAbsolute = null;
                    if (s.fposAbsolute != null) {
                        fposAbsolute = s.fposAbsolute.evaluateToInteger(s, code, true);
                        if (fposAbsolute == null) {
                            config.error("Cannot evaluate " + s.fposAbsolute + " in " + s.sl);
                            return false;
                        }
                    }
                    Integer fposOffset = null;
                    if (s.fposOffset != null) {
                        fposOffset = s.fposOffset.evaluateToInteger(s, code, true);
                        if (fposOffset == null) {
                            config.error("Cannot evaluate " + s.fposAbsolute + " in " + s.sl);
                            return false;
                        }
                    }
                    statementBytes.add(new StatementBinaryEffect(s, new byte[0], fposAbsolute, fposOffset));
                    break;
                }
                    
                default:
                {
                    byte[] data = generateStatementBytes(s, code);
                    if (data != null) {
                        statementBytes.add(new StatementBinaryEffect(s, data, null, null));
                    } else {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    
    public byte[] generateStatementBytes(CodeStatement s, CodeBase code)
    {
        switch(s.type) {
            case CodeStatement.STATEMENT_NONE:
            case CodeStatement.STATEMENT_ORG:
            case CodeStatement.STATEMENT_CONSTANT:
            case CodeStatement.STATEMENT_MACRO:
            case CodeStatement.STATEMENT_MACROCALL:
                break;

            case CodeStatement.STATEMENT_INCLUDE:
                config.warn("generateStatementBytes called with an include statement!");
                return null;

            case CodeStatement.STATEMENT_INCBIN:
            {
                int skip = 0;
                int size = 0;
                if (s.incbinSkip != null) skip = s.incbinSkip.evaluateToInteger(s, code, false);
                if (s.incbinSize != null) size = s.incbinSize.evaluateToInteger(s, code, false);
                long flength = s.incbin.length();
                int datalength = Math.min((int)flength - skip, size);
                byte data[] = new byte[datalength];
                try (InputStream is = new FileInputStream(s.incbin)) {
                    if (skip > 0) is.skip(skip);
                    is.read(data, 0, datalength);
                } catch(Exception e) {
                    config.error("Cannot expand incbin: " + s.incbin + " (skip: " + skip + ", size: " + size + ", expectedLength: " + datalength + ")");
                    return null;
                }
                return data;
            }

            case CodeStatement.STATEMENT_DATA_BYTES:
            {
                List<Integer> data = new ArrayList<>();
                for(Expression exp: s.data) {        
                    if (!expressionToBytes(exp, s, code, data)) return null;
                }
                byte datab[] = new byte[data.size()];
                for(int i = 0;i<data.size();i++) {
                    datab[i] = (byte)(int)data.get(i);
                }
                return datab;
            }

            case CodeStatement.STATEMENT_DATA_WORDS:
            {
                byte data[] = new byte[s.data.size()*2];
                for(int i = 0;i<s.data.size();i++) {
                    Expression exp = s.data.get(i);
                    if (exp.evaluatesToNumericConstant()) {
                        Integer v = exp.evaluateToInteger(s, code, true);
                        if (v == null) {
                            config.error("Cannot evaluate expression " + exp + " when generating a binary: " + s.sl);
                            return null;
                        }
                        data[i*2] = (byte)(int)(v&0x00ff);
                        data[i*2+1] = (byte)(int)((v>>8)&0x00ff);
                    } else {
                        config.error("Cannot evaluate expression " + exp + " when generating a binary: " + s.sl);
                        return null;
                    }
                }
                return data;
            }

            case CodeStatement.STATEMENT_DATA_DOUBLE_WORDS:
            {
                byte data[] = new byte[s.data.size()*4];
                for(int i = 0;i<s.data.size();i++) {
                    Expression exp = s.data.get(i);
                    if (exp.evaluatesToNumericConstant()) {
                        int v = exp.evaluateToInteger(s, code, true);
                        data[i*4] = (byte)(int)(v&0x00ff);
                        data[i*4+1] = (byte)(int)((v>>8)&0x00ff);
                        data[i*4+2] = (byte)(int)((v>>16)&0x00ff);
                        data[i*4+3] = (byte)(int)((v>>24)&0x00ff);
                    } else {
                        config.error("Cannot evaluate expression " + exp + " when generating a binary: " + s.sl);
                        return null;
                    }
                }
                return data;
            }

            case CodeStatement.STATEMENT_DEFINE_SPACE:
                if (s.space_value != null) {
                    Integer value = s.space_value.evaluateToInteger(s, code, true);
                    Integer amount = s.space.evaluateToInteger(s, code, true);
                    if (value == null) {
                        config.error("Cannot evaluate " + s.space_value + " in " + s.sl);
                        return null;
                    }
                    if (amount == null) {
                        config.error("Cannot evaluate " + s.space + " in " + s.sl);
                        return null;
                    }
                    if (amount < 0) {
                        config.error("Negative space ("+amount+") in " + s.sl);
                        return null;
                    }
                    byte data[] = new byte[amount];
                    for(int i = 0;i<amount;i++) {
                        data[i] = (byte)(int)value;
                    }
                    return data;
                }
                break;

            case CodeStatement.STATEMENT_CPUOP:
            {
                List<Integer> data = s.op.assembleToBytes(s, code, config);
                if (data == null) {
                    config.error("Cannot convert op " + s.op + " to bytes in " + s.sl);
                    return null;
                }
                byte datab[] = new byte[data.size()];
                for(int i = 0;i<data.size();i++) {
                    datab[i] = (byte)(int)data.get(i);
                }
                return datab;
            }            
        }
        
        return new byte[0];
    }    
    
    
    public boolean expressionToBytes(Expression exp, CodeStatement ss, CodeBase code, List<Integer> data)
    {
        Object val = exp.evaluate(ss, code, true);
        if (val == null) {
            config.error("Cannot evaluate expression " + exp + " when generating a binary: " + ss.sl);
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
            config.error("Unsupported value " + val + "when generating a binary: " + ss.sl);
            return false;
        }
        
        return true;
    }    
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    } 
}
