package acme;

/**
 * @author acme
 * @date 2019/9/3 8:15 PM
 */
public class GenString {
    public static byte[] getBytes(int n){
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n * 1024; i++) {
            sb.append('A');
        }
        return sb.toString().getBytes();
    }
}
