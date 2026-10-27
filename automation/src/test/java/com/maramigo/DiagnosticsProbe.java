package com.maramigo;
import com.maramigo.support.BrowserTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;
/** Opt-in deliberate failure; not matched by the normal *Test suite. @author Maram Abbas */
class DiagnosticsProbe extends BrowserTest {
 @Test void deliberateFailureProducesScreenshot(){open("#home");fail("Deliberate diagnostic probe: this failure is expected only when explicitly requested.");}
}
