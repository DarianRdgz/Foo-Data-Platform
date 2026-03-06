Story 5.1 seed inputs

Place these CSV files in this folder (docker/seed/) before running with FDP_GEO_SEEDING_ENABLED=true.
They will be mounted read-only into the backend container at /opt/fdp/seed.

Required files + columns:

1) metros.csv
   Columns: cbsa_code,name,state_fips2
   Example:
     cbsa_code,name,state_fips2
     26420,Houston-The Woodlands-Sugar Land,48

2) counties.csv
   Columns: county_fips5,name,state_fips2
   Example:
     county_fips5,name,state_fips2
     48201,Harris,48

3) zip_county.csv
   Columns: zip_code,county_fips5
   Example:
     zip_code,county_fips5
     77002,48201