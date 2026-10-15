package com.ute.uteexpress.dto.shipment;

import java.util.List;

public record ShipmentOptions(List<BranchOption> branches, List<ServiceOption> services) {
    public ShipmentOptions { branches = List.copyOf(branches); services = List.copyOf(services); }
    public record BranchOption(Long id, String name, String province) { }
    public record ServiceOption(Long id, String name) { }
}
