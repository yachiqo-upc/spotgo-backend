package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa;
import com.yachiqo.spotgo.parking.application.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.UUID;
@Component
public class ParkingPaymentAdapter implements ParkingPaymentGateway {
 private final JdbcTemplate jdbc;
 public ParkingPaymentAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void requireApproved(UUID id,UUID profile,BigDecimal amount) {
  try {
   var rows=jdbc.queryForList("select profile_id,amount,status,operation_type from digital_payments where payment_id=?",id);
   if(rows.isEmpty()) throw new ParkingException(HttpStatus.NOT_FOUND,"Pago inexistente");
   var row=rows.get(0);
   if(!profile.toString().equals(row.get("profile_id").toString()) || new BigDecimal(row.get("amount").toString()).compareTo(amount)!=0 ||
      !"APPROVED".equals(row.get("status").toString()) || !"RESERVATION".equals(row.get("operation_type").toString()))
    throw new ParkingException(HttpStatus.CONFLICT,"El pago debe estar aprobado y corresponder al conductor, importe y operacion RESERVATION");
  } catch(DataAccessException e) {
   throw new ParkingException(HttpStatus.SERVICE_UNAVAILABLE,"Payments & Billing aun no implementa digital_payments; no se puede confirmar un pago");
  }
 }
}
