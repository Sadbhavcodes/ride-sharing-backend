\c postgres

CREATE DATABASE rideshare_drivers;
CREATE DATABASE rideshare_trips;
CREATE DATABASE rideshare_matching;
CREATE DATABASE rideshare_locations;
CREATE DATABASE rideshare_payments;

-- Enable PostGIS on the locations database
\c rideshare_locations
CREATE EXTENSION IF NOT EXISTS postgis;
