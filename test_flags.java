import java.lang.reflect.*;
public class test_flags {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("android.location.GnssStatus");
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                System.out.println(f.getName() + " = " + f.get(null));
            }
        }
    }
}
