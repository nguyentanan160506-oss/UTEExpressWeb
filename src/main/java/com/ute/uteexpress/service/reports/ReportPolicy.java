package com.ute.uteexpress.service.reports;

import com.ute.uteexpress.dto.admin.ReportViews.*;
import com.ute.uteexpress.dto.admin.ReportViews.Period;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.AdminRuleException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class ReportPolicy {
    private final Clock clock;private final BranchRepository branches;private final ShippingServiceRepository services;
    public ReportPolicy(Clock clock,BranchRepository branches,ShippingServiceRepository services){this.clock=clock;this.branches=branches;this.services=services;}
    public Filter filter(Request r){
        if(r==null)throw invalid();
        LocalDate today=LocalDate.ofInstant(clock.instant(),ZoneOffset.UTC),from=r.from(),to=r.to();
        switch(r.period()==null?Period.LAST_7_DAYS:r.period()){
            case TODAY->{from=today;to=today;}case LAST_7_DAYS->{from=today.minusDays(6);to=today;}
            case LAST_30_DAYS->{from=today.minusDays(29);to=today;}case THIS_MONTH->{from=today.withDayOfMonth(1);to=today;}case CUSTOM->{}
        }
        if(from==null || to==null || from.getYear()<1 || to.getYear()>9998 || from.isAfter(to) || to.isAfter(today) || ChronoUnit.DAYS.between(from,to)>=93)throw invalid();
        if(r.branchId()!=null && (r.branchId()<=0 || !branches.existsById(r.branchId())))throw new AdminRuleException("Chi nhánh không hợp lệ.");
        if(r.serviceId()!=null && (r.serviceId()<=0 || !services.existsById(r.serviceId())))throw new AdminRuleException("Dịch vụ không hợp lệ.");
        var dataset=r.dataset()==null?Dataset.SHIPMENTS:r.dataset();String status=r.status()==null || r.status().isBlank()?null:r.status();
        if(status!=null && !AdminReportQueries.definition(dataset).statuses().contains(status))throw new AdminRuleException("Trạng thái không phù hợp nhóm báo cáo.");
        return new Filter(dataset,from,to,from.atStartOfDay(),to.plusDays(1).atStartOfDay(),r.branchId(),r.serviceId(),status,r.trend()==null?Trend.DAY:r.trend());
    }
    public static void page(int page,int size){if(page<0 || page>100000 || size<1 || size>50)throw new AdminRuleException("Phân trang phải hợp lệ, tối đa 50 dòng mỗi trang.");}
    private AdminRuleException invalid(){return new AdminRuleException("Khoảng ngày UTC phải hợp lệ, tối đa 93 ngày, bao gồm ngày kết thúc và không vượt ngày hiện tại.");}
}
