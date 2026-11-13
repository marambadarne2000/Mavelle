package com.maramigo.accounts;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ProxyTrustConfigurationTest {
 @Test void emptyTrustAllProxyConfigurationIsRejected(){assertThatThrownBy(()->ProxyTrustConfiguration.validate("")).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->ProxyTrustConfiguration.validate("  ")).isInstanceOf(IllegalStateException.class);}
 @Test void noProxyAndAnExplicitPeerAreAccepted(){assertThatCode(()->ProxyTrustConfiguration.validate("(?!)")).doesNotThrowAnyException();assertThatCode(()->ProxyTrustConfiguration.validate("10\\.0\\.0\\.7")).doesNotThrowAnyException();}
}
