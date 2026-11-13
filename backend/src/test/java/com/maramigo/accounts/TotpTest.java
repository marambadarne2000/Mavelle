package com.maramigo.accounts;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/** Published RFC 6238 vectors, truncation, drift and replay. @author Maram Abbas */
class TotpTest {
 @Test void matchesRfc6238Sha1Vectors(){String secret=Totp.encode("12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII));long[] times={59,1111111109,1111111111,1234567890,2000000000L,20000000000L};String[] codes={"287082","081804","050471","005924","279037","353130"};for(int i=0;i<times.length;i++)assertThat(Totp.code(secret,times[i]/30)).isEqualTo(codes[i]);}
 @Test void rejectsReplayMalformedCodesAndOutOfWindowSteps(){String secret=Totp.secret();assertThat(Totp.decode(secret)).hasSize(20);String code=Totp.code(secret,100);assertThat(Totp.match(secret,code,3000,99)).isEqualTo(100);assertThat(Totp.match(secret,code,3000,100)).isEqualTo(-1);assertThat(Totp.match(secret,code,3090,0)).isEqualTo(-1);assertThat(Totp.match(secret,"0000000",3000,0)).isEqualTo(-1);}
 @Test void vaultAuthenticatesCiphertextAndDoesNotReuseNonces(){byte[] bytes=new byte[32];java.util.Arrays.fill(bytes,(byte)7);var vault=new SecretVault(java.util.Base64.getEncoder().encodeToString(bytes));String a=vault.encrypt("synthetic-value"),b=vault.encrypt("synthetic-value");assertThat(a).isNotEqualTo(b).doesNotContain("synthetic-value");assertThat(vault.decrypt(a)).isEqualTo("synthetic-value");byte[] tampered=java.util.Base64.getDecoder().decode(a);tampered[tampered.length-1]^=1;assertThatThrownBy(()->vault.decrypt(java.util.Base64.getEncoder().encodeToString(tampered))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
}
