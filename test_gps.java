import java.lang.reflect.*;
public class test_gps {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("android.location.GpsSatellite");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println(f.getName() + " : " + f.getType().getSimpleName());
        }
    }
}
