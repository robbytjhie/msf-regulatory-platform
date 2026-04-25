# Mock Supporting Documents (Testing Pack)

This pack contains two test scenarios for the MSF licensing demo:

- `scenario-pass` (clean, plausible evidence docs)
- `scenario-flagged` (intentionally suspicious/mismatched docs)

Use with the Operator Submit page:

1. Select a licensing track.
2. Upload files from one scenario folder.
3. Assign each file to its document category.

## Category Mapping (Recommended)

- `REGISTRATION_DOC`: `registration_doc_*`
- `FLOOR_PLAN`: `ecdc_floor_plan_*`
- `ATTENDANCE_LOG`: `scfa_attendance_log_*`
- `STAFF_ROSTER`: `hfaa_staff_roster_*`
- `HOME_SAFETY_PHOTOS`: `childminding_home_safety_*`

## Important for AI outcome checks

The current backend may use either:

- External AI metadata review (if keys configured), or
- Simulated fallback (25% random flagged).

So "flagged" scenario files are crafted to be suspicious by metadata, but fallback mode can still be probabilistic.
For consistent demo behavior, use external AI config or repeat submit/poll until a flagged sample appears.

