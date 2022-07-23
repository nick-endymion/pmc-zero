####################### locations > locations
insert into a_locations(id, created_at, description, inuse, mfile_id, name, origin, location_typ, updated_at, uri, storage_id)
SELECT id, created_at, description, inuse, mfile_id, name, origin, typ, updated_at, uri, storage_id from locations;

update a_locations set filetype = 'jpg' where location_typ = 3;
