
import java.io.IOException;

public class test {
    public static void main(String[] args)throws IOException {
        ProcessBuilder p1=new ProcessBuilder("ls");
        Process process =p1.start();
    }
}
