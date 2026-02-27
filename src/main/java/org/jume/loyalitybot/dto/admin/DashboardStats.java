package org.jume.loyalitybot.dto.admin;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;

@Data
@Builder
public class DashboardStats {
    private long totalCustomers;
    private long activeCustomers;
    private long pendingCustomers;
    private BigDecimal totalBonusesIssued;
    private BigDecimal totalRevenue;
    private long transactionsToday;
    private long transactionsThisWeek;
    private long transactionsThisMonth;
}
