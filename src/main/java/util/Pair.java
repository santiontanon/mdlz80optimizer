/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package util;

public class Pair<T1,T2> {
	public T1 left;
	public T2 right;
	
	public Pair(T1 a,T2 b) {
		left = a;
		right = b;
	}   
        
        public T1 getLeft() {
            return left;
        }

        public T2 getRight() {
            return right;
        }
        
        public void setLeft(T1 a_left) {
            left = a_left;
        }

        public void setRight(T2 a_right) {
            right = a_right;
        }
        
        @Override
        public String toString() {
            return "<" + left + "," + right + ">";
        }
        
        public static <T1, T2> Pair<T1, T2>of(T1 a, T2 b) {
            return new Pair<>(a, b);
        }
}
