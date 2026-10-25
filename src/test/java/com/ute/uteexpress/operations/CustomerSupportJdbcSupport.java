package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.operations.CustomerTicketCommands;
import com.ute.uteexpress.dto.operations.TicketCommands;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;

abstract class CustomerSupportJdbcSupport extends OperationsJdbcSupport {
    Shipment shipment;
    @BeforeEach void customerSetup(){
        shipment=db(()->{
            SupportTicketRoute route=new SupportTicketRoute();route.setOriginBranch(f.source);route.setHandlingBranch(f.source);em.persist(route);
            return f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.minusHours(1));
        });as(f.owner,"ROLE_USER");
    }
    String token(){return UUID.randomUUID().toString().replace("-","");}
    CustomerTicketCommands.Create createCommand(){return new CustomerTicketCommands.Create(shipment.getId(),TicketCategory.DELAY,"Cần hỗ trợ vận đơn","Mô tả từ khách hàng",token());}
    Long createCustomer(){return tickets.createCustomer(createCommand());}
    Long version(Long id){return ticketRows.findById(id).orElseThrow().getVersion();}
    CustomerTicketCommands.Message message(Long id,String body){return new CustomerTicketCommands.Message(version(id),body,token());}
    void state(Long id,TicketStatus target){
        as(f.manager,"ROLE_MANAGER");
        if(target!=TicketStatus.OPEN){
            tickets.assign(id,new TicketCommands.Assign(version(id),f.secondEmployee.getId(),null));
            if(target!=TicketStatus.ASSIGNED){
                tickets.changeStatus(id,new TicketCommands.ChangeStatus(version(id),TicketStatus.PROCESSING,null));
                if(target==TicketStatus.WAITING_CUSTOMER || target==TicketStatus.WAITING_INTERNAL)tickets.changeStatus(id,new TicketCommands.ChangeStatus(version(id),target,null));
                if(target==TicketStatus.RESOLVED || target==TicketStatus.CLOSED || target==TicketStatus.REOPENED){
                    tickets.resolve(id,new TicketCommands.Decision(version(id),"Kết quả công khai chính thức"));
                    if(target==TicketStatus.CLOSED)tickets.close(id,new TicketCommands.Decision(version(id),"Đã kiểm tra"));
                    if(target==TicketStatus.REOPENED)tickets.reopen(id,new TicketCommands.Decision(version(id),"Xem xét thêm"));
                }
            }
        }as(f.owner,"ROLE_USER");
    }
    Long event(Long id,TicketAction action){return db(()->em.createQuery("select e.id from SupportTicketEvent e where e.ticket.id=:id and e.action=:action order by e.id desc",Long.class).setParameter("id",id).setParameter("action",action).setMaxResults(1).getSingleResult());}
}
