package msb.com.vn.qrservice.domain.repository;

import msb.com.vn.qrservice.domain.entity.VietQrInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VietQrInfoRepository extends JpaRepository<VietQrInfo, String> {

    Optional<VietQrInfo> findByQrCodeId(String qrCodeId);

    List<VietQrInfo> findByBankAccount(String bankAccount);
}
