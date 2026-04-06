alter table fdp_geo.geo_areas
    add column if not exists centroid_latitude numeric(9,6),
    add column if not exists centroid_longitude numeric(9,6);

create index if not exists idx_geo_areas_level_centroid
    on fdp_geo.geo_areas (geo_level, centroid_longitude, centroid_latitude);

create index if not exists idx_geo_areas_level_fips
    on fdp_geo.geo_areas (geo_level, fips_code);

create index if not exists idx_geo_areas_level_cbsa
    on fdp_geo.geo_areas (geo_level, cbsa_code);

create index if not exists idx_geo_areas_level_zip
    on fdp_geo.geo_areas (geo_level, zip_code);
