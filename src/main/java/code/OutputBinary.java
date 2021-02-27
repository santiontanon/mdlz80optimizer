/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package code;

/**
 *
 * @author santi
 */
public class OutputBinary {
    public String fileName = null;
    public SourceFile main = null;

    public OutputBinary(String a_fileName, SourceFile a_main)
    {
        fileName = a_fileName;
        main = a_main;
    }    
}
