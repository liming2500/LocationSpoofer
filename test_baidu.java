import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.model.LatLngBounds;
public class test_baidu {
    public static void main(String[] args) {
        LatLngBounds b = null;
        MapStatusUpdateFactory.newLatLngBounds(b, 100, 100);
    }
}
