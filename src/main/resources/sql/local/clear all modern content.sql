
delete from a_bessources  where id in (select id from mfiles);
delete from a_media  where id in (select id from mfiles);
delete from a_sets  where id in (select id from folders);

delete from a_locations where id in (select id from locations);
delete from a_storages where id in (select id from storages);
