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

    public void write(int arg0) throws IOException {
        data.add(arg0);
    }

    public List<Integer> getData() {
        return data;
    }
}
