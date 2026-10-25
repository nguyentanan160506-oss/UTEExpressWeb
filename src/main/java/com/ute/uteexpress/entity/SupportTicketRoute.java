package com.ute.uteexpress.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

/** Explicit customer-intake routing configuration. No inferred or seeded fallback route. */
@Entity
@Table(name="support_ticket_routes",schema="dbo",uniqueConstraints={
    @UniqueConstraint(name="UQ_support_ticket_routes_origin",columnNames={"origin_branch_id"})})
public class SupportTicketRoute {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id",nullable=false)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="origin_branch_id",nullable=false,foreignKey=@ForeignKey(name="FK_support_ticket_routes_origin"))
    private Branch originBranch;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="handling_branch_id",nullable=false,foreignKey=@ForeignKey(name="FK_support_ticket_routes_handling"))
    private Branch handlingBranch;
    @Column(name="is_active",nullable=false) @ColumnDefault("1")
    private boolean active=true;
    public Long getId(){return id;}
    public Branch getOriginBranch(){return originBranch;}
    public void setOriginBranch(Branch value){originBranch=value;}
    public Branch getHandlingBranch(){return handlingBranch;}
    public void setHandlingBranch(Branch value){handlingBranch=value;}
    public boolean isActive(){return active;}
    public void setActive(boolean value){active=value;}
}
