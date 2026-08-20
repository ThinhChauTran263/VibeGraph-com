package com.vibegraph;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.vibegraph.auth.service.AdminImportPricingManagementService;
import com.vibegraph.auth.dto.AdminImportPricingUpdateRequest;
import com.vibegraph.auth.repository.ImportPricingTierRepository;
import java.util.List;

@SpringBootTest
public class DebugTest {
    @Autowired
    AdminImportPricingManagementService s;
    
    @Autowired
    ImportPricingTierRepository r;

    @Test
    public void test() {
        try {
            s.replace("IMPORT_ARCHIVE", new AdminImportPricingUpdateRequest(List.of(
                new AdminImportPricingUpdateRequest.Tier("SMALL", 100, 2),
                new AdminImportPricingUpdateRequest.Tier("MEDIUM", 200, 5),
                new AdminImportPricingUpdateRequest.Tier("LARGE", 500, 15),
                new AdminImportPricingUpdateRequest.Tier("XLARGE", null, 40)
            )));
            r.flush(); // FLUSH TO DB TO TRIGGER CONSTRAINT!
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
