package com.ute.uteexpress.service.reports;

import com.ute.uteexpress.dto.admin.AuditViews.*;
import com.ute.uteexpress.dto.admin.ReportViews;
import com.ute.uteexpress.enums.AuditResourceType;
import com.ute.uteexpress.repository.AdminAuditQueries;
import com.ute.uteexpress.service.AdminRuleException;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true,timeout=30) @PreAuthorize("hasRole('ADMIN')")
public class AdminAuditService {
    private final AdminReportingAccess access;private final ReportPolicy policy;private final AdminAuditQueries queries;
    public AdminAuditService(AdminReportingAccess access,ReportPolicy policy,AdminAuditQueries queries){this.access=access;this.policy=policy;this.queries=queries;}
    public Page read(Request request,int page,int size){
        var actor=access.requireAdmin();ReportPolicy.page(page,size);
        if(request==null)throw new AdminRuleException("Bộ lọc audit không hợp lệ.");
        var dates=policy.filter(new ReportViews.Request(ReportViews.Dataset.SHIPMENTS,request.period(),request.from(),request.to(),request.branchId(),null,null,null));
        if(request.actorId()!=null && request.actorId()<=0)throw new AdminRuleException("Actor ID phải là số dương.");
        String action=blank(request.action()),entityType=blank(request.entityType()),id=blank(request.entityId());
        if(action!=null && !action.matches("[A-Z][A-Z0-9_]{0,99}"))throw new AdminRuleException("Action phải là mã hợp lệ, tối đa 100 ký tự.");
        if(entityType!=null && !entityTypes().contains(entityType))throw new AdminRuleException("Loại đối tượng không hợp lệ.");
        if(id!=null && (id.length()>50 || !id.matches("[A-Za-z0-9_-]+")))throw new AdminRuleException("ID đối tượng không hợp lệ, tối đa 50 ký tự.");
        var filter=new Filter(dates.from(),dates.to(),request.source()==null?Source.ALL:request.source(),request.actorId(),action,entityType,id,request.branchId());
        var result=new Page(filter,queries.rows(filter,actor.getId(),page,size),page,size,queries.count(filter,actor.getId()));access.requireAdmin();return result;
    }
    public static List<String> entityTypes(){var list=new ArrayList<String>(Arrays.stream(AuditResourceType.values()).map(Enum::name).toList());list.add("TRANSPORT_TRIP");list.add("SUPPORT_TICKET");return List.copyOf(list);}
    private String blank(String s){return s==null || s.isBlank()?null:s.strip();}
}
