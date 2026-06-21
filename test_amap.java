import com.amap.api.services.route.DriveStep;
import java.lang.reflect.Method;
public class test_amap {
    public static void main(String[] args) {
        for (Method m : DriveStep.class.getMethods()) {
            if (m.getName().toLowerCase().contains("traffic")) {
                System.out.println(m.getName());
            }
        }
    }
}
