import android.location.GnssStatus;
public class test_builder {
    public static void main(String[] args) throws Exception {
        Class<?> builderClass = Class.forName("android.location.GnssStatus$Builder");
        System.out.println("Builder found: " + builderClass);
    }
}
