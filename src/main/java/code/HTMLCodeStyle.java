/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package code;

/**
 *
 * @author santi
 */
public class HTMLCodeStyle {
    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_LABEL_DEFINITION = 2;
    public static final int TYPE_LABEL_USE = 3;
    public static final int TYPE_MNEMONIC = 4;
    public static final int TYPE_CONSTANT = 5;
    public static final int TYPE_MACRO = 6;
    
    public String backgroundColor = "ffffff";
    public String defaultStyle = "color: #000000";
    public String comentStyle = "color: #880000";
    public String labelStyle = "color: #000088";
    public String mnemonicStyle = "color: #006600";
    public String constantStyle = "color: #888888";
    public String macroStyle = "color: #880088;font-weight: bold";
    
    public boolean labelsAsLinks = true;
    
    
    public String getStyle(int type) {
        switch(type) {
            case TYPE_COMMENT: return comentStyle;
            case TYPE_LABEL_DEFINITION: 
            case TYPE_LABEL_USE:
                return labelStyle;
            case TYPE_MNEMONIC: return mnemonicStyle;
            case TYPE_CONSTANT: return constantStyle;
            case TYPE_MACRO: return macroStyle;
            default:
                return defaultStyle;
        }
    }
    
     
    public static String renderStyledHTMLPiece(String piece, int type, HTMLCodeStyle style) {
        if (style != null) {
            if (type == TYPE_LABEL_DEFINITION && style.labelsAsLinks) {
                return "<span style=\"" + style.getStyle(type) + "\"><a name=\""+piece+"\">" + piece + "</a></span>";
            } else if (type == TYPE_LABEL_USE && style.labelsAsLinks) {
//                return "<span style=\"" + style.getStyle(type) + "\"><a href=\"#"+piece+"\">" + piece + "</a></span>";
                return "<span style=\"" + style.getStyle(type) + "\"><a style=\"" + style.getStyle(type) + "\" href=\"#"+piece+"\">" + piece + "</a>";
            } else {
                return "<span style=\"" + style.getStyle(type) + "\">" + piece + "</span>";
            }
        } else {
            return piece;
        }
    }
}
