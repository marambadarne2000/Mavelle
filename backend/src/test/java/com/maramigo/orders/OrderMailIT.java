package com.maramigo.orders;
import com.maramigo.accounts.AccountMail;
import com.maramigo.support.TestPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ActiveProfiles("demo") @SpringBootTest(properties={"DEMO_ADMIN_EMAIL=order-mail-owner@example.test","DEMO_ADMIN_PASSWORD=Synthetic-owner-password-2026"})
class OrderMailIT {
 static final TestPostgres postgres=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{postgres.configure(p);}
 @AfterAll static void close()throws Exception{postgres.close();}
 @Autowired JdbcTemplate db;@Autowired PlatformTransactionManager manager;
 AccountMail transport;OrderMail worker;
 @BeforeEach void setup(){db.update("UPDATE order_mail_jobs SET status='COMPLETED'");transport=mock(AccountMail.class);when(transport.mode()).thenReturn("smtp");worker=new OrderMail(db,transport,manager,true);}
 long job(){String unique=UUID.randomUUID().toString();long user=db.queryForObject("INSERT INTO users(email,password_hash,role) VALUES (?,'synthetic','CUSTOMER') RETURNING id",Long.class,unique+"@example.test");long id=db.queryForObject("INSERT INTO orders(order_number,user_id,total_price,status,shipping,idempotency_key,request_fingerprint) VALUES (?, ?,20,'PENDING','{}',?,'synthetic') RETURNING id",Long.class,"MG-"+unique,user,unique);db.update("INSERT INTO order_mail_jobs(order_id,recipient) VALUES (?,?)",id,unique+"@example.test");return id;}
 String state(long id){return db.queryForObject("SELECT status FROM order_mail_jobs WHERE order_id=?",String.class,id);}
 @Test void completedDeliveryIsNotSentAgain(){long id=job();worker.deliverPending();assertThat(state(id)).isEqualTo("COMPLETED");assertThat(db.queryForObject("SELECT delivery_mode FROM order_mail_jobs WHERE order_id=?",String.class,id)).isEqualTo("smtp");worker.deliverPending();verify(transport,times(1)).sendOrder(anyString(),anyString(),contains("20.00"));}
 @Test void failuresRetryThenStopWithoutAFalseSuccess(){long id=job();doThrow(new IllegalStateException("Synthetic transport failure")).when(transport).sendOrder(anyString(),anyString(),anyString());for(int i=0;i<3;i++){worker.deliverPending();db.update("UPDATE order_mail_jobs SET next_attempt=CURRENT_TIMESTAMP WHERE order_id=?",id);}assertThat(state(id)).isEqualTo("FAILED");assertThat(db.queryForObject("SELECT delivery_mode FROM order_mail_jobs WHERE order_id=?",String.class,id)).isNull();worker.deliverPending();verify(transport,times(3)).sendOrder(anyString(),anyString(),anyString());}
 @Test void disabledTransportDoesNotConsumeRetriesAndStaleClaimsRecover(){long id=job();when(transport.mode()).thenReturn("disabled");worker.deliverPending();assertThat(state(id)).isEqualTo("PENDING");assertThat(db.queryForObject("SELECT attempts FROM order_mail_jobs WHERE order_id=?",Integer.class,id)).isZero();when(transport.mode()).thenReturn("file");db.update("UPDATE order_mail_jobs SET status='RUNNING',attempts=1,claimed_at=CURRENT_TIMESTAMP-INTERVAL '2 minutes' WHERE order_id=?",id);worker.deliverPending();assertThat(state(id)).isEqualTo("COMPLETED");assertThat(db.queryForObject("SELECT delivery_mode FROM order_mail_jobs WHERE order_id=?",String.class,id)).isEqualTo("file");}
}
