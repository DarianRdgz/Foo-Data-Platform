create index if not exists idx_area_snapshot_geo_cat_period_non_rollup
on fdp_geo.area_snapshot (geo_id, category, snapshot_period desc)
where is_rollup = false;