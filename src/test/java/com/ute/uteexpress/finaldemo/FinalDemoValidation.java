package com.ute.uteexpress.finaldemo;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Read-only minimum acceptance, not a claim of browser or production-load coverage. */
public class FinalDemoValidation {
    @PersistenceContext EntityManager em;
    @Autowired BranchNetworkService network;
    @Transactional(readOnly=true)
    public void validate(){
        var locals=em.createQuery("select b from Branch b where b.code like 'FD-B-%'",Branch.class).getResultList();
        if(locals.size()!=35)throw new IllegalStateException("35 demo customer branches required");
        var edges=new HashSet<String>();
        for(var r:em.createQuery("select r from TransportRoute r where r.origin.code like 'FD-%'",TransportRoute.class).getResultList()){
            if(!Boolean.TRUE.equals(r.getActive()))throw new IllegalStateException("Inactive demo route");TransportRouting.validateRoute(r.getOrigin(),r.getDestination(),r.getLeg());edges.add(r.getOrigin().getId()+":"+r.getDestination().getId());
        }
        if(edges.size()!=112)throw new IllegalStateException("112 directed routes required");
        for(var a:locals){
            for(var method:ShipmentMethod.values())if(network.quoteServingBranches(a.getProvince(),a.getDistrict(),a.getWard(),method).stream().noneMatch(b->b.id().equals(a.getId())))throw new IllegalStateException("Missing origin coverage");
            if(network.quoteDeliveryBranches(a.getProvince(),a.getDistrict(),a.getWard()).stream().noneMatch(b->b.id().equals(a.getId())))throw new IllegalStateException("Missing delivery coverage");
            for(var b:locals){var s=new Shipment();s.setOriginBranch(a);s.setDestinationBranch(b);s.setOriginHub(a.getParentHub());s.setDestinationHub(b.getParentHub());var path=TransportRouting.path(s);
                for(int i=1;i<path.size();i++)if(!edges.contains(path.get(i-1).getId()+":"+path.get(i).getId()))throw new IllegalStateException("Missing physical leg");
            }
        }
        for(var n:FinalDemoNetwork.nodes()){
            var branch=em.createQuery("select b from Branch b where b.code=:code",Branch.class).setParameter("code","FD-"+n.code()).getSingleResult();
            long managers=em.createQuery("select count(m) from ManagerBranch m, Employee e, UserRole r where m.branch=:b and e.branch=:b and e.user=m.manager and r.user=m.manager and r.role.name='ROLE_MANAGER' and e.status=:active and m.manager.status=:userActive",Long.class).setParameter("b",branch).setParameter("active",EmployeeStatus.ACTIVE).setParameter("userActive",UserStatus.ACTIVE).getSingleResult();
            if(managers<1)throw new IllegalStateException("Manager/employee binding missing");
            if(branch.getBranchType()==BranchType.LOCAL_BRANCH&&em.createQuery("select count(e) from Employee e, UserRole r where e.branch=:b and r.user=e.user and r.role.name='ROLE_SHIPPER' and e.position=:p and e.status=:active and e.user.status=:ua",Long.class).setParameter("b",branch).setParameter("p",EmployeePosition.COURIER_DRIVER).setParameter("active",EmployeeStatus.ACTIVE).setParameter("ua",UserStatus.ACTIVE).getSingleResult()<1)throw new IllegalStateException("Shipper binding missing");
        }
        for(String code:FinalDemoSeed.SCENARIOS){var s=em.createQuery("select s from Shipment s where s.note=:note",Shipment.class).setParameter("note","FD-SCENARIO:"+code).getSingleResult();
            if(s.getPriceSnapshot()==null||s.getTotalFee().compareTo(s.getShippingFee().add(s.getPickupFee()).add(s.getInsuranceFee()))!=0
                    ||em.createQuery("select count(e) from ShipmentEvent e where e.shipment=:s",Long.class).setParameter("s",s).getSingleResult()<1)throw new IllegalStateException("Missing price/event snapshot");
        }
        System.out.println("PASS network 35 coverage sites 1225 branch pairs real-service scenarios and actor bindings");
    }
}
