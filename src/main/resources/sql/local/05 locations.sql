####################### locations > locations
insert into a_locations(id, created_at, description, inuse, mfile_id, name, origin, typ, updated_at, uri, storage_id)
SELECT id, created_at, description, inuse, mfile_id, name, origin, typ, updated_at, uri, storage_id from locations;
