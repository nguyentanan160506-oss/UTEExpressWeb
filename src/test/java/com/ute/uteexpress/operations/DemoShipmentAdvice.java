package com.ute.uteexpress.operations;

import com.ute.uteexpress.controller.UserShipmentController;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.BranchServiceAreaRepository;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Read-only hints from the actual disposable demo coverage; never bundled in production. */
@ControllerAdvice(assignableTypes = UserShipmentController.class)
@Profile({"demo-local","demo-sql"})
@ConditionalOnProperty(name="uteexpress.demo.enabled",havingValue="true")
public class DemoShipmentAdvice {
    private final BranchServiceAreaRepository areas;
    public DemoShipmentAdvice(BranchServiceAreaRepository areas,DataSource source,org.springframework.core.env.Environment environment)throws Exception {
        this.areas=areas;
        try(var connection=source.getConnection()){
            if(environment.matchesProfiles("demo-sql"))com.ute.uteexpress.demo.SqlDemoTarget.verify(connection);
            else if(!"H2".equals(connection.getMetaData().getDatabaseProductName())
                    ||!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:uteexpress_demo_"))
                throw new IllegalStateException("Demo coverage hints require the owned disposable H2 database");
        }
    }
    @ModelAttribute("demoCoverageExamples")
    @Transactional(readOnly=true)
    public List<String> coverageExamples(){
        return areas.findAll().stream().filter(a->a.isActive()&&a.isSupportsDelivery())
            .filter(a->a.getBranch().getStatus()==BranchStatus.ACTIVE
                &&a.getBranch().getBranchType()==BranchType.LOCAL_BRANCH
                &&a.getBranch().getParentHub()!=null
                &&a.getBranch().getParentHub().getStatus()==BranchStatus.ACTIVE
                &&a.getBranch().getParentHub().getBranchType()==BranchType.CENTRAL_HUB)
            .map(a->a.getProvince()+" / "+a.getDistrict()+" / "+(a.getWard().isBlank()?"Các phường/xã trong quận/huyện":a.getWard()))
            .distinct().sorted().toList();
    }
}
