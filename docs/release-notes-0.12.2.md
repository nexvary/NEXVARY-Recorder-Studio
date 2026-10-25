# Guardian 0.12.2 — compact dashboard

The home setup banner, header spacing, feature icons and card minimum heights
are reduced so the normal-size 360x800dp dashboard can show all nine cards above
bottom navigation. Setup label now resolves the active text color instead of
inheriting a dark default. Touch targets remain at least48dp. Labels are not
ellipsized or fixed to a single line; enlarged text can scroll normally.
Rows share spare vertical space when everything fits, preserving a balanced
dashboard instead of leaving a large empty area below the final row.

Two instrumentation regressions verify Arabic/English normal-size fit and
large-text accessibility, with real screenshots and measured bounds. Accepted source b18b0aa, CI40143812943: build/lint/unit and API33/34/35
passed (82 instrumentation cases per API). Actual geometry and screenshots
confirm normal-size AR/EN fit and enlarged-text reachability. Physical OPPO
remains a separate check. Package, permissions, storage and release key are unchanged.
