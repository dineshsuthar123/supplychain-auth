CREATE TABLE public.legacy_accounts(id integer PRIMARY KEY, evidence text NOT NULL);
INSERT INTO public.legacy_accounts VALUES(7,'legacy data must remain byte-identical');
CREATE TABLE public.flyway_schema_history(installed_rank integer PRIMARY KEY, version varchar(50), checksum integer);
INSERT INTO public.flyway_schema_history VALUES(1,'1',123456789);
