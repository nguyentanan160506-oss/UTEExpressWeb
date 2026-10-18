package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.ShipmentOptions;
import com.ute.uteexpress.entity.Branch;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
public class BranchNetworkService {
    private final BranchServiceAreaRepository areas;
    private final BranchRepository branches;
    public BranchNetworkService(BranchServiceAreaRepository areas, BranchRepository branches) {
        this.areas = areas; this.branches = branches;
    }

    public List<ShipmentOptions.BranchOption> servingBranches(
            @NotBlank @Size(max = 100) String province, @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String ward, @NotNull ShipmentMethod method) {
        return areas.findServingBranches(province.strip(), district.strip(), ward.strip(), method,
                BranchStatus.ACTIVE, BranchType.LOCAL_BRANCH, BranchType.CENTRAL_HUB).stream()
            .map(branch -> {
                requireValidHub(branch);
                return new ShipmentOptions.BranchOption(branch.getId(), branch.getName(), branch.getProvince());
            }).toList();
    }

    public Long configuredHub(@NotNull @Positive Long localBranchId) {
        Branch branch = branches.findByIdAndStatus(localBranchId, BranchStatus.ACTIVE)
            .orElseThrow(() -> new LogisticsRuleException("Chi nhánh chưa đủ điều kiện sử dụng."));
        return requireValidHub(branch).getId();
    }

    public List<ShipmentOptions.BranchOption> deliveryBranches(
            @NotBlank @Size(max = 100) String province, @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String ward) {
        return areas.findDeliveryBranches(province.strip(), district.strip(), ward.strip(),
                BranchStatus.ACTIVE, BranchType.LOCAL_BRANCH, BranchType.CENTRAL_HUB).stream()
            .map(branch -> {
                requireValidHub(branch);
                return new ShipmentOptions.BranchOption(branch.getId(), branch.getName(), branch.getProvince());
            }).toList();
    }

    private Branch requireValidHub(Branch branch) {
        Branch hub = branch.getParentHub();
        if (branch.getStatus() != BranchStatus.ACTIVE || branch.getBranchType() != BranchType.LOCAL_BRANCH
                || hub == null || hub.getStatus() != BranchStatus.ACTIVE || hub.getBranchType() != BranchType.CENTRAL_HUB
                || branch.getId().equals(hub.getId())) {
            throw new LogisticsRuleException("Chi nhánh chưa có kho tập kết hoạt động hợp lệ.");
        }
        return hub;
    }
}
