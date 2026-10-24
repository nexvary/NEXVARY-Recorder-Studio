# Guardian 0.12.2 — compact dashboard

The home setup banner, header spacing, feature icons and card minimum heights
are reduced so the normal-size 360x800dp dashboard can show all nine cards above
bottom navigation. Setup label now resolves the active text color instead of
inheriting a dark default. Touch targets remain at least48dp. Labels are not
ellipsized or fixed to a single line; enlarged text can scroll normally.

Two instrumentation regressions verify Arabic/English normal-size fit and
large-text accessibility, with real screenshots and measured bounds. Results
are pending until recorded in DEVELOPMENT_STATE.md. Physical OPPO remains a
separate check. Package, permissions, storage and release key are unchanged.
