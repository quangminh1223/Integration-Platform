package msb.com.vn.qrservice.cache;

import msb.com.vn.qrservice.cache.model.ServiceRouteConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cache bảng SPF (service route config) trên Redis.
 *
 * Toàn bộ logic save/load nằm ở {@link AbstractTableCache}; class này chỉ khai báo:
 *  - hash key  = "service-routes"
 *  - kiểu      = {@link ServiceRouteConfig}
 *  - khóa field = SPF_CODE
 */
@Component
public class ServiceRouteCache extends AbstractTableCache<ServiceRouteConfig> {

    public ServiceRouteCache(ObjectProvider<SystemCacheStore> storeProvider) {
        super(storeProvider, "service-routes", ServiceRouteConfig.class, ServiceRouteConfig::getSpfCode);
    }

    /** Alias thân thiện theo nghiệp vụ (tùy chọn). */
    public Optional<ServiceRouteConfig> findByCode(String spfCode) {
        return findByKey(spfCode);
    }
}
