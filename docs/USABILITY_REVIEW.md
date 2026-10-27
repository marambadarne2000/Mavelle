# Usability and keyboard review

Implemented: first-stop skip navigation; content focus after route changes; translated document titles; unknown-route recovery; safe company-link fallback while metadata loads; focused login errors; duplicate-login submission protection; larger mobile controls and 16px mobile input text; telephone input semantics; stable selectors for authentication, basket, checkout, product/stock forms, order status and dashboard filters.

The product dialog explicitly wraps Tab/Shift+Tab, excludes disabled controls, closes with Escape and restores focus to its opener. A real browser check exposed backward traversal leaving the dialog before the explicit wrapper was added. Both DOM tests and actual browser/Selenium checks verified the correction.

Reviewed demo product translations are searchable in Hebrew/Arabic. Custom content is not automatically translated. Ambiguous queries keep their original text instead of choosing an arbitrary product.

Seven added frontend regressions cover routes, localized search, error focus, dialog traversal, labeled login/autocomplete, one pending login request with recovery, and stale dashboard data after a failed refresh. React/jsdom tests do not measure real layout; zero-dimension chart warnings there are not a substitute for real browser layout checks.

Manual browser checks passed: first Tab and skip activation, unchanged route, content focus, Hebrew search, dialog traversal in both directions, Escape restoration, unknown-route recovery, one main landmark, no unresolved company links and no unlabeled controls in the sampled recovery view. No error-level browser logs were recorded in that sampled tab. This is a bounded observation, not a claim about every state.

Selenium passed six independent Chrome cases, including exact 1280×900 English and 390×844 Hebrew/Arabic viewports and horizontal-overflow checks. Evidence: `docs/screenshots/selenium-desktop.png`, `selenium-mobile.png`, `keyboard-skip.png`. Full purchase journeys in every viewport remain part of the next automation expansion.

Selected opaque palette contrast calculations: navigation 17.55, body 17.83, muted text 6.56, dark text at the darkest gold-button stop 4.83, disabled add label 4.61. These do not certify every gradient, widget, state or image. Full WCAG conformance, screen readers, device coverage and a complete network-failure matrix are not claimed.

This work changed no existing orders or stock. Selenium invalid-login checks use unique synthetic nonexistent emails; no administrator credentials are passed to the smoke suite.
