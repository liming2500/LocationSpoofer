import java.lang.reflect.*;
public class test_gnss {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("android.location.GnssStatus");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println(f.getName() + " : " + f.getType().getSimpleName());
        }
    }
}
