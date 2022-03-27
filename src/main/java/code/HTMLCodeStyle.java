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
    public static final int TYPE_LABEL = 2;
    public static final int TYPE_MNEMONIC = 3;
    public static final int TYPE_CONSTANT = 4;
    public static final int TYPE_MACRO = 5;
    
    public String backgroundColor = "ffffff";
    public String defaultStyle = "color: #000000";
    public String comentStyle = "color: #880000";
    public String labelStyle = "color: #000088";
    public String mnemonicStyle = "color: #008800";
    public String constantStyle = "color: #888888";
    public String macroStyle = "color: #880088;font-weight: bold";
    
    
    public String getStyle(int type) {
        switch(type) {
            case TYPE_COMMENT: return comentStyle;
            case TYPE_LABEL: return labelStyle;
            case TYPE_MNEMONIC: return mnemonicStyle;
            case TYPE_CONSTANT: return constantStyle;
            case TYPE_MACRO: return macroStyle;
            default:
                return defaultStyle;
        }
    }
    
     
    public static String renderStyledHTMLPiece(String piece, int type, HTMLCodeStyle style) {
        if (style != null) {
            return "<span style=\"" + style.getStyle(type) + "\">" + piece + "</span>";
        } else {
            return piece;
        }
    }
}
