# Browser accessibility and responsive matrix

The isolated full suite includes `AccessibilityE2ETest`: six independent Chrome
journeys, combining English/Hebrew/Arabic with exact 1280×900 and 390×900 CSS
viewports. Each journey now checks 23 rendered states (138 samples total):

| Area | Rendered states |
| --- | --- |
| Public | home, open product dialog, store catalog, customer login, customer registration, business registration, owner login alias, employee login alias, employee activation, empty basket, missing-page recovery |
| Customer | account, populated basket, shipping checkout, order confirmation, order history |
| Owner | owner workspace, account, marketplace management, populated dashboard, existing-product/stock editor, order detail/status editor, visible cancellation confirmation |

Each sample checks document language/direction, document horizontal overflow and
axe-core 4.14.0 rules tagged `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`,
`wcag22aa` and `best-practice`. Every reported violation fails the journey; no
specific rule or severity is disabled to make it pass. Execution failures fail
the test too. Checkout additionally verifies real Tab traversal through the six
shipping fields to submission. Existing smoke tests cover skip navigation,
dialog Tab/Shift+Tab wrapping, Escape/focus restoration and focused login errors.

The tests use generated customers, products, orders and synthetic shipping in a
disposable schema. They never use normal administrator credentials or mutate
normal orders. axe is loaded locally from the locked npm dependency; page data is
not sent to a scanning service. Implementation: [official axe API](https://github.com/dequelabs/axe-core/blob/develop/doc/API.md).

## Run and inspect

```powershell
# All public, purchase and accessibility journeys:
./scripts/Test-Automation.ps1 -Full
# Accessibility matrix only, with separate JUnit reports:
./scripts/Test-Automation.ps1 -Full -AccessibilityOnly
```

Install frontend dependencies first (`npm ci` on supported Node). Full mode
includes 24 Selenium cases: seven smoke, six purchase, six accessibility matrix
journeys, two owner/employee workflows and two marketplace workflows. The complete application count is 115:
56 backend + 35 frontend + 24 Selenium. The targeted mode is six cases, not a
fresh pass of the other 18.

Per-sample results: `automation/target/accessibility/<language>-<width>.json`.
They record the axe version, sampled view, violations and `incomplete` checks;
they do not include full HTML or browser storage. Reports/screenshots are
uploaded with Selenium CI artifacts for seven days. Targeted JUnit results use
`target/accessibility-reports`; full runs use `target/full-reports`.

## Problems found and corrected

The gate requires all 24 Selenium cases to pass on one revision. Its multilingual
matrix samples 138 states; four further English merchant overview/catalog samples
are recorded as `marketplace-*.json`. All violations fail the corresponding test.
Incomplete checks remain in reports. Check the hosted run for that commit and the
retained local reports rather than inferring success from a case count. The
owner/employee and merchant functional workflows use English at both widths;
merchant/employee screen-reader and full RTL audits are not claimed by them.

Marketplace checks found and corrected gold text on a cream background and a
header that overflowed at 390px after adding the business application link.

Selected synthetic evidence: [Hebrew mobile checkout](screenshots/accessibility-checkout-he-mobile.png)
and [Arabic mobile dashboard](screenshots/accessibility-dashboard-ar-mobile.png).

- Product actions used accessible names that omitted the visible button text.
  Image buttons now derive their name from visible category/action text plus
  visually hidden product context. Add buttons include their current visible
  state label and product name, including disabled states.
- The portfolio announcement was outside any landmark. It is now a named
  region. Checkout's order summary has a distinct accessible name from the demo
  disclaimer landmark.
- A long generated administrator email could overflow the English mobile account
  view. Account text now wraps inside the available width.
- Hebrew basket removal used a different word form in its accessible name than
  the visible label. The accessible name now retains the visible translated
  action and adds product context.

## Limits and remaining human review

This is a bounded automated review, not WCAG certification. axe `incomplete`
results are retained for human review, **not counted as passes**. In particular,
CSS gradients, clipped metallic lettering and some label contexts cannot always
be resolved automatically. Previous selected opaque-palette/gradient-stop
calculations are recorded in USABILITY_REVIEW.md; they do not certify every pixel
or every interaction state. Gold styling is preserved; these corrections concern
names, landmarks, text wrapping and keyboard behavior.

Screenshot inspection samples include mobile RTL checkout and dashboard, and a
desktop dashboard. The two viewports are Chrome emulations, not evidence of real
iOS/Safari devices. Screen-reader interaction, text/browser zoom, the complete
network-failure matrix, additional browsers/devices, new-product form state,
expanded chart tables and every possible order status still need human or
additional targeted review before making full accessibility claims.
