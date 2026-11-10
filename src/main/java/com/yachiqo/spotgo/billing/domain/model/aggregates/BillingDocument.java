package com.yachiqo.spotgo.billing.domain.model.aggregates;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.*;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import static com.yachiqo.spotgo.billing.domain.model.valueobjects.BillingValues.*;

@Entity @Table(name="billing_documents",uniqueConstraints=@UniqueConstraint(columnNames={"series","document_number"})) @Getter
public class BillingDocument {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="document_id",nullable=false,updatable=false) private UUID id;

 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id",nullable=false,unique=true,updatable=false) private DigitalPayment payment;
 @Enumerated(EnumType.STRING) @Column(name="document_type",nullable=false,updatable=false) private BillingDocumentType documentType;
 @Column(name="document_number",nullable=false,updatable=false) private String documentNumber;
 @Column(nullable=false,updatable=false) private String series;
 @Column(name="legal_name",nullable=false,updatable=false) private String legalName;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="tax_data",columnDefinition="jsonb",nullable=false,updatable=false) private Map<String,Object> taxData;
 @Column(name="issued_at",nullable=false,updatable=false) private Instant issuedAt;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private BillingDocumentStatus status;

 protected BillingDocument() {}
 public BillingDocument(DigitalPayment payment,BillingDocumentType type,String number,String series,String name,Map<String,Object> tax,Instant now){
  if(payment.getStatus()!=PaymentStatus.APPROVED)throw new IllegalArgumentException("A billing document can be issued only for an approved payment.");
  this.payment=payment;documentType=Objects.requireNonNull(type);documentNumber=text(number);this.series=text(series);legalName=text(name);
  taxData=new LinkedHashMap<>(tax);issuedAt=now;status=BillingDocumentStatus.ISSUED;
 }
 public UUID getPaymentId(){return payment.getId();}
 public void voidDocument(){status=BillingDocumentStatus.VOID;}

}
