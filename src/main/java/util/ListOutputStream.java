/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class ListOutputStream extends OutputStream {
    List<Integer> data = new ArrayList<>();

    @Override
    public void write(int bytevalue) throws IOException {
        data.add(bytevalue&0xff);
    }

    public List<Integer> getData() {
        return data;
    }
}
