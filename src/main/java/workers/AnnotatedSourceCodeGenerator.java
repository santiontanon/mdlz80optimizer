/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileWriter;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;
import code.HTMLCodeStyle;
import code.OutputBinary;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;

/**
 *
 * @author santi
 */
public class AnnotatedSourceCodeGenerator implements MDLWorker {
    public final static String GFX_TAG = "mdl-asm+:html:gfx";
    public final static int COLOR_PIXEL_ON = 0xff000000;  // black
    public final static int COLOR_PIXEL_OFF = 0xffdddddd;  // light gray
    public final static int COLOR_PIXEL_MASKED = 0xff8888ff;  // blue
    
    MDLConfig config = null;

    boolean generateHTML = false;
    String outputFileName = null;
    int imgIndex = 0;
    HTMLCodeStyle style = new HTMLCodeStyle();
    boolean respectOriginalIndentation = false;
    boolean annotateEqusWithFinalValue = true;


    public AnnotatedSourceCodeGenerator(MDLConfig a_config)
    {
        config = a_config;
        style.annotateEqusWithFinalValue = annotateEqusWithFinalValue;
    }


    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-asm+ <output file>```: generates a single text file containing the original assembler code (with macros expanded), that includes size and time annotations at the beginning of each file to help with manual optimizations beyond what MDL already provides.\n"
             + "- ```-asm+:html <output file>```: acts like ```-asm+```, except that the output is in html (rendered as a table), allowing it to have some extra information. " +
               "It also recognizes certain tags in the source code to add html visualizations of the graphics in the game, extracting them automatically from the data in the assembler files. "+
               "Specifically, if you add a comment like this ```; mdl-asm+:html:gfx(bitmap,pre,1,8,2)```, it will interpret the bytes prior to this as a bitmap and render it visually in the html. " +
               "```pre``` means that the data is before the comment (use ```post``` to use the data that comes after the comment. ```bitmap``` means that the data will be interpreted as a bitmap (black/white with one bit per pixel). " +
               "You can use ```and-or-bitmap-with-size``` to interpret it as the usual ZX spectrum graphics where each two bytes represent 8 pixels (first is and-mask, second is or-mask). The first two bytes will be interpreted as the height/width (hence, this can only be used with ```post```). " + 
               "When specifying ```bitmap```, the next two parameters are the width (in bytes)/height (in pixels). The last parameter is the zoom factor to use when visualizing them in the html.\n" +
               "- ```-asm+:no-reindent```: tries to respect the original indentation of the source assembler file (this is not always possible, as MDL might modify or generate code, making this hard; this is why this is not on by default).\n" +
               "- ```-asm+:no-label-links```: by default, labels used in expressions are rendered as links that point to the label definitions. Use this flag to deactivate such behavior if desired.\n";
    }


    @Override
    public String simpleDocString() {
        return "";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-asm+") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-asm+:html") && flags.size()>=2) {
            flags.remove(0);
            generateHTML = true;
            outputFileName = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-asm+:no-reindent")) {
            flags.remove(0);
            respectOriginalIndentation = true;
            return true;
        } else if (flags.get(0).equals("-asm+:no-label-links")) {
            flags.remove(0);
            style.labelsAsLinks = false;
            return true;
        }
        return false;
    }


    @Override
    public boolean work(CodeBase code) {

        if (outputFileName != null) {
            config.debug("Executing "+this.getClass().getSimpleName()+" worker...");
            imgIndex = 0;
            
            if (config.evaluateAllExpressions) code.evaluateAllExpressions();
            
            // Calculate the position of each statement in the generated binary:
            HashMap<CodeStatement, Integer> positionInBinaryMap = new HashMap<>();
            HashMap<CodeStatement, BinaryGenerator.StatementBinaryEffect> statementBytesMap = new HashMap<>();
            List<BinaryGenerator.StatementBinaryEffect> statementBytes = new ArrayList<>();
            BinaryGenerator gen = new BinaryGenerator(config);
            for(OutputBinary output: code.outputs) {
                if (!gen.generateStatementBytes(output.main, code, statementBytes)) return false;
            }
            int position = 0;
            for(BinaryGenerator.StatementBinaryEffect effect:statementBytes) {
                positionInBinaryMap.put(effect.s, position);
                statementBytesMap.put(effect.s, effect);
                position += effect.bytes.length;
                if (effect.fposAbsolute != null) {
                    position = effect.fposAbsolute;
                } else if (effect.fposOffset != null) {
                    position += effect.fposOffset;
                }
            }
            
            try (FileWriter fw = new FileWriter(outputFileName)) {
                if (generateHTML) {
                    // write HTML header:
                    fw.write("<head>\n");            
                    fw.write("<title>MDL: annotated source code</title>\n");
                    fw.write("<style>\n");
                    fw.write("  table.sourcefile {\n");
                    fw.write("    border: 1px solid black;\n");
                    fw.write("    background-color: #eeeeee;\n");
                    fw.write("    }\n");
                    fw.write("  table.sourcefile tr td {\n");
                    fw.write("    border-right: 1px solid black;\n");
                    fw.write("    padding-left: 8px;\n");
                    fw.write("    padding-right: 8px;\n");
                    fw.write("    }\n");
                    fw.write("  table.sourcefile tr td:last-of-type {\n");
                    fw.write("    border: none;\n");
                    fw.write("    background-color: #ffffff;\n");
                    fw.write("    }\n");
                    fw.write("</style>\n");
                    fw.write("</head>\n");
                    fw.write("<body>\n");
                }
                for(SourceFile sf:code.getSourceFiles()) {
                    
                    if (generateHTML) {
                        fw.write("<hr><br><a name=\""+sf.fileName+"\"><b> Source Code file: " + sf.fileName + "</b></a><br>\n");
                        fw.write("<table class=\"sourcefile\" cellspacing=\"0\">\n");
                        fw.write("<tr><td style=\"border-bottom: 1px solid black\"><b>Address</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Position in Binary</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Size</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Timing</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Assembled</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Code</b></td></tr>\n");
                        fw.write(sourceFileHTMLString(sf, code, positionInBinaryMap, statementBytesMap, outputFileName));
                        fw.write("</table>\n");
                        fw.write("\n");
                    } else {
                        fw.write("; ------------------------------------------------\n");
                        fw.write("; ---- " + sf.fileName + " --------------------------------\n");
                        fw.write("; ------------------------------------------------\n\n");
                        fw.write("; Address  Size  Time\n");
                        fw.write("; -------------------\n");
                        fw.write(sourceFileString(sf, code));
                        fw.write("\n");
                    }
                }
                if (generateHTML) {
                    fw.write("</body>\n");
                }                
                fw.flush();
            } catch (Exception e) {
                config.error("Cannot write to file " + outputFileName + ": " + e);
                for(Object st:e.getStackTrace()) {
                    config.error("    " + st);
                }
                return false;
            }
        }
        return true;
    }


    public String sourceFileString(SourceFile sf, CodeBase code)
    {
        StringBuilder sb = new StringBuilder();
        sourceFileString(sf, sb, code);
        return sb.toString();
    }


    public void sourceFileString(SourceFile sf, StringBuilder sb, CodeBase code)
    {
        for (CodeStatement ss:sf.getStatements()) {
            sb.append("  ");
            Integer address = ss.getAddress(code);
            if (address == null) {
                sb.append("????");
            } else {
                sb.append(config.tokenizer.toHexWord(address, config.hexStyle));
            }
            sb.append("  ");
            Integer size = ss.sizeInBytes(code, true, true, true);
            if (size == null) {
                config.error("Cannot evaluate the size of statement " + ss + " in " + ss.sl);
                return;
            }
            String sizeString = "" + (size > 0 ? size:"");
            while(sizeString.length() < 4) sizeString = " " + sizeString;
            sb.append(sizeString);
            sb.append("  ");
            String timeString = "" + ss.timeString();
            while(timeString.length() < 5) timeString = " " + timeString;
            sb.append(timeString);
            sb.append("  ");
            String ssString = ss.toString();
            if (respectOriginalIndentation) ssString = reconstructIndentation(ssString, ss);
            sb.append(ssString);
            sb.append("\n");
        }
    }
    
    
    public String sourceFileHTMLString(SourceFile sf, CodeBase code,
            HashMap<CodeStatement, Integer> positionInBinaryMap,
            HashMap<CodeStatement, BinaryGenerator.StatementBinaryEffect> statementBytesMap,
            String outputFileName)
    {
        StringBuilder sb = new StringBuilder();
        sourceFileHTMLString(sf, sb, code, positionInBinaryMap, statementBytesMap, outputFileName);
        return sb.toString();
    }

    
    public void sourceFileHTMLString(SourceFile sf, StringBuilder sb, CodeBase code,
            HashMap<CodeStatement, Integer> positionInBinaryMap,
            HashMap<CodeStatement, BinaryGenerator.StatementBinaryEffect> statementBytesMap,
            String outputFileName)
    {
        for (CodeStatement ss:sf.getStatements()) {
            sb.append("<tr>");
            Integer address = ss.getAddress(code);
            if (address == null) {
                sb.append("<td>????</td>");
            } else {
                sb.append("<td>");
                sb.append(config.tokenizer.toHexWord(address, config.hexStyle));
                sb.append("</td>");
            }
            
            Integer position = positionInBinaryMap.get(ss);
            if (position == null) {
                sb.append("<td></td>");
            } else {
                sb.append("<td>");
                if (position >= 0xffff) {
                    sb.append(config.tokenizer.toHexDWord(position, config.hexStyle));
                } else {
                    sb.append(config.tokenizer.toHexWord(position, config.hexStyle));
                }
                sb.append("</td>");
            }
            
            Integer size = ss.sizeInBytes(code, true, true, true);
            if (size == null) {
                config.error("Cannot evaluate the size of statement " + ss + " in " + ss.sl);
                return;
            }
            String sizeString = "" + (size > 0 ? size:"");
            sb.append("<td>");
            sb.append(sizeString);
            sb.append("</td>");
            String timeString = "" + ss.timeString();
            sb.append("<td>");
            sb.append(timeString);
            sb.append("</td>");
            if (ss.type == CodeStatement.STATEMENT_CPUOP) {
                List<Integer> data = ss.op.assembleToBytes(ss, code, config);
                if (data == null) {
                    config.error("Cannot convert " + ss.op + " to bytes in " + ss.sl);
                    sb.append("<td>ERROR</td>");
                } else {
                    sb.append("<td><pre>");
                    for(Integer v:data) {
                        sb.append(config.tokenizer.toHex(v, 2));
                        sb.append(" ");
                    }
                    sb.append("</pre></td>");
                }
            } else {
                sb.append("<td></td>");
            }       
            
            String ssString = ss.toStringHTML(style, code);
            if (respectOriginalIndentation) ssString = reconstructIndentation(ssString, ss);
            
            // Check if there is an "mdl-asm+:html:gfx" tag:
            if (ss.comment != null && ss.comment.contains(GFX_TAG)) {
                // Tag found!, get parameters:
                int cmdStartIdx = ss.comment.indexOf(GFX_TAG);
                int cmdEndIdx = ss.comment.indexOf(")", cmdStartIdx);
                if (cmdEndIdx >= 0) {
                    String cmdString = ss.comment.substring(cmdStartIdx, cmdEndIdx+1);
                    String tokens[] = cmdString.substring(GFX_TAG.length()).split(",");
                    List<String> args = new ArrayList<>();
                    for(int i = 0;i<tokens.length;i++) {
                        String token = tokens[i].strip();
                        token = token.replace("(", "");
                        token = token.replace(")", "");
                        args.add(token);
                    }
                    BufferedImage img = null;
                    switch(args.get(0)) {
                        case "bitmap":
                        {
                            // check if we need to get the data from before, or from afterwards:
                            if (args.size() != 5) {
                                config.warn("Expected 5 arguments in a 'bitmap' comment tag (ignoring): " + ss.comment);                        
                                break;
                            }
                            Integer width = Integer.parseInt(args.get(2));
                            Integer height = Integer.parseInt(args.get(3));
                            int dataSize = width*height;
                            List<Byte> bitmapData = null;
                            if (args.get(1).equalsIgnoreCase("pre")) {
                                // pre:
                                bitmapData = getPreData(ss, code, dataSize, statementBytesMap);
                            } else if (args.get(1).equalsIgnoreCase("post")) {
                                // post:
                                bitmapData = getPostData(ss, code, dataSize, positionInBinaryMap, statementBytesMap);
                            } else {
                                config.warn("Expected second argument to be either 'pre' or 'post' in a 'bitmap' coment tag (ignoring): " + ss.comment);
                                break;
                            }
                            if (bitmapData != null) {
                                // convert it to an image!
                                int zoom = Integer.parseInt(args.get(4));
                                img = generateBitmapImage(bitmapData, width, height, zoom);
                            }
                            break;
                        }
                        case "and-or-bitmap-with-size":
                        {
                            // check if we need to get the data from before, or from afterwards:
                            if (args.size() != 3) {
                                config.warn("Expected 3 arguments in a 'and-or-bitmap-with-size' comment tag (ignoring): " + ss.comment);                        
                                break;
                            }
                            List<Byte> bitmapData = null;
                            if (args.get(1).equalsIgnoreCase("post")) {
                                // post:
                                bitmapData = getPostDataAndOrWithSize(ss, code, positionInBinaryMap, statementBytesMap);
                            } else {
                                config.warn("Expected second argument to be 'post' in a 'bitmap' coment tag (ignoring): " + ss.comment);
                                break;
                            }
                            if (bitmapData != null) {
                                // convert it to an image!
                                int zoom = Integer.parseInt(args.get(2));
                                img = generateAndOrBitmapImage(bitmapData, zoom);
                            }
                            break;
                        }
                        default:
                            config.warn(args.get(0) + " argument not recognized in comment tag (ignoring): " + ss.comment);
                    }

                    if (img != null) {
                        // Save image to disk (see if we need to create a folder):
                        String folderName = outputFileName + "-assets";
                        File folder = new File(folderName);
                        if (!folder.exists()) {
                            folder.mkdirs();
                        }
                        String fileName = folderName + File.separator + "img" + imgIndex + ".png";
                        try {
                            ImageIO.write(img, "png", new File(fileName));
                        } catch (IOException ex) {
                            config.error("Cannot write to file " + fileName);
                        }
                        imgIndex += 1;

                        // replace command by image tag:

                        String imageTag = "<img src=\"" + fileName + "\" alt=\"MDL bitmap visualization\" width=\"" + img.getWidth() + "\" height=\"" + img.getHeight() + "\">";
                        ssString = ssString.replace(cmdString, imageTag);
                    }
                } else {
                    config.warn("Could not parse "+GFX_TAG+" tag (ignoring): " + ss.comment);
                }
            }
            
            sb.append("<td><pre>");
            sb.append(ssString);
            sb.append("</pre></td>");
            sb.append("</tr>\n");
            
        }
    }    
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    }   

    
    public String reconstructIndentation(String ssString, CodeStatement ss)
    {
        if (ss.sl == null || ss.sl.line == null) return ssString;
        String originalLine = ss.sl.line;
        // get indentation:
        String indentation = "";
        for(int i = 0;i<originalLine.length();i++) {
            char c = originalLine.charAt(i);
            if (c == ' ' || c == '\t') {
                indentation += c;
            } else {
                break;
            }
        }
        return indentation += ssString.trim();
    }
    
    
    private List<Byte> getPreData(CodeStatement ss, CodeBase code, int dataLeft,
            HashMap<CodeStatement, BinaryGenerator.StatementBinaryEffect> statementBytesMap) 
    {
        List<Byte> data = new ArrayList<>();

        while(dataLeft > 0 && ss != null) {
            BinaryGenerator.StatementBinaryEffect statementBytes = statementBytesMap.get(ss);
            if (statementBytes == null) {
                config.warn("Error in getPreData. This is probably a bug, please report it.");
                return null;
            }
            if (statementBytes.bytes != null) {
                for(int i = statementBytes.bytes.length-1;i>=0 && dataLeft > 0;i--, dataLeft--) {
                    data.add(0, statementBytes.bytes[i]);
                }
            }
            
            ss = ss.source.getPreviousStatementTo(ss, code);
        }
        if (ss == null) {
            // There wasn't ehough data, ignore
            return null;
        }
        return data;
    }

    
    private List<Byte> getPostData(CodeStatement ss, CodeBase code, int dataLeft, HashMap<CodeStatement, Integer> positionInBinaryMap, HashMap<CodeStatement, BinaryGenerator.StatementBinaryEffect> statementBytesMap)
    {
        List<Byte> data = new ArrayList<>();
        
        ss = ss.source.getNextStatementTo(ss, code);
        while(dataLeft > 0 && ss != null) {
            BinaryGenerator.StatementBinaryEffect statementBytes = statementBytesMap.get(ss);
            if (statementBytes == null) {
                config.warn("Error in getPreData. This is probably a bug, please report it.");
                return null;
            }
            if (statementBytes.bytes != null) {
                for(int i = 0;i < statementBytes.bytes.length && dataLeft > 0;i++, dataLeft--) {
                    data.add(statementBytes.bytes[i]);
                }
            }
            
            ss = ss.source.getNextStatementTo(ss, code);
        }
        if (ss == null) {
            // There wasn't ehough data, ignore
            return null;
        }
        return data;        
    }


    private List<Byte> getPostDataAndOrWithSize(CodeStatement ss, CodeBase code, HashMap<CodeStatement, Integer> positionInBinaryMap, HashMap<CodeStatement, BinaryGenerator.StatementBinaryEffect> statementBytesMap)
    {
        boolean knowSize = false;
        int dataLeft = 2;
        List<Byte> data = new ArrayList<>();
        
        ss = ss.source.getNextStatementTo(ss, code);
        while(dataLeft > 0 && ss != null) {
            BinaryGenerator.StatementBinaryEffect statementBytes = statementBytesMap.get(ss);
            if (statementBytes == null) {
                config.warn("Error in getPreData. This is probably a bug, please report it.");
                return null;
            }
            if (statementBytes.bytes != null) {
                for(int i = 0;i < statementBytes.bytes.length && dataLeft > 0;i++) {
                    data.add(statementBytes.bytes[i]);
                    dataLeft--;
                    if (dataLeft == 0 && !knowSize) {
                        knowSize = true;
                        int height = data.get(0);
                        int width = data.get(1);
                        if (height<0) height += 256;
                        if (width<0) width += 256;
                        dataLeft = width * height * 2;
                    }
                }
            }
            
            ss = ss.source.getNextStatementTo(ss, code);
        }
        if (ss == null) {
            // There wasn't ehough data, ignore
            return null;
        }
        return data;        
    }

    
    private BufferedImage generateBitmapImage(List<Byte> bitmapData, Integer width, Integer height, int zoom)
    {
        BufferedImage img = new BufferedImage(width*8*zoom, height*zoom, BufferedImage.TYPE_INT_ARGB);
        for(int i = 0;i<height;i++) {
            for(int j = 0;j<width;j++) {
                int v = bitmapData.get(i*width + j);
                if (v<0) v += 256;
                for(int k = 0;k<8;k++) {
                    if ((v & 0x80) != 0) {
                        // pixel!
                        putZoomedPixel(img, j*8+k, i, zoom, COLOR_PIXEL_ON);
                    } else {
                        // no pixel:
                        putZoomedPixel(img, j*8+k, i, zoom, COLOR_PIXEL_OFF);
                    }
                    v <<= 1;
                }
            }
        }
        return img;
    }

    
    private BufferedImage generateAndOrBitmapImage(List<Byte> bitmapData, int zoom)
    {
        int height = bitmapData.get(0);
        int width = bitmapData.get(1);
        if (height<0) height += 256;
        if (width<0) width += 256;
        BufferedImage img = new BufferedImage(width*8*zoom, height*zoom, BufferedImage.TYPE_INT_ARGB);
        for(int i = 0;i<height;i++) {
            for(int j = 0;j<width;j++) {
                int andMask = bitmapData.get(2+(i*width + j)*2);
                int orMask = bitmapData.get(2+(i*width + j)*2+1);
                if (andMask<0) andMask += 256;
                if (orMask<0) orMask += 256;
                for(int k = 0;k<8;k++) {
                    if ((andMask & 0x80) != 0) {
                        // background:
                        putZoomedPixel(img, j*8+k, i, zoom, COLOR_PIXEL_OFF);
                    } else {
                        // pixel masked!
                        putZoomedPixel(img, j*8+k, i, zoom, COLOR_PIXEL_MASKED);
                    }
                    if ((orMask & 0x80) != 0) {
                        // pixel on!
                        putZoomedPixel(img, j*8+k, i, zoom, COLOR_PIXEL_ON);
                    }
                    andMask <<= 1;
                    orMask <<= 1;
                }
            }
        }
        return img;
    }

    private void putZoomedPixel(BufferedImage img, int x, int y, int zoom, int color) 
    {
        for(int i = 0;i<zoom;i++) {
            for(int j = 0;j<zoom;j++) {
                img.setRGB(x*zoom+j, y*zoom+i, color);
            }
        }
    }
}
