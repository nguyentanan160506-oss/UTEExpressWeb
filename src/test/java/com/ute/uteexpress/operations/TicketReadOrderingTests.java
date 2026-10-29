package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.operations.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketReadOrderingTests {
    OperationsAccessService access=mock(OperationsAccessService.class);
    EmployeeRepository employees=mock(EmployeeRepository.class);
    SupportTicketRepository tickets=mock(SupportTicketRepository.class);
    TicketReadService service=new TicketReadService(access,tickets,mock(SupportTicketMessageRepository.class),mock(SupportTicketEventRepository.class),employees,Clock.systemUTC());
    void scope(){var user=new User();user.setId(5L);var b=new Branch();b.setId(1L);when(access.scope(1L)).thenReturn(new OperationsAccessService.Scope(user,b));}
    @Test void candidateIdSortHasOnlyOneColumnAndKeepsBranchScope(){
        scope();when(employees.findTicketCandidates(eq(1L),any())).thenReturn(Page.empty());
        service.candidates(1L,2);var page=ArgumentCaptor.forClass(Pageable.class);verify(employees).findTicketCandidates(eq(1L),page.capture());
        assertEquals(2,page.getValue().getPageNumber());assertEquals(20,page.getValue().getPageSize());assertEquals(List.of(Sort.Order.desc("id")),page.getValue().getSort().stream().toList());verify(access).scope(1L);
    }
    @Test void unauthorizedCandidateScopeNeverQueriesEmployees(){when(access.scope(1L)).thenThrow(new AccessDeniedException("Forbidden"));assertThrows(AccessDeniedException.class,()->service.candidates(1L,0));verifyNoInteractions(employees);}
    @Test void normalTicketSortRetainsDeterministicIdTieBreaker(){
        scope();when(tickets.search(eq(5L),eq(1L),isNull(),isNull(),isNull(),isNull(),eq(false),eq(false),any(),any())).thenReturn(Page.empty());
        service.list(1L,null,null,null,null,false,false,0,20,"createdAt");var page=ArgumentCaptor.forClass(Pageable.class);verify(tickets).search(eq(5L),eq(1L),isNull(),isNull(),isNull(),isNull(),eq(false),eq(false),any(),page.capture());
        assertEquals(List.of(Sort.Order.desc("createdAt"),Sort.Order.desc("id")),page.getValue().getSort().stream().toList());verify(access,times(2)).scope(1L);
    }
}
